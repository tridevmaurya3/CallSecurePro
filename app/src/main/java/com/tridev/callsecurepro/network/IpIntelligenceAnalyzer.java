package com.tridev.callsecurepro.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Local-only IP address validation and scope classification.
 *
 * This class does not perform DNS lookups for hostnames and does not provide a person's
 * location. Public-IP geolocation, when explicitly requested by the user, is handled by a
 * separate service and is presented only as approximate network-region information.
 */
public final class IpIntelligenceAnalyzer {

    public enum Version {
        IPV4,
        IPV6,
        UNKNOWN
    }

    public enum Scope {
        PUBLIC,
        PRIVATE,
        LOOPBACK,
        LINK_LOCAL,
        MULTICAST,
        RESERVED,
        UNSPECIFIED,
        INVALID
    }

    public static final class Result {
        private final boolean valid;
        @NonNull
        private final Version version;
        @NonNull
        private final Scope scope;
        @NonNull
        private final String canonicalAddress;
        @NonNull
        private final String explanation;

        private Result(
                boolean valid,
                @NonNull Version version,
                @NonNull Scope scope,
                @NonNull String canonicalAddress,
                @NonNull String explanation
        ) {
            this.valid = valid;
            this.version = version;
            this.scope = scope;
            this.canonicalAddress = canonicalAddress;
            this.explanation = explanation;
        }

        public boolean isValid() {
            return valid;
        }

        @NonNull
        public Version getVersion() {
            return version;
        }

        @NonNull
        public Scope getScope() {
            return scope;
        }

        @NonNull
        public String getCanonicalAddress() {
            return canonicalAddress;
        }

        @NonNull
        public String getExplanation() {
            return explanation;
        }

        public boolean canUseOnlineApproximateLookup() {
            return valid && scope == Scope.PUBLIC;
        }
    }

    @NonNull
    public Result analyze(@Nullable String rawInput) {
        String input = normalizeInput(rawInput);
        if (input.isEmpty()) {
            return invalid("Enter an IPv4 or IPv6 address.");
        }

        if (isStrictIpv4(input)) {
            return analyzeIpv4(input);
        }

        if (input.indexOf(':') >= 0) {
            return analyzeIpv6(input);
        }

        return invalid("This is not a valid IPv4 or IPv6 address.");
    }

    public boolean looksLikeIpCandidate(@Nullable String value) {
        String input = normalizeInput(value);
        return input.indexOf(':') >= 0 || input.indexOf('.') >= 0;
    }

    @NonNull
    private Result analyzeIpv4(@NonNull String input) {
        int[] octets = parseIpv4(input);
        if (octets == null) {
            return invalid("This is not a valid IPv4 address.");
        }

        String canonical = String.format(
                Locale.US,
                "%d.%d.%d.%d",
                octets[0], octets[1], octets[2], octets[3]
        );

        if (octets[0] == 0) {
            return valid(Version.IPV4, Scope.UNSPECIFIED, canonical,
                    "This IPv4 range is reserved for the local host/network and is not a public Internet address.");
        }
        if (octets[0] == 127) {
            return valid(Version.IPV4, Scope.LOOPBACK, canonical,
                    "Loopback address: it refers back to the same device.");
        }
        if (octets[0] == 169 && octets[1] == 254) {
            return valid(Version.IPV4, Scope.LINK_LOCAL, canonical,
                    "Link-local address: it is usable only on the local network segment.");
        }
        if (octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168)) {
            return valid(Version.IPV4, Scope.PRIVATE, canonical,
                    "Private IPv4 address: it is intended for local networks and cannot identify a public Internet location.");
        }
        if (octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127) {
            return valid(Version.IPV4, Scope.RESERVED, canonical,
                    "Carrier-grade NAT range: it is shared inside provider networks and is not a normal public endpoint address.");
        }
        if (octets[0] >= 224 && octets[0] <= 239) {
            return valid(Version.IPV4, Scope.MULTICAST, canonical,
                    "Multicast address: it represents a group of receivers, not one public device.");
        }
        if (isIpv4DocumentationOrBenchmark(octets) || octets[0] >= 240) {
            return valid(Version.IPV4, Scope.RESERVED, canonical,
                    "Reserved or documentation IPv4 range: public geolocation is not meaningful for this address.");
        }

        return valid(Version.IPV4, Scope.PUBLIC, canonical,
                "Public IPv4 address. An optional online lookup can provide only approximate network-region and provider information.");
    }

    @NonNull
    private Result analyzeIpv6(@NonNull String input) {
        try {
            InetAddress address = InetAddress.getByName(input);
            if (!(address instanceof Inet6Address)) {
                return invalid("This is not a valid IPv6 address.");
            }

            String canonical = stripScopeId(address.getHostAddress());
            byte[] bytes = address.getAddress();

            if (address.isAnyLocalAddress()) {
                return valid(Version.IPV6, Scope.UNSPECIFIED, canonical,
                        "Unspecified IPv6 address: it does not identify a public endpoint.");
            }
            if (address.isLoopbackAddress()) {
                return valid(Version.IPV6, Scope.LOOPBACK, canonical,
                        "Loopback IPv6 address: it refers back to the same device.");
            }
            if (address.isLinkLocalAddress()) {
                return valid(Version.IPV6, Scope.LINK_LOCAL, canonical,
                        "Link-local IPv6 address: it is limited to the local network link.");
            }
            if (address.isMulticastAddress()) {
                return valid(Version.IPV6, Scope.MULTICAST, canonical,
                        "Multicast IPv6 address: it represents a receiver group, not one public device.");
            }
            if (isUniqueLocalIpv6(bytes)) {
                return valid(Version.IPV6, Scope.PRIVATE, canonical,
                        "Unique-local IPv6 address: it is intended for private networks.");
            }
            if (isDocumentationIpv6(bytes)) {
                return valid(Version.IPV6, Scope.RESERVED, canonical,
                        "Documentation IPv6 range: it is reserved for examples and testing.");
            }

            return valid(Version.IPV6, Scope.PUBLIC, canonical,
                    "Public IPv6 address. An optional online lookup can provide only approximate network-region and provider information.");
        } catch (UnknownHostException | IllegalArgumentException ignored) {
            return invalid("This is not a valid IPv6 address.");
        }
    }

    private boolean isIpv4DocumentationOrBenchmark(@NonNull int[] o) {
        return (o[0] == 192 && o[1] == 0 && o[2] == 2)
                || (o[0] == 198 && o[1] == 51 && o[2] == 100)
                || (o[0] == 203 && o[1] == 0 && o[2] == 113)
                || (o[0] == 198 && (o[1] == 18 || o[1] == 19));
    }

    private boolean isUniqueLocalIpv6(@NonNull byte[] bytes) {
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private boolean isDocumentationIpv6(@NonNull byte[] bytes) {
        return bytes.length == 16
                && (bytes[0] & 0xFF) == 0x20
                && (bytes[1] & 0xFF) == 0x01
                && (bytes[2] & 0xFF) == 0x0D
                && (bytes[3] & 0xFF) == 0xB8;
    }

    private boolean isStrictIpv4(@NonNull String input) {
        return parseIpv4(input) != null;
    }

    @Nullable
    private int[] parseIpv4(@NonNull String input) {
        String[] parts = input.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }

        int[] values = new int[4];
        for (int index = 0; index < 4; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            for (int charIndex = 0; charIndex < part.length(); charIndex++) {
                if (!Character.isDigit(part.charAt(charIndex))) {
                    return null;
                }
            }
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return null;
                }
                values[index] = value;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return values;
    }

    @NonNull
    private String normalizeInput(@Nullable String rawInput) {
        String value = rawInput == null ? "" : rawInput.trim();
        if (value.length() >= 2 && value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    @NonNull
    private String stripScopeId(@Nullable String value) {
        if (value == null) {
            return "";
        }
        int percent = value.indexOf('%');
        return percent >= 0 ? value.substring(0, percent) : value;
    }

    @NonNull
    private Result valid(
            @NonNull Version version,
            @NonNull Scope scope,
            @NonNull String canonical,
            @NonNull String explanation
    ) {
        return new Result(true, version, scope, canonical, explanation);
    }

    @NonNull
    private Result invalid(@NonNull String explanation) {
        return new Result(false, Version.UNKNOWN, Scope.INVALID, "", explanation);
    }
}
