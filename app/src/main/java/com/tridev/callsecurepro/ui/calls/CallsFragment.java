package com.tridev.callsecurepro.ui.calls;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.calls.CallNoteRepository;
import com.tridev.callsecurepro.calls.CallReminderPreferences;
import com.tridev.callsecurepro.data.calls.CallNoteEntity;
import com.tridev.callsecurepro.databinding.FragmentCallsBinding;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallsFragment extends Fragment {

    private static final int MAX_CALL_LOG_ITEMS = 500;

    private enum CallFilter {
        ALL,
        INCOMING,
        OUTGOING,
        MISSED,
        BLOCKED
    }

    private FragmentCallsBinding binding;
    private CallsAdapter callsAdapter;
    private ExecutorService callLogExecutor;
    private CallNoteRepository callNoteRepository;
    private final List<CallHistoryItem> allCalls = new ArrayList<>();
    private CallFilter activeFilter = CallFilter.ALL;
    private boolean loadingCalls;
    private boolean suppressPostCallPromptSwitch;

    private final ActivityResultLauncher<String> callLogPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (binding == null) {
                            return;
                        }
                        if (granted) {
                            showCallsContent();
                            loadCallHistory();
                        } else {
                            showPermissionState();
                        }
                    }
            );

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        CallReminderPreferences.setPostCallPromptEnabled(requireContext(), granted);
                        setPostCallPromptSwitch(granted);
                        if (!granted) {
                            Toast.makeText(
                                    requireContext(),
                                    R.string.calls_post_call_prompt_permission,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentCallsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        callLogExecutor = Executors.newSingleThreadExecutor();
        callNoteRepository = new CallNoteRepository(requireContext());
        callsAdapter = new CallsAdapter(new CallsAdapter.Listener() {
            @Override
            public void onOpenDetails(@NonNull CallHistoryItem item) {
                openCallDetails(item);
            }

            @Override
            public void onCallBack(@NonNull CallHistoryItem item) {
                openCallbackDialer(item);
            }
        });

        binding.callsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.callsRecyclerView.setAdapter(callsAdapter);
        binding.callsRecyclerView.setHasFixedSize(false);

        binding.allowCallLogButton.setOnClickListener(view1 ->
                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        );
        binding.refreshButton.setOnClickListener(view1 -> loadCallHistory());

        setupFollowUpInbox();
        setupPostCallPrompt();
        setupSearch();
        setupFilters();
        refreshFollowUpInbox();
        refreshPermissionAndData();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshPostCallPromptState();
            refreshFollowUpInbox();
            refreshPermissionAndData();
        }
    }

    private void setupFollowUpInbox() {
        View.OnClickListener listener = view -> openFollowUpCenter();
        binding.followUpInboxCard.setOnClickListener(listener);
        binding.followUpInboxButton.setOnClickListener(listener);
    }

    private void refreshFollowUpInbox() {
        ExecutorService executor = callLogExecutor;
        CallNoteRepository repository = callNoteRepository;
        if (!isAdded() || executor == null || executor.isShutdown() || repository == null) {
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            repository.reconcilePendingFollowUps(appContext);
            CallNoteRepository.FollowUpStats stats = repository.getFollowUpStats();
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (binding != null) {
                    binding.followUpInboxBody.setText(getString(
                            R.string.calls_follow_up_card_body,
                            stats.overdue,
                            stats.upcoming
                    ));
                }
            });
        });
    }

    private void openFollowUpCenter() {
        startActivity(new Intent(requireContext(), FollowUpCenterActivity.class));
    }

    private void setupPostCallPrompt() {
        refreshPostCallPromptState();
        binding.postCallPromptSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (suppressPostCallPromptSwitch || !isAdded()) {
                return;
            }

            if (!checked) {
                CallReminderPreferences.setPostCallPromptEnabled(requireContext(), false);
                return;
            }

            if (canPostNotifications()) {
                CallReminderPreferences.setPostCallPromptEnabled(requireContext(), true);
                return;
            }

            CallReminderPreferences.setPostCallPromptEnabled(requireContext(), false);
            setPostCallPromptSwitch(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
    }

    private void refreshPostCallPromptState() {
        if (!isAdded() || binding == null) {
            return;
        }
        boolean enabled = CallReminderPreferences.isPostCallPromptEnabled(requireContext());
        if (enabled && !canPostNotifications()) {
            enabled = false;
            CallReminderPreferences.setPostCallPromptEnabled(requireContext(), false);
        }
        setPostCallPromptSwitch(enabled);
    }

    private void setPostCallPromptSwitch(boolean checked) {
        if (binding == null) {
            return;
        }
        suppressPostCallPromptSwitch = true;
        binding.postCallPromptSwitch.setChecked(checked);
        suppressPostCallPromptSwitch = false;
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void setupSearch() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action required.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Filtering is applied after the edit is complete.
            }

            @Override
            public void afterTextChanged(Editable editable) {
                applyFilters();
            }
        });
    }

    private void setupFilters() {
        binding.filterAll.setOnClickListener(view -> {
            activeFilter = CallFilter.ALL;
            applyFilters();
        });
        binding.filterIncoming.setOnClickListener(view -> {
            activeFilter = CallFilter.INCOMING;
            applyFilters();
        });
        binding.filterOutgoing.setOnClickListener(view -> {
            activeFilter = CallFilter.OUTGOING;
            applyFilters();
        });
        binding.filterMissed.setOnClickListener(view -> {
            activeFilter = CallFilter.MISSED;
            applyFilters();
        });
        binding.filterBlocked.setOnClickListener(view -> {
            activeFilter = CallFilter.BLOCKED;
            applyFilters();
        });
    }

    private void refreshPermissionAndData() {
        if (hasCallLogPermission()) {
            showCallsContent();
            if (!loadingCalls) {
                loadCallHistory();
            }
        } else {
            showPermissionState();
        }
    }

    private boolean hasCallLogPermission() {
        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionState() {
        binding.permissionCard.setVisibility(View.VISIBLE);
        binding.callsContent.setVisibility(View.GONE);
        loadingCalls = false;
    }

    private void showCallsContent() {
        binding.permissionCard.setVisibility(View.GONE);
        binding.callsContent.setVisibility(View.VISIBLE);
    }

    private void loadCallHistory() {
        if (!hasCallLogPermission()
                || loadingCalls
                || callLogExecutor == null
                || callLogExecutor.isShutdown()
                || callNoteRepository == null) {
            return;
        }

        loadingCalls = true;
        binding.loadingIndicator.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);
        binding.refreshButton.setEnabled(false);

        callLogExecutor.execute(() -> {
            List<CallHistoryItem> loadedCalls = queryCallLog();
            List<CallHistoryItem> enrichedCalls = enrichWithSmartMetadata(loadedCalls);

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }

                loadingCalls = false;
                binding.loadingIndicator.setVisibility(View.GONE);
                binding.refreshButton.setEnabled(true);

                allCalls.clear();
                allCalls.addAll(enrichedCalls);
                updateSmartSummary();
                applyFilters();
            });
        });
    }

    @NonNull
    private List<CallHistoryItem> enrichWithSmartMetadata(
            @NonNull List<CallHistoryItem> calls
    ) {
        if (calls.isEmpty() || callNoteRepository == null) {
            return calls;
        }

        List<Long> ids = new ArrayList<>(calls.size());
        for (CallHistoryItem item : calls) {
            ids.add(item.getId());
        }

        Map<Long, CallNoteEntity> notes = callNoteRepository.findForCallIds(ids);
        if (notes.isEmpty()) {
            return calls;
        }

        List<CallHistoryItem> result = new ArrayList<>(calls.size());
        for (CallHistoryItem item : calls) {
            CallNoteEntity note = notes.get(item.getId());
            if (note == null) {
                result.add(item);
            } else {
                result.add(item.withNoteMetadata(
                        note.noteText,
                        note.followUpAt,
                        note.followUpDone
                ));
            }
        }
        return result;
    }

    @NonNull
    private List<CallHistoryItem> queryCallLog() {
        List<CallHistoryItem> result = new ArrayList<>();

        String[] projection = new String[]{
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
        };

        String sortOrder = CallLog.Calls.DATE + " DESC";

        try (Cursor cursor = requireContext().getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
        )) {
            if (cursor == null) {
                return result;
            }

            int idIndex = cursor.getColumnIndex(CallLog.Calls._ID);
            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
            int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
            int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);

            while (cursor.moveToNext() && result.size() < MAX_CALL_LOG_ITEMS) {
                if (idIndex < 0 || typeIndex < 0 || dateIndex < 0 || durationIndex < 0) {
                    continue;
                }

                long id = cursor.getLong(idIndex);
                String rawNumber = numberIndex >= 0 ? cursor.getString(numberIndex) : null;
                String number = readableNumber(rawNumber);
                String cachedName = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                int type = cursor.getInt(typeIndex);
                long timestamp = cursor.getLong(dateIndex);
                long duration = cursor.getLong(durationIndex);

                result.add(new CallHistoryItem(
                        id,
                        number,
                        cachedName,
                        type,
                        timestamp,
                        duration
                ));
            }
        } catch (SecurityException ignored) {
            // Permission state will be re-checked by the UI on the next lifecycle pass.
        }

        return result;
    }

    @NonNull
    private String readableNumber(@Nullable String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty() || "-1".equals(rawNumber.trim())) {
            return getString(R.string.calls_unknown_number);
        }
        if ("-2".equals(rawNumber.trim())) {
            return getString(R.string.calls_private_number);
        }
        if ("-3".equals(rawNumber.trim())) {
            return getString(R.string.calls_restricted_number);
        }
        return rawNumber.trim();
    }

    private void updateSmartSummary() {
        long todayStart = startOfTodayMillis();
        int todayCount = 0;
        int missedCount = 0;
        long talkSeconds = 0;

        for (CallHistoryItem item : allCalls) {
            if (item.getTimestamp() < todayStart) {
                continue;
            }

            todayCount++;
            if (item.getType() == CallLog.Calls.MISSED_TYPE) {
                missedCount++;
            }
            if (item.getType() == CallLog.Calls.INCOMING_TYPE
                    || item.getType() == CallLog.Calls.OUTGOING_TYPE) {
                talkSeconds += item.getDurationSeconds();
            }
        }

        binding.todayCallsValue.setText(String.valueOf(todayCount));
        binding.missedCallsValue.setText(String.valueOf(missedCount));
        binding.talkTimeValue.setText(formatSummaryDuration(talkSeconds));
    }

    private long startOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    @NonNull
    private String formatSummaryDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (hours > 0) {
            return getString(R.string.calls_duration_hours_format, hours, minutes);
        }
        if (minutes > 0) {
            return getString(R.string.calls_duration_minutes_format, minutes);
        }
        if (totalSeconds > 0) {
            return getString(R.string.calls_duration_seconds_format, totalSeconds);
        }
        return getString(R.string.calls_duration_minutes_format, 0);
    }

    private void applyFilters() {
        if (binding == null || callsAdapter == null) {
            return;
        }

        CharSequence searchText = binding.searchInput.getText();
        String query = searchText == null
                ? ""
                : searchText.toString().trim().toLowerCase(Locale.getDefault());

        List<CallHistoryItem> filtered = new ArrayList<>();
        for (CallHistoryItem item : allCalls) {
            if (!matchesActiveFilter(item)) {
                continue;
            }

            if (!query.isEmpty()) {
                String title = item.getDisplayTitle().toLowerCase(Locale.getDefault());
                String number = item.getNumber().toLowerCase(Locale.getDefault());
                String note = item.getNoteText() == null
                        ? ""
                        : item.getNoteText().toLowerCase(Locale.getDefault());
                if (!title.contains(query) && !number.contains(query) && !note.contains(query)) {
                    continue;
                }
            }

            filtered.add(item);
        }

        callsAdapter.submitList(filtered);
        binding.resultCount.setText(getString(R.string.calls_count_format, filtered.size()));

        boolean hasResults = !filtered.isEmpty();
        binding.callsRecyclerView.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        binding.emptyState.setVisibility(hasResults ? View.GONE : View.VISIBLE);

        if (!hasResults) {
            boolean narrowed = !query.isEmpty() || activeFilter != CallFilter.ALL;
            binding.emptyTitle.setText(
                    narrowed ? R.string.calls_search_empty_title : R.string.calls_empty_title
            );
            binding.emptyBody.setText(
                    narrowed ? R.string.calls_search_empty_body : R.string.calls_empty_body
            );
        }
    }

    private boolean matchesActiveFilter(@NonNull CallHistoryItem item) {
        int type = item.getType();

        switch (activeFilter) {
            case INCOMING:
                return type == CallLog.Calls.INCOMING_TYPE;
            case OUTGOING:
                return type == CallLog.Calls.OUTGOING_TYPE;
            case MISSED:
                return type == CallLog.Calls.MISSED_TYPE;
            case BLOCKED:
                return type == CallLog.Calls.BLOCKED_TYPE
                        || type == CallLog.Calls.REJECTED_TYPE;
            case ALL:
            default:
                return true;
        }
    }

    private void openCallDetails(@NonNull CallHistoryItem item) {
        Intent intent = new Intent(requireContext(), CallDetailActivity.class);
        intent.putExtra(CallDetailActivity.EXTRA_CALL_LOG_ID, item.getId());
        startActivity(intent);
    }

    private void openCallbackDialer(@NonNull CallHistoryItem item) {
        String number = item.getNumber().trim();
        if (number.isEmpty()
                || number.equals(getString(R.string.calls_unknown_number))
                || number.equals(getString(R.string.calls_private_number))
                || number.equals(getString(R.string.calls_restricted_number))) {
            return;
        }

        Intent dialIntent = new Intent(
                Intent.ACTION_DIAL,
                Uri.fromParts("tel", number, null)
        );

        try {
            startActivity(dialIntent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    requireContext(),
                    R.string.calls_no_phone_app,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onDestroyView() {
        if (callLogExecutor != null) {
            callLogExecutor.shutdownNow();
            callLogExecutor = null;
        }
        callNoteRepository = null;
        callsAdapter = null;
        binding = null;
        super.onDestroyView();
    }
}
