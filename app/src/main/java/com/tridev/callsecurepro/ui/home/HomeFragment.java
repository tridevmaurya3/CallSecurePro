package com.tridev.callsecurepro.ui.home;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.provider.CallLog;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.tridev.callsecurepro.MainActivity;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.FragmentHomeBinding;
import com.tridev.callsecurepro.network.IpIntelligenceAnalyzer;
import com.tridev.callsecurepro.setup.CallerProtectionSetupActivity;
import com.tridev.callsecurepro.ui.lookup.NumberLookupActivity;
import com.tridev.callsecurepro.ui.network.IpIntelligenceActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private IpIntelligenceAnalyzer ipIntelligenceAnalyzer;
    private HomeDashboardRepository dashboardRepository;
    private ExecutorService dashboardExecutor;
    private final AtomicInteger dashboardGeneration = new AtomicInteger();
    private boolean dashboardLoading;

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
        dashboardRepository = new HomeDashboardRepository(requireContext());
        dashboardExecutor = Executors.newSingleThreadExecutor();

        setupQuickActions();
        setupNumberLookup();
        setupDashboardActions();
        refreshLiveDashboard();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && dashboardRepository != null) {
            refreshLiveDashboard();
        }
    }

    private void setupQuickActions() {
        binding.floatingDialButton.setOnClickListener(view -> openSection(R.id.nav_dial));
        binding.actionCallsCard.setOnClickListener(view -> openSection(R.id.nav_calls));
        binding.actionContactsCard.setOnClickListener(view -> openSection(R.id.nav_contacts));
        binding.actionProtectionCard.setOnClickListener(view -> openSection(R.id.nav_protection));
    }

    private void setupNumberLookup() {
        binding.numberLookupInputLayout.setHint(R.string.home_lookup_phone_ip_hint);
        binding.numberLookupInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        binding.numberLookupInput.setSingleLine(true);
        binding.numberLookupInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        // Material outlined fields need enough vertical room for the floating label and entered
        // text. The previous fixed 48dp edit-text height could clip digits on some font scales.
        ViewGroup.LayoutParams inputLayoutParams = binding.numberLookupInput.getLayoutParams();
        if (inputLayoutParams != null) {
            inputLayoutParams.height = dpToPx(56);
            binding.numberLookupInput.setLayoutParams(inputLayoutParams);
        }
        binding.numberLookupInput.setMinHeight(dpToPx(56));
        binding.numberLookupInput.setGravity(Gravity.CENTER_VERTICAL);
        binding.numberLookupInput.setIncludeFontPadding(false);

        binding.numberLookupButton.setOnClickListener(view -> performLookup());

        binding.numberLookupInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performLookup();
                return true;
            }
            return false;
        });
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void setupDashboardActions() {
        binding.openCallsButton.setOnClickListener(view -> openSection(R.id.nav_calls));

        binding.startSetupButton.setOnClickListener(view -> {
            Intent intent = new Intent(requireContext(), CallerProtectionSetupActivity.class);
            startActivity(intent);
        });
    }

    private void refreshLiveDashboard() {
        FragmentHomeBinding currentBinding = binding;
        ExecutorService executor = dashboardExecutor;
        HomeDashboardRepository repository = dashboardRepository;
        if (currentBinding == null
                || executor == null
                || executor.isShutdown()
                || repository == null
                || dashboardLoading) {
            return;
        }

        dashboardLoading = true;
        int generation = dashboardGeneration.incrementAndGet();

        executor.execute(() -> {
            try {
                HomeDashboardRepository.Snapshot snapshot = repository.loadSnapshot();
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (binding == null || generation != dashboardGeneration.get()) {
                        return;
                    }
                    dashboardLoading = false;
                    renderSnapshot(snapshot);
                });
            } catch (RuntimeException ignored) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (binding == null || generation != dashboardGeneration.get()) {
                        return;
                    }
                    dashboardLoading = false;
                    renderDashboardFallback();
                });
            }
        });
    }

    private void renderSnapshot(@NonNull HomeDashboardRepository.Snapshot snapshot) {
        boolean setupComplete = snapshot.setupReadyCount >= snapshot.setupTotalCount;
        styleStatusChip(
                binding.dashboardStatusChip,
                setupComplete,
                setupComplete
                        ? R.string.home_dashboard_live_ready
                        : R.string.home_dashboard_live_setup
        );

        if (snapshot.callLogAvailable) {
            binding.actionCallsSummary.setText(
                    snapshot.missedToday > 0
                            ? getString(R.string.home_calls_missed_today, snapshot.missedToday)
                            : getString(R.string.home_calls_none_missed)
            );
        } else {
            binding.actionCallsSummary.setText(R.string.home_calls_access_needed);
        }

        binding.actionContactsSummary.setText(
                snapshot.contactsAvailable
                        ? getString(R.string.home_contacts_count_short, snapshot.contactCount)
                        : getString(R.string.home_contacts_access_needed)
        );
        binding.actionProtectionSummary.setText(
                getString(R.string.home_protection_blocked_short, snapshot.screenedBlocked)
        );

        binding.screenedBlockedCount.setText(String.valueOf(snapshot.screenedBlocked));
        binding.screenedSilencedCount.setText(String.valueOf(snapshot.screenedSilenced));
        binding.localReportCount.setText(String.valueOf(snapshot.localSpamReports));
        binding.protectionSummaryText.setText(
                getString(
                        R.string.home_protection_live_summary,
                        snapshot.trustedNumbers,
                        snapshot.manuallyBlockedNumbers,
                        snapshot.pendingFollowUps
                )
        );
        styleStatusChip(
                binding.protectionStatusChip,
                snapshot.callerScreeningActive,
                snapshot.callerScreeningActive
                        ? R.string.home_protection_live_active
                        : R.string.home_protection_live_setup
        );

        renderRecentActivity(snapshot);
        renderSetupStatus(snapshot);
    }

    private void renderRecentActivity(@NonNull HomeDashboardRepository.Snapshot snapshot) {
        if (!snapshot.callLogAvailable) {
            binding.recentActivityTitle.setText(R.string.home_recent_access_title);
            binding.recentActivityBody.setText(R.string.home_recent_access_body);
            return;
        }

        HomeDashboardRepository.LatestCall latest = snapshot.latestCall;
        if (latest == null) {
            binding.recentActivityTitle.setText(R.string.home_recent_empty_title);
            binding.recentActivityBody.setText(R.string.home_recent_empty_body);
            return;
        }

        String title = latest.displayName.trim();
        if (title.isEmpty()) {
            title = getString(R.string.home_recent_unknown);
        }
        binding.recentActivityTitle.setText(title);

        String direction = getString(getCallTypeLabel(latest.type));
        if (latest.timestamp > 0L) {
            CharSequence relative = DateUtils.getRelativeTimeSpanString(
                    latest.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            );
            binding.recentActivityBody.setText(
                    getString(
                            R.string.home_recent_detail,
                            direction,
                            relative,
                            snapshot.pendingFollowUps
                    )
            );
        } else {
            binding.recentActivityBody.setText(
                    getString(
                            R.string.home_recent_detail_no_time,
                            direction,
                            snapshot.pendingFollowUps
                    )
            );
        }
    }

    private void renderSetupStatus(@NonNull HomeDashboardRepository.Snapshot snapshot) {
        boolean complete = snapshot.setupReadyCount >= snapshot.setupTotalCount;
        if (complete) {
            binding.setupStatusTitle.setText(R.string.home_setup_complete_title);
            binding.setupStatusBody.setText(R.string.home_setup_complete_body);
            binding.startSetupButton.setText(R.string.home_setup_review_action);
        } else {
            binding.setupStatusTitle.setText(R.string.home_setup_title);
            binding.setupStatusBody.setText(
                    getString(
                            R.string.home_setup_progress_short,
                            snapshot.setupReadyCount,
                            snapshot.setupTotalCount
                    )
            );
            binding.startSetupButton.setText(R.string.home_setup_action);
        }
    }

    private int getCallTypeLabel(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE:
                return R.string.home_recent_incoming;
            case CallLog.Calls.OUTGOING_TYPE:
                return R.string.home_recent_outgoing;
            case CallLog.Calls.MISSED_TYPE:
                return R.string.home_recent_missed;
            case CallLog.Calls.REJECTED_TYPE:
                return R.string.home_recent_rejected;
            case CallLog.Calls.BLOCKED_TYPE:
                return R.string.home_recent_blocked;
            default:
                return R.string.home_recent_other;
        }
    }

    private void styleStatusChip(
            @NonNull com.google.android.material.chip.Chip chip,
            boolean positive,
            int textRes
    ) {
        int foreground = ContextCompat.getColor(
                requireContext(),
                positive ? R.color.csp_safe : R.color.csp_unknown
        );
        int background = ContextCompat.getColor(
                requireContext(),
                positive ? R.color.csp_safe_container : R.color.csp_unknown_container
        );
        chip.setText(textRes);
        chip.setTextColor(foreground);
        chip.setChipBackgroundColor(ColorStateList.valueOf(background));
    }

    private void renderDashboardFallback() {
        binding.dashboardStatusChip.setText(R.string.home_dashboard_live_setup);
        binding.actionCallsSummary.setText(R.string.home_dashboard_loading_short);
        binding.actionContactsSummary.setText(R.string.home_dashboard_loading_short);
        binding.actionProtectionSummary.setText(R.string.home_dashboard_loading_short);
        binding.protectionSummaryText.setText(R.string.home_protection_subtitle);
        binding.recentActivityTitle.setText(R.string.home_activity_empty_title);
        binding.recentActivityBody.setText(R.string.home_activity_empty_body);
        binding.setupStatusBody.setText(R.string.home_setup_body);
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
        dashboardGeneration.incrementAndGet();
        if (dashboardExecutor != null) {
            dashboardExecutor.shutdownNow();
            dashboardExecutor = null;
        }
        dashboardLoading = false;
        dashboardRepository = null;
        ipIntelligenceAnalyzer = null;
        binding = null;
        super.onDestroyView();
    }
}
