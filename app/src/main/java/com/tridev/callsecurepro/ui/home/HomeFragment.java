package com.tridev.callsecurepro.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.tridev.callsecurepro.MainActivity;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.FragmentHomeBinding;
import com.tridev.callsecurepro.network.IpIntelligenceAnalyzer;
import com.tridev.callsecurepro.setup.CallerProtectionSetupActivity;
import com.tridev.callsecurepro.ui.lookup.NumberLookupActivity;
import com.tridev.callsecurepro.ui.network.IpIntelligenceActivity;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private IpIntelligenceAnalyzer ipIntelligenceAnalyzer;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ipIntelligenceAnalyzer = new IpIntelligenceAnalyzer();
        setupQuickActions();
        setupNumberLookup();
        setupDashboardActions();
    }

    private void setupQuickActions() {
        binding.floatingDialButton.setOnClickListener(view -> openSection(R.id.nav_dial));
        binding.actionCallsCard.setOnClickListener(view -> openSection(R.id.nav_calls));
        binding.actionContactsCard.setOnClickListener(view -> openSection(R.id.nav_contacts));
        binding.actionProtectionCard.setOnClickListener(view -> openSection(R.id.nav_protection));
    }

    private void setupNumberLookup() {
        binding.numberLookupInputLayout.setHint(R.string.ip_home_lookup_hint);
        binding.numberLookupInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        binding.numberLookupInput.setSingleLine(true);
        binding.numberLookupInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        binding.numberLookupButton.setOnClickListener(view -> performLookup());

        binding.numberLookupInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performLookup();
                return true;
            }
            return false;
        });
    }

    private void setupDashboardActions() {
        binding.openCallsButton.setOnClickListener(view -> openSection(R.id.nav_calls));

        binding.startSetupButton.setOnClickListener(view -> {
            Intent intent = new Intent(requireContext(), CallerProtectionSetupActivity.class);
            startActivity(intent);
        });
    }

    private void performLookup() {
        CharSequence inputText = binding.numberLookupInput.getText();
        String input = inputText == null ? "" : inputText.toString().trim();

        if (input.isEmpty()) {
            binding.numberLookupInputLayout.setError(
                    getString(R.string.ip_home_lookup_empty)
            );
            binding.numberLookupInput.requestFocus();
            return;
        }

        binding.numberLookupInputLayout.setError(null);

        IpIntelligenceAnalyzer analyzer = ipIntelligenceAnalyzer;
        if (analyzer != null && analyzer.looksLikeIpCandidate(input)) {
            Intent intent = new Intent(requireContext(), IpIntelligenceActivity.class);
            intent.putExtra(IpIntelligenceActivity.EXTRA_IP, input);
            startActivity(intent);
            return;
        }

        Intent intent = new Intent(requireContext(), NumberLookupActivity.class);
        intent.putExtra(NumberLookupActivity.EXTRA_NUMBER, input);
        startActivity(intent);
    }

    private void openSection(int itemId) {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).selectMainSection(itemId);
        }
    }

    @Override
    public void onDestroyView() {
        ipIntelligenceAnalyzer = null;
        super.onDestroyView();
        binding = null;
    }
}
