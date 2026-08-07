package com.tridev.callsecurepro.ui.network;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ActivityIpIntelligenceBinding;
import com.tridev.callsecurepro.network.IpIntelligenceAnalyzer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class IpIntelligenceActivity extends AppCompatActivity {

    public static final String EXTRA_IP = "com.tridev.callsecurepro.extra.IP_ADDRESS";

    private ActivityIpIntelligenceBinding binding;
    private IpIntelligenceAnalyzer analyzer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        binding = ActivityIpIntelligenceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        analyzer = new IpIntelligenceAnalyzer();
        applySystemInsets();
        setupActions();
        renderDeviceNetwork();
        consumeInitialIp(getIntent());
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.ipRoot, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    Math.max(view.getPaddingLeft(), bars.left),
                    Math.max(view.getPaddingTop(), bars.top),
                    Math.max(view.getPaddingRight(), bars.right),
                    Math.max(view.getPaddingBottom(), bars.bottom)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.ipRoot);
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.analyzeButton.setOnClickListener(view -> analyzeCurrentInput());
        binding.ipInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                analyzeCurrentInput();
                return true;
            }
            return false;
        });
    }

    private void consumeInitialIp(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String ip = intent.getStringExtra(EXTRA_IP);
        if (ip == null || ip.trim().isEmpty()) {
            return;
        }
        binding.ipInput.setText(ip.trim());
        binding.ipInput.setSelection(binding.ipInput.length());
        renderAnalysis(analyzer.analyze(ip));
    }

    private void analyzeCurrentInput() {
        CharSequence text = binding.ipInput.getText();
        String value = text == null ? "" : text.toString().trim();
        if (value.isEmpty()) {
            binding.ipInputLayout.setError(getString(R.string.ip_lookup_required));
            binding.ipInput.requestFocus();
            return;
        }
        binding.ipInputLayout.setError(null);
        renderAnalysis(analyzer.analyze(value));
    }

    private void renderAnalysis(@NonNull IpIntelligenceAnalyzer.Result result) {
        binding.resultCard.setVisibility(View.VISIBLE);

        if (result.isValid()) {
            binding.validityChip.setText(R.string.ip_lookup_valid);
            binding.validityChip.setTextColor(
                    ContextCompat.getColor(this, R.color.csp_safe)
            );
            binding.validityChip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.csp_safe_container)
            ));
            binding.canonicalText.setVisibility(View.VISIBLE);
            binding.canonicalText.setText(
                    getString(R.string.ip_lookup_canonical_format, result.getCanonicalAddress())
            );
        } else {
            binding.validityChip.setText(R.string.ip_lookup_invalid_chip);
            binding.validityChip.setTextColor(
                    ContextCompat.getColor(this, R.color.csp_spam)
            );
            binding.validityChip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.csp_spam_container)
            ));
            binding.canonicalText.setVisibility(View.GONE);
        }

        binding.versionText.setText(
                getString(R.string.ip_lookup_version_format, getVersionLabel(result.getVersion()))
        );
        binding.scopeText.setText(
                getString(R.string.ip_lookup_scope_format, getScopeLabel(result.getScope()))
        );
        binding.explanationText.setText(result.getExplanation());
    }

    @NonNull
    private String getVersionLabel(@NonNull IpIntelligenceAnalyzer.Version version) {
        switch (version) {
            case IPV4:
                return getString(R.string.ip_version_ipv4);
            case IPV6:
                return getString(R.string.ip_version_ipv6);
            case UNKNOWN:
            default:
                return getString(R.string.ip_version_unknown);
        }
    }

    @NonNull
    private String getScopeLabel(@NonNull IpIntelligenceAnalyzer.Scope scope) {
        switch (scope) {
            case PUBLIC:
                return getString(R.string.ip_scope_public);
            case PRIVATE:
                return getString(R.string.ip_scope_private);
            case LOOPBACK:
                return getString(R.string.ip_scope_loopback);
            case LINK_LOCAL:
                return getString(R.string.ip_scope_link_local);
            case MULTICAST:
                return getString(R.string.ip_scope_multicast);
            case RESERVED:
                return getString(R.string.ip_scope_reserved);
            case UNSPECIFIED:
                return getString(R.string.ip_scope_unspecified);
            case INVALID:
            default:
                return getString(R.string.ip_scope_invalid);
        }
    }

    private void renderDeviceNetwork() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE
        );
        if (connectivityManager == null) {
            showNoNetworkDetails();
            return;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            showNoNetworkDetails();
            return;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);

        binding.networkTransportText.setText(
                getString(R.string.ip_device_network_transport_format, getTransportLabel(capabilities))
        );

        List<String> addresses = new ArrayList<>();
        if (linkProperties != null) {
            for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                InetAddress address = linkAddress.getAddress();
                if (address == null || address.isLoopbackAddress()) {
                    continue;
                }
                String hostAddress = address.getHostAddress();
                if (hostAddress == null || hostAddress.trim().isEmpty()) {
                    continue;
                }
                int scopeSeparator = hostAddress.indexOf('%');
                if (scopeSeparator >= 0) {
                    hostAddress = hostAddress.substring(0, scopeSeparator);
                }
                if (!addresses.contains(hostAddress)) {
                    addresses.add(hostAddress);
                }
                if (addresses.size() >= 8) {
                    break;
                }
            }
        }

        if (addresses.isEmpty()) {
            binding.localAddressesText.setText(R.string.ip_device_network_none);
        } else {
            binding.localAddressesText.setText(android.text.TextUtils.join("\n", addresses));
        }
    }

    private void showNoNetworkDetails() {
        binding.networkTransportText.setText(
                getString(
                        R.string.ip_device_network_transport_format,
                        getString(R.string.ip_transport_other)
                )
        );
        binding.localAddressesText.setText(R.string.ip_device_network_none);
    }

    @NonNull
    private String getTransportLabel(@Nullable NetworkCapabilities capabilities) {
        if (capabilities == null) {
            return getString(R.string.ip_transport_other);
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return getString(R.string.ip_transport_vpn);
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return getString(R.string.ip_transport_wifi);
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return getString(R.string.ip_transport_cellular);
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return getString(R.string.ip_transport_ethernet);
        }
        return getString(R.string.ip_transport_other);
    }

    @Override
    protected void onDestroy() {
        analyzer = null;
        binding = null;
        super.onDestroy();
    }
}
