package com.tridev.callsecurepro.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.tridev.callsecurepro.MainActivity;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.FragmentHomeBinding;
import com.tridev.callsecurepro.setup.CallerProtectionSetupActivity;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

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

        setupQuickActions();
        setupNumberLookup();
        setupDashboardActions();
    }

    private void setupQuickActions() {
        binding.actionDialCard.setOnClickListener(view -> openSection(R.id.nav_dial));
        binding.actionCallsCard.setOnClickListener(view -> openSection(R.id.nav_calls));
        binding.actionContactsCard.setOnClickListener(view -> openSection(R.id.nav_contacts));
        binding.actionProtectionCard.setOnClickListener(view -> openSection(R.id.nav_protection));
    }

    private void setupNumberLookup() {
        binding.numberLookupButton.setOnClickListener(view -> performNumberLookup());

        binding.numberLookupInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performNumberLookup();
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

    private void performNumberLookup() {
        CharSequence inputText = binding.numberLookupInput.getText();
        String number = inputText == null ? "" : inputText.toString().trim();

        if (number.isEmpty()) {
            binding.numberLookupInputLayout.setError(
                    getString(R.string.home_lookup_empty_error)
            );
            binding.numberLookupInput.requestFocus();
            return;
        }

        binding.numberLookupInputLayout.setError(null);

        Toast.makeText(
                requireContext(),
                R.string.home_lookup_placeholder_message,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void openSection(int itemId) {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).selectMainSection(itemId);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
