package com.tridev.callsecurepro.community;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Uses a deterministic SHA-256 identifier so raw phone numbers are not Firestore document IDs. */
public final class CommunityNumberHasher {

    private CommunityNumberHasher() {
    }

    @NonNull
    public static String sha256(@NonNull String normalizedNumber) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalizedNumber.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Android", exception);
        }
    }
}
