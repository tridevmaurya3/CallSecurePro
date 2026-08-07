package com.tridev.callsecurepro.ui.calls;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.calls.CallNoteRepository;
import com.tridev.callsecurepro.calls.CallReminderScheduler;
import com.tridev.callsecurepro.data.calls.CallNoteEntity;
import com.tridev.callsecurepro.databinding.ActivityMissedCallAssistantBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MissedCallAssistantActivity extends AppCompatActivity {

    private ActivityMissedCallAssistantBinding binding;
    private MissedCallAssistantAdapter adapter;
    private MissedCallAssistantRepository repository;
    private CallNoteRepository noteRepository;
    private ExecutorService executor;
    private final AtomicInteger generation = new AtomicInteger();
    private final List<MissedCallerItem> allItems = new ArrayList<>();

    private final ActivityResultLauncher<String> callLogPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> loadData()
            );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMissedCallAssistantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new MissedCallAssistantRepository(this);
        noteRepository = new CallNoteRepository(this);
        executor = Executors.newSingleThreadExecutor();
        adapter = new MissedCallAssistantAdapter(new MissedCallAssistantAdapter.Listener() {
            @Override
            public void onOpenDetails(@NonNull MissedCallerItem item) {
                openDetails(item);
            }

            @Override
            public void onCall(@NonNull MissedCallerItem item) {
                openDialer(item);
            }

            @Override
            public void onReply(@NonNull MissedCallerItem item) {
                showQuickReply(item);
            }

            @Override
            public void onRemind(@NonNull MissedCallerItem item) {
                showReminderPresets(item);
            }
        });

        applyInsets();
        binding.missedRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.missedRecyclerView.setAdapter(adapter);
        binding.backButton.setOnClickListener(view -> finish());
        binding.allowButton.setOnClickListener(view ->
                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        );
        setupSearch();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) {
            loadData();
        }
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.missedAssistantRoot, (view, insets) -> {
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
        ViewCompat.requestApplyInsets(binding.missedAssistantRoot);
    }

    private void setupSearch() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applySearch();
            }
        });
    }

    private void loadData() {
        ExecutorService currentExecutor = executor;
        MissedCallAssistantRepository currentRepository = repository;
        if (binding == null
                || currentExecutor == null
                || currentExecutor.isShutdown()
                || currentRepository == null) {
            return;
        }

        int operation = generation.incrementAndGet();
        binding.loadingIndicator.setVisibility(android.view.View.VISIBLE);
        currentExecutor.execute(() -> {
            MissedCallAssistantRepository.Snapshot snapshot = currentRepository.load();
            runOnUiThread(() -> renderSnapshot(operation, snapshot));
        });
    }

    private void renderSnapshot(
            int operation,
            @NonNull MissedCallAssistantRepository.Snapshot snapshot
    ) {
        if (binding == null || operation != generation.get()) {
            return;
        }
        binding.loadingIndicator.setVisibility(android.view.View.GONE);
        binding.permissionCard.setVisibility(
                snapshot.permissionGranted ? android.view.View.GONE : android.view.View.VISIBLE
        );
        binding.missedRecyclerView.setVisibility(
                snapshot.permissionGranted ? android.view.View.VISIBLE : android.view.View.GONE
        );

        binding.callerCount.setText(String.valueOf(snapshot.items.size()));
        binding.repeatCount.setText(String.valueOf(snapshot.repeatCallers));
        binding.eventCount.setText(String.valueOf(snapshot.totalEvents));

        allItems.clear();
        allItems.addAll(snapshot.items);
        applySearch();
    }

    private void applySearch() {
        if (binding == null || adapter == null) {
            return;
        }
        CharSequence value = binding.searchInput.getText();
        String query = value == null
                ? ""
                : value.toString().trim().toLowerCase(Locale.getDefault());
        List<MissedCallerItem> filtered = new ArrayList<>();
        for (MissedCallerItem item : allItems) {
            if (query.isEmpty()
                    || item.displayName.toLowerCase(Locale.getDefault()).contains(query)
                    || item.number.toLowerCase(Locale.getDefault()).contains(query)) {
                filtered.add(item);
            }
        }
        adapter.submitList(filtered);

        boolean empty = filtered.isEmpty()
                && binding.permissionCard.getVisibility() != android.view.View.VISIBLE;
        binding.emptyState.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        if (empty) {
            boolean narrowed = !query.isEmpty();
            binding.emptyTitle.setText(
                    narrowed
                            ? R.string.missed_assistant_no_match
                            : R.string.missed_assistant_empty_title
            );
            binding.emptyBody.setText(
                    narrowed
                            ? R.string.missed_assistant_no_match_body
                            : R.string.missed_assistant_empty_body
            );
        }
    }

    private void openDetails(@NonNull MissedCallerItem item) {
        Intent intent = new Intent(this, CallDetailActivity.class);
        intent.putExtra(CallDetailActivity.EXTRA_CALL_LOG_ID, item.latestCallLogId);
        startActivity(intent);
    }

    private void openDialer(@NonNull MissedCallerItem item) {
        if (!item.dialable) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", item.number, null));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.missed_assistant_no_phone_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void showQuickReply(@NonNull MissedCallerItem item) {
        if (!item.dialable) {
            return;
        }
        CharSequence[] replies = new CharSequence[]{
                getString(R.string.missed_assistant_reply_1),
                getString(R.string.missed_assistant_reply_2),
                getString(R.string.missed_assistant_reply_3)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.missed_assistant_reply_title)
                .setItems(replies, (dialog, which) -> {
                    if (which >= 0 && which < replies.length) {
                        openMessageComposer(item.number, replies[which].toString());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openMessageComposer(@NonNull String number, @NonNull String body) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(number)));
        intent.putExtra("sms_body", body);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.missed_assistant_no_message_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void showReminderPresets(@NonNull MissedCallerItem item) {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.missed_assistant_remind_2_hours),
                getString(R.string.missed_assistant_remind_tomorrow),
                getString(R.string.missed_assistant_remind_3_days)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.missed_assistant_remind_title)
                .setItems(options, (dialog, which) -> {
                    long now = System.currentTimeMillis();
                    long target;
                    if (which == 0) {
                        target = now + 2L * 60L * 60L * 1000L;
                    } else if (which == 1) {
                        target = now + 24L * 60L * 60L * 1000L;
                    } else {
                        target = now + 3L * 24L * 60L * 60L * 1000L;
                    }
                    saveReminder(item, target);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveReminder(@NonNull MissedCallerItem item, long target) {
        ExecutorService currentExecutor = executor;
        CallNoteRepository currentRepository = noteRepository;
        if (currentExecutor == null || currentExecutor.isShutdown() || currentRepository == null) {
            return;
        }
        currentExecutor.execute(() -> {
            CallNoteEntity existing = currentRepository.find(item.latestCallLogId);
            String noteText = existing == null ? "" : existing.noteText;
            currentRepository.save(
                    item.latestCallLogId,
                    item.number,
                    item.latestTimestamp,
                    noteText,
                    target,
                    false
            );
            CallNoteEntity saved = currentRepository.find(item.latestCallLogId);
            if (saved != null) {
                CallReminderScheduler.syncFollowUp(this, saved);
            }
            runOnUiThread(() -> showReminderSavedToast());
        });
    }

    private void showReminderSavedToast() {
        if (binding == null) {
            return;
        }
        boolean notificationsReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
        Toast.makeText(
                this,
                notificationsReady
                        ? R.string.missed_assistant_reminder_saved
                        : R.string.missed_assistant_reminder_saved_notifications_off,
                Toast.LENGTH_SHORT
        ).show();
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
        noteRepository = null;
        binding = null;
        super.onDestroy();
    }
}
