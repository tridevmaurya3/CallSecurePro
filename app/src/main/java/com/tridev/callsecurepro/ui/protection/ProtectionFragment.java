package com.tridev.callsecurepro.ui.protection;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.data.protection.ScreeningEventEntity;
import com.tridev.callsecurepro.databinding.FragmentProtectionBinding;
import com.tridev.callsecurepro.databinding.ItemScreeningHistoryBinding;
import com.tridev.callsecurepro.protection.CallerAssessment;
import com.tridev.callsecurepro.protection.CallerIntelligenceEngine;
import com.tridev.callsecurepro.protection.ProtectionPreferences;
import com.tridev.callsecurepro.protection.ProtectionRepository;
import com.tridev.callsecurepro.protection.ScreeningHistoryRepository;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ProtectionFragment extends Fragment {

    private static final int HISTORY_PREVIEW_LIMIT = 8;

    private FragmentProtectionBinding binding;
    private ExecutorService protectionExecutor;
    private ProtectionRepository repository;
    private ScreeningHistoryRepository historyRepository;
    private CallerIntelligenceEngine intelligenceEngine;
    private final AtomicInteger operationGeneration = new AtomicInteger();

    @Nullable
    private CallerAssessment currentAssessment;
    @Nullable
    private String currentDisplayNumber;

    private boolean syncingSettings;

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
        historyRepository = new ScreeningHistoryRepository(requireContext());
        intelligenceEngine = new CallerIntelligenceEngine(requireContext());
        protectionExecutor = Executors.newSingleThreadExecutor();

        setupSettings();
        setupActions();
        refreshDashboardData();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            syncSettingSwitches();
            refreshDashboardData();
        }
    }

    private void setupSettings() {
        syncSettingSwitches();

        binding.autoBlockSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!syncingSettings) {
                ProtectionPreferences.setAutoBlockHighRiskEnabled(requireContext(), checked);
            }
        });

        binding.silenceSuspiciousSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!syncingSettings) {
                ProtectionPreferences.setSilenceSuspiciousEnabled(requireContext(), checked);
            }
        });

        binding.blockHiddenSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!syncingSettings) {
                ProtectionPreferences.setBlockHiddenCallsEnabled(requireContext(), checked);
            }
        });

        binding.blockUnknownSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (syncingSettings) {
                return;
            }
            ProtectionPreferences.setBlockUnknownCallersEnabled(requireContext(), checked);
            if (checked) {
                ProtectionPreferences.setSilenceUnknownCallersEnabled(requireContext(), false);
                syncingSettings = true;
                binding.silenceUnknownSwitch.setChecked(false);
                syncingSettings = false;
            }
        });

        binding.silenceUnknownSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (syncingSettings) {
                return;
            }
            ProtectionPreferences.setSilenceUnknownCallersEnabled(requireContext(), checked);
            if (checked) {
                ProtectionPreferences.setBlockUnknownCallersEnabled(requireContext(), false);
                syncingSettings = true;
                binding.blockUnknownSwitch.setChecked(false);
                syncingSettings = false;
            }
        });
    }

    private void syncSettingSwitches() {
        if (binding == null) {
            return;
        }

        syncingSettings = true;
        binding.autoBlockSwitch.setChecked(
                ProtectionPreferences.isAutoBlockHighRiskEnabled(requireContext())
        );
        binding.silenceSuspiciousSwitch.setChecked(
                ProtectionPreferences.isSilenceSuspiciousEnabled(requireContext())
        );
        binding.blockHiddenSwitch.setChecked(
                ProtectionPreferences.isBlockHiddenCallsEnabled(requireContext())
        );
        binding.blockUnknownSwitch.setChecked(
                ProtectionPreferences.isBlockUnknownCallersEnabled(requireContext())
        );
        binding.silenceUnknownSwitch.setChecked(
                ProtectionPreferences.isSilenceUnknownCallersEnabled(requireContext())
        );
        syncingSettings = false;
    }

    private void setupActions() {
        binding.analyzeButton.setOnClickListener(view -> analyzeInputNumber());
        binding.blockButton.setOnClickListener(view -> toggleBlocked());
        binding.trustButton.setOnClickListener(view -> toggleTrusted());
        binding.reportButton.setOnClickListener(view -> reportSpam());
        binding.clearScreeningHistoryButton.setOnClickListener(view -> clearScreeningHistory());
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
        runAssessment(displayNumber);
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

    private void runAssessment(@NonNull String number) {
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
            });
        });
    }

    private void refreshDashboardData() {
        ExecutorService executor = protectionExecutor;
        ScreeningHistoryRepository currentHistoryRepository = historyRepository;
        if (executor == null || executor.isShutdown() || currentHistoryRepository == null) {
            return;
        }

        executor.execute(() -> {
            ProtectionRepository.Stats protectionStats = repository.getStats();
            ScreeningHistoryRepository.Stats screeningStats = currentHistoryRepository.getStats();
            List<ScreeningEventEntity> events = currentHistoryRepository.recent(HISTORY_PREVIEW_LIMIT);

            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (binding != null) {
                    renderStats(protectionStats);
                    renderScreeningHistory(screeningStats, events);
                }
            });
        });
    }

    private void clearScreeningHistory() {
        ExecutorService executor = protectionExecutor;
        ScreeningHistoryRepository currentHistoryRepository = historyRepository;
        if (executor == null || executor.isShutdown() || currentHistoryRepository == null) {
            return;
        }

        binding.clearScreeningHistoryButton.setEnabled(false);
        executor.execute(() -> {
            currentHistoryRepository.clear();
            ScreeningHistoryRepository.Stats stats = currentHistoryRepository.getStats();
            List<ScreeningEventEntity> events = currentHistoryRepository.recent(HISTORY_PREVIEW_LIMIT);

            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }
                binding.clearScreeningHistoryButton.setEnabled(true);
                renderScreeningHistory(stats, events);
                Toast.makeText(
                        requireContext(),
                        R.string.protection_history_cleared,
                        Toast.LENGTH_SHORT
                ).show();
            });
        });
    }

    private void renderStats(@NonNull ProtectionRepository.Stats stats) {
        binding.blockedCount.setText(String.valueOf(stats.blocked));
        binding.trustedCount.setText(String.valueOf(stats.trusted));
        binding.reportCount.setText(String.valueOf(stats.reports));
    }

    private void renderScreeningHistory(
            @NonNull ScreeningHistoryRepository.Stats stats,
            @NonNull List<ScreeningEventEntity> events
    ) {
        binding.screeningHistorySummary.setText(getString(
                R.string.protection_history_counts_format,
                stats.blocked,
                stats.silenced,
                stats.allowed
        ));
        binding.screeningHistoryContainer.removeAllViews();
        binding.screeningHistoryEmpty.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
        binding.clearScreeningHistoryButton.setEnabled(!events.isEmpty());

        for (ScreeningEventEntity event : events) {
            ItemScreeningHistoryBinding row = ItemScreeningHistoryBinding.inflate(
                    getLayoutInflater(),
                    binding.screeningHistoryContainer,
                    false
            );
            row.historyNumber.setText(event.displayNumber);
            row.historyReason.setText(event.reason);
            renderHistoryAction(row, event.action);

            String risk = event.riskLevel.substring(0, 1).toUpperCase(Locale.getDefault())
                    + event.riskLevel.substring(1).toLowerCase(Locale.getDefault());
            String riskText = getString(
                    R.string.protection_history_risk_format,
                    risk,
                    event.riskScore
            );
            String dateTime = DateFormat.getMediumDateFormat(requireContext())
                    .format(new Date(event.screenedAt))
                    + " • "
                    + DateFormat.getTimeFormat(requireContext()).format(new Date(event.screenedAt));
            row.historyMeta.setText(riskText + " • " + dateTime);
            binding.screeningHistoryContainer.addView(row.getRoot());
        }
    }

    private void renderHistoryAction(
            @NonNull ItemScreeningHistoryBinding row,
            @NonNull String action
    ) {
        int labelRes;
        int foreground;
        int background;

        if (ScreeningHistoryRepository.ACTION_BLOCKED.equals(action)) {
            labelRes = R.string.protection_history_action_blocked;
            foreground = ContextCompat.getColor(requireContext(), R.color.csp_spam);
            background = ContextCompat.getColor(requireContext(), R.color.csp_spam_container);
        } else if (ScreeningHistoryRepository.ACTION_SILENCED.equals(action)) {
            labelRes = R.string.protection_history_action_silenced;
            foreground = ContextCompat.getColor(requireContext(), R.color.csp_unknown);
            background = ContextCompat.getColor(requireContext(), R.color.csp_unknown_container);
        } else {
            labelRes = R.string.protection_history_action_allowed;
            foreground = ContextCompat.getColor(requireContext(), R.color.csp_primary);
            background = ContextCompat.getColor(requireContext(), R.color.csp_primary_container);
        }

        row.historyActionChip.setText(labelRes);
        row.historyActionChip.setTextColor(foreground);
        row.historyActionChip.setChipBackgroundColor(ColorStateList.valueOf(background));
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
        historyRepository = null;
        repository = null;
        binding = null;
        super.onDestroyView();
    }
}
