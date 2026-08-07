package com.tridev.callsecurepro.ui.protection;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.FragmentProtectionBinding;
import com.tridev.callsecurepro.protection.CallerAssessment;
import com.tridev.callsecurepro.protection.CallerIntelligenceEngine;
import com.tridev.callsecurepro.protection.ProtectionPreferences;
import com.tridev.callsecurepro.protection.ProtectionRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ProtectionFragment extends Fragment {

    private FragmentProtectionBinding binding;
    private ExecutorService protectionExecutor;
    private ProtectionRepository repository;
    private CallerIntelligenceEngine intelligenceEngine;
    private final AtomicInteger operationGeneration = new AtomicInteger();

    @Nullable
    private CallerAssessment currentAssessment;
    @Nullable
    private String currentDisplayNumber;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentProtectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new ProtectionRepository(requireContext());
        intelligenceEngine = new CallerIntelligenceEngine(requireContext());
        protectionExecutor = Executors.newSingleThreadExecutor();

        setupSettings();
        setupActions();
        refreshStats();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            syncSettingSwitches();
            refreshStats();
        }
    }

    private void setupSettings() {
        syncSettingSwitches();

        binding.autoBlockSwitch.setOnCheckedChangeListener((buttonView, checked) ->
                ProtectionPreferences.setAutoBlockHighRiskEnabled(requireContext(), checked)
        );

        binding.silenceSuspiciousSwitch.setOnCheckedChangeListener((buttonView, checked) ->
                ProtectionPreferences.setSilenceSuspiciousEnabled(requireContext(), checked)
        );
    }

    private void syncSettingSwitches() {
        if (binding == null) {
            return;
        }

        binding.autoBlockSwitch.setChecked(
                ProtectionPreferences.isAutoBlockHighRiskEnabled(requireContext())
        );
        binding.silenceSuspiciousSwitch.setChecked(
                ProtectionPreferences.isSilenceSuspiciousEnabled(requireContext())
        );
    }

    private void setupActions() {
        binding.analyzeButton.setOnClickListener(view -> analyzeInputNumber());
        binding.blockButton.setOnClickListener(view -> toggleBlocked());
        binding.trustButton.setOnClickListener(view -> toggleTrusted());
        binding.reportButton.setOnClickListener(view -> reportSpam());
    }

    private void analyzeInputNumber() {
        if (binding.numberInput.getText() == null) {
            showNumberRequired();
            return;
        }

        String displayNumber = binding.numberInput.getText().toString().trim();
        if (ProtectionRepository.normalize(displayNumber).isEmpty()) {
            showNumberRequired();
            return;
        }

        binding.numberInputLayout.setError(null);
        currentDisplayNumber = displayNumber;
        runAssessment(displayNumber, false, 0);
    }

    private void showNumberRequired() {
        binding.numberInputLayout.setError(getString(R.string.protection_number_required));
        binding.numberInput.requestFocus();
    }

    private void toggleBlocked() {
        CallerAssessment assessment = currentAssessment;
        String number = currentDisplayNumber;
        if (assessment == null || number == null) {
            return;
        }

        boolean newBlockedState = !assessment.isUserBlocked();
        runRuleMutation(() -> repository.setBlocked(number, newBlockedState), false);
    }

    private void toggleTrusted() {
        CallerAssessment assessment = currentAssessment;
        String number = currentDisplayNumber;
        if (assessment == null || number == null) {
            return;
        }

        boolean newTrustedState = !assessment.isTrusted();
        runRuleMutation(() -> repository.setTrusted(number, newTrustedState), false);
    }

    private void reportSpam() {
        String number = currentDisplayNumber;
        if (number == null) {
            return;
        }
        runRuleMutation(() -> repository.addSpamReport(number), true);
    }

    private void runRuleMutation(@NonNull Runnable mutation, boolean reportAdded) {
        ExecutorService executor = protectionExecutor;
        String number = currentDisplayNumber;
        if (executor == null || executor.isShutdown() || number == null) {
            return;
        }

        int generation = operationGeneration.incrementAndGet();
        setWorking(true);

        executor.execute(() -> {
            try {
                mutation.run();
                CallerAssessment assessment = intelligenceEngine.assess(number);
                ProtectionRepository.Stats stats = repository.getStats();

                if (!isAdded() || generation != operationGeneration.get()) {
                    return;
                }

                requireActivity().runOnUiThread(() -> {
                    if (binding == null || generation != operationGeneration.get()) {
                        return;
                    }
                    setWorking(false);
                    renderAssessment(number, assessment);
                    renderStats(stats);
                    Toast.makeText(
                            requireContext(),
                            reportAdded
                                    ? R.string.protection_report_added
                                    : R.string.protection_rule_updated,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            } catch (RuntimeException exception) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        setWorking(false);
                    }
                });
            }
        });
    }

    private void runAssessment(
            @NonNull String number,
            boolean showToast,
            int toastRes
    ) {
        ExecutorService executor = protectionExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }

        int generation = operationGeneration.incrementAndGet();
        setWorking(true);

        executor.execute(() -> {
            CallerAssessment assessment = intelligenceEngine.assess(number);
            ProtectionRepository.Stats stats = repository.getStats();

            if (!isAdded() || generation != operationGeneration.get()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                if (binding == null || generation != operationGeneration.get()) {
                    return;
                }
                setWorking(false);
                renderAssessment(number, assessment);
                renderStats(stats);
                if (showToast && toastRes != 0) {
                    Toast.makeText(requireContext(), toastRes, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void refreshStats() {
        ExecutorService executor = protectionExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }

        executor.execute(() -> {
            ProtectionRepository.Stats stats = repository.getStats();
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (binding != null) {
                    renderStats(stats);
                }
            });
        });
    }

    private void renderStats(@NonNull ProtectionRepository.Stats stats) {
        binding.blockedCount.setText(String.valueOf(stats.blocked));
        binding.trustedCount.setText(String.valueOf(stats.trusted));
        binding.reportCount.setText(String.valueOf(stats.reports));
    }

    private void renderAssessment(
            @NonNull String displayNumber,
            @NonNull CallerAssessment assessment
    ) {
        currentDisplayNumber = displayNumber;
        currentAssessment = assessment;

        binding.resultCard.setVisibility(View.VISIBLE);
        binding.resultNumber.setText(displayNumber);
        binding.resultReason.setText(assessment.getReason());
        binding.resultReports.setText(
                getString(R.string.protection_reports_format, assessment.getLocalReports())
        );
        binding.riskScore.setText(
                getString(R.string.protection_risk_score_format, assessment.getRiskScore())
        );

        int labelRes;
        int foreground;
        int background;

        switch (assessment.getLevel()) {
            case SAFE:
                labelRes = R.string.protection_result_safe;
                foreground = ContextCompat.getColor(requireContext(), R.color.csp_safe);
                background = ContextCompat.getColor(requireContext(), R.color.csp_safe_container);
                break;
            case SUSPICIOUS:
                labelRes = R.string.protection_result_suspicious;
                foreground = ContextCompat.getColor(requireContext(), R.color.csp_unknown);
                background = ContextCompat.getColor(requireContext(), R.color.csp_unknown_container);
                break;
            case SPAM:
                labelRes = R.string.protection_result_spam;
                foreground = ContextCompat.getColor(requireContext(), R.color.csp_spam);
                background = ContextCompat.getColor(requireContext(), R.color.csp_spam_container);
                break;
            case UNKNOWN:
            default:
                labelRes = R.string.protection_result_unknown;
                foreground = ContextCompat.getColor(requireContext(), R.color.csp_business);
                background = ContextCompat.getColor(requireContext(), R.color.csp_business_container);
                break;
        }

        binding.riskChip.setText(labelRes);
        binding.riskChip.setTextColor(foreground);
        binding.riskChip.setChipBackgroundColor(ColorStateList.valueOf(background));

        binding.blockButton.setText(
                assessment.isUserBlocked()
                        ? R.string.protection_unblock
                        : R.string.protection_block
        );
        binding.trustButton.setText(
                assessment.isTrusted()
                        ? R.string.protection_untrust
                        : R.string.protection_trust
        );
    }

    private void setWorking(boolean working) {
        if (binding == null) {
            return;
        }
        binding.analyzeButton.setEnabled(!working);
        binding.blockButton.setEnabled(!working);
        binding.trustButton.setEnabled(!working);
        binding.reportButton.setEnabled(!working);
    }

    @Override
    public void onDestroyView() {
        operationGeneration.incrementAndGet();
        if (protectionExecutor != null) {
            protectionExecutor.shutdownNow();
            protectionExecutor = null;
        }
        currentAssessment = null;
        currentDisplayNumber = null;
        intelligenceEngine = null;
        repository = null;
        binding = null;
        super.onDestroyView();
    }
}
