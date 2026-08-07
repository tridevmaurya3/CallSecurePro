package com.tridev.callsecurepro.ui.calls;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ActivityFollowUpCenterBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FollowUpCenterActivity extends AppCompatActivity {

    private enum Filter {
        ALL,
        OVERDUE,
        UPCOMING,
        DONE
    }

    private ActivityFollowUpCenterBinding binding;
    private FollowUpCenterAdapter adapter;
    private FollowUpCenterRepository repository;
    private ExecutorService executor;
    private final AtomicInteger generation = new AtomicInteger();
    private final List<FollowUpCenterItem> allItems = new ArrayList<>();
    private Filter activeFilter = Filter.ALL;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityFollowUpCenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new FollowUpCenterRepository(this);
        executor = Executors.newSingleThreadExecutor();
        adapter = new FollowUpCenterAdapter(new FollowUpCenterAdapter.Listener() {
            @Override
            public void onOpenDetails(@NonNull FollowUpCenterItem item) {
                openDetails(item);
            }

            @Override
            public void onMarkDone(@NonNull FollowUpCenterItem item) {
                markDone(item);
            }

            @Override
            public void onSnooze(@NonNull FollowUpCenterItem item) {
                snooze(item);
            }
        });

        applyInsets();
        binding.followUpRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.followUpRecyclerView.setAdapter(adapter);
        setupActions();
        updateNotificationStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) {
            updateNotificationStatus();
            loadData(true);
        }
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.followUpRoot, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    Math.max(view.getPaddingLeft(), bars.left),
                    Math.max(view.getPaddingTop(), bars.top),
                    Math.max(view.getPaddingRight(), bars.right),
                    Math.max(view.getPaddingBottom(), bars.bottom)
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.followUpRoot);
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilters();
            }
        });

        binding.filterAll.setOnClickListener(view -> {
            activeFilter = Filter.ALL;
            applyFilters();
        });
        binding.filterOverdue.setOnClickListener(view -> {
            activeFilter = Filter.OVERDUE;
            applyFilters();
        });
        binding.filterUpcoming.setOnClickListener(view -> {
            activeFilter = Filter.UPCOMING;
            applyFilters();
        });
        binding.filterDone.setOnClickListener(view -> {
            activeFilter = Filter.DONE;
            applyFilters();
        });
    }

    private void loadData(boolean reconcile) {
        ExecutorService currentExecutor = executor;
        FollowUpCenterRepository currentRepository = repository;
        if (currentExecutor == null || currentExecutor.isShutdown() || currentRepository == null) {
            return;
        }

        int operation = generation.incrementAndGet();
        binding.loadingIndicator.setVisibility(View.VISIBLE);
        currentExecutor.execute(() -> {
            if (reconcile) {
                currentRepository.reconcilePending(this);
            }
            FollowUpCenterRepository.Snapshot snapshot = currentRepository.load();
            runOnUiThread(() -> renderSnapshot(operation, snapshot));
        });
    }

    private void renderSnapshot(
            int operation,
            @NonNull FollowUpCenterRepository.Snapshot snapshot
    ) {
        if (binding == null || operation != generation.get()) {
            return;
        }
        binding.loadingIndicator.setVisibility(View.GONE);
        allItems.clear();
        allItems.addAll(snapshot.items);
        binding.overdueCount.setText(String.valueOf(snapshot.stats.overdue));
        binding.upcomingCount.setText(String.valueOf(snapshot.stats.upcoming));
        binding.doneCount.setText(String.valueOf(snapshot.stats.completed));
        applyFilters();
    }

    private void applyFilters() {
        if (binding == null || adapter == null) {
            return;
        }

        CharSequence queryText = binding.searchInput.getText();
        String query = queryText == null
                ? ""
                : queryText.toString().trim().toLowerCase(Locale.getDefault());
        long now = System.currentTimeMillis();
        List<FollowUpCenterItem> filtered = new ArrayList<>();

        for (FollowUpCenterItem item : allItems) {
            if (!matchesFilter(item, now)) {
                continue;
            }
            if (!query.isEmpty()) {
                String searchable = (item.displayName + " " + item.number + " " + item.noteText)
                        .toLowerCase(Locale.getDefault());
                if (!searchable.contains(query)) {
                    continue;
                }
            }
            filtered.add(item);
        }

        adapter.submitList(filtered);
        binding.resultCount.setText(getString(R.string.follow_up_center_count_format, filtered.size()));
        boolean empty = filtered.isEmpty();
        binding.followUpRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            boolean narrowed = activeFilter != Filter.ALL || !query.isEmpty();
            binding.emptyTitle.setText(
                    narrowed ? R.string.follow_up_center_no_match : R.string.follow_up_center_empty_title
            );
            binding.emptyBody.setText(
                    narrowed ? R.string.follow_up_center_no_match_body : R.string.follow_up_center_empty_body
            );
        }
    }

    private boolean matchesFilter(@NonNull FollowUpCenterItem item, long now) {
        FollowUpCenterItem.Bucket bucket = item.bucket(now);
        switch (activeFilter) {
            case OVERDUE:
                return bucket == FollowUpCenterItem.Bucket.OVERDUE;
            case UPCOMING:
                return bucket == FollowUpCenterItem.Bucket.UPCOMING;
            case DONE:
                return bucket == FollowUpCenterItem.Bucket.DONE;
            case ALL:
            default:
                return true;
        }
    }

    private void openDetails(@NonNull FollowUpCenterItem item) {
        if (!item.callLogAvailable) {
            return;
        }
        Intent intent = new Intent(this, CallDetailActivity.class);
        intent.putExtra(CallDetailActivity.EXTRA_CALL_LOG_ID, item.callLogId);
        startActivity(intent);
    }

    private void markDone(@NonNull FollowUpCenterItem item) {
        mutate(item.callLogId, true);
    }

    private void snooze(@NonNull FollowUpCenterItem item) {
        mutate(item.callLogId, false);
    }

    private void mutate(long callLogId, boolean markDone) {
        ExecutorService currentExecutor = executor;
        FollowUpCenterRepository currentRepository = repository;
        if (currentExecutor == null || currentExecutor.isShutdown() || currentRepository == null) {
            return;
        }
        int operation = generation.incrementAndGet();
        binding.loadingIndicator.setVisibility(View.VISIBLE);
        currentExecutor.execute(() -> {
            if (markDone) {
                currentRepository.markDone(this, callLogId);
            } else {
                currentRepository.snoozeOneDay(this, callLogId);
            }
            FollowUpCenterRepository.Snapshot snapshot = currentRepository.load();
            runOnUiThread(() -> {
                renderSnapshot(operation, snapshot);
                if (binding != null && operation == generation.get()) {
                    Toast.makeText(
                            this,
                            markDone
                                    ? R.string.follow_up_center_marked_done
                                    : R.string.follow_up_center_snoozed,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        });
    }

    private void updateNotificationStatus() {
        boolean ready = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
        binding.notificationStatus.setText(
                ready
                        ? R.string.follow_up_center_notification_ready
                        : R.string.follow_up_center_notification_off
        );
        binding.notificationStatus.setTextColor(ContextCompat.getColor(
                this,
                ready ? R.color.csp_safe : R.color.csp_warning
        ));
    }

    @Override
    protected void onDestroy() {
        generation.incrementAndGet();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        adapter = null;
        repository = null;
        binding = null;
        super.onDestroy();
    }
}
