package com.tridev.callsecurepro.ui.calls;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;
import android.text.format.DateFormat;
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

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.calls.CallNoteRepository;
import com.tridev.callsecurepro.data.calls.CallNoteEntity;
import com.tridev.callsecurepro.databinding.ActivityCallDetailBinding;
import com.tridev.callsecurepro.protection.CallerAssessment;
import com.tridev.callsecurepro.protection.CallerIntelligenceEngine;
import com.tridev.callsecurepro.ui.lookup.NumberLookupActivity;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CallDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CALL_LOG_ID =
            "com.tridev.callsecurepro.extra.CALL_LOG_ID";

    private ActivityCallDetailBinding binding;
    private ExecutorService detailExecutor;
    private CallNoteRepository noteRepository;
    private CallerIntelligenceEngine intelligenceEngine;
    private final AtomicInteger operationGeneration = new AtomicInteger();

    @Nullable
    private CallDetail loadedCall;
    @Nullable
    private CallNoteEntity loadedNote;

    private boolean suppressFollowUpSelection;
    private boolean followUpSelectionChanged;
    private long selectedFollowUpAt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        binding = ActivityCallDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        detailExecutor = Executors.newSingleThreadExecutor();
        noteRepository = new CallNoteRepository(this);
        intelligenceEngine = new CallerIntelligenceEngine(this);

        applySystemInsets();
        setupActions();
        loadCallDetails();
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.callDetailRoot, (view, windowInsets) -> {
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
        ViewCompat.requestApplyInsets(binding.callDetailRoot);
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.callButton.setOnClickListener(view -> callNumber());
        binding.messageButton.setOnClickListener(view -> messageNumber());
        binding.lookupButton.setOnClickListener(view -> lookupNumber());
        binding.saveButton.setOnClickListener(view -> saveNoteAndFollowUp());
        binding.markFollowUpDoneButton.setOnClickListener(view -> markFollowUpDone());

        binding.followUpChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (suppressFollowUpSelection || checkedIds.isEmpty()) {
                return;
            }

            followUpSelectionChanged = true;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.followUpNoneChip) {
                selectedFollowUpAt = 0L;
            } else if (checkedId == R.id.followUpTomorrowChip) {
                selectedFollowUpAt = futureTime(1);
            } else if (checkedId == R.id.followUpThreeDaysChip) {
                selectedFollowUpAt = futureTime(3);
            } else if (checkedId == R.id.followUpWeekChip) {
                selectedFollowUpAt = futureTime(7);
            }
            renderFollowUpPreview(selectedFollowUpAt, false);
        });
    }

    private void loadCallDetails() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            showError(R.string.call_detail_permission_required);
            return;
        }

        long callLogId = getIntent().getLongExtra(EXTRA_CALL_LOG_ID, -1L);
        if (callLogId < 0L || detailExecutor == null || detailExecutor.isShutdown()) {
            showError(R.string.call_detail_not_found);
            return;
        }

        setLoading(true);
        int generation = operationGeneration.incrementAndGet();
        detailExecutor.execute(() -> {
            CallDetail detail = queryCall(callLogId);
            if (detail == null) {
                runOnUiThread(() -> {
                    if (binding != null && generation == operationGeneration.get()) {
                        setLoading(false);
                        showError(R.string.call_detail_not_found);
                    }
                });
                return;
            }

            CallNoteEntity note = noteRepository.find(callLogId);
            InteractionStats stats = queryInteractionStats(detail.number);
            CallerAssessment assessment = intelligenceEngine.assess(detail.number);

            runOnUiThread(() -> {
                if (binding == null || generation != operationGeneration.get()) {
                    return;
                }
                loadedCall = detail;
                loadedNote = note;
                setLoading(false);
                render(detail, note, stats, assessment);
            });
        });
    }

    @Nullable
    private CallDetail queryCall(long callLogId) {
        String[] projection = new String[]{
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
        };

        try (Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                CallLog.Calls._ID + " = ?",
                new String[]{String.valueOf(callLogId)},
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
            int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
            int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);
            if (typeIndex < 0 || dateIndex < 0 || durationIndex < 0) {
                return null;
            }

            String number = readableNumber(numberIndex >= 0 ? cursor.getString(numberIndex) : null);
            String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
            return new CallDetail(
                    callLogId,
                    number,
                    name,
                    cursor.getInt(typeIndex),
                    cursor.getLong(dateIndex),
                    cursor.getLong(durationIndex)
            );
        } catch (SecurityException ignored) {
            return null;
        }
    }

    @NonNull
    private InteractionStats queryInteractionStats(@NonNull String targetNumber) {
        String normalizedTarget = PhoneNumberUtils.normalizeNumber(targetNumber);
        if (normalizedTarget == null || normalizedTarget.isEmpty()) {
            return new InteractionStats(0, 0, 0L);
        }

        long since = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);
        int calls = 0;
        int missed = 0;
        long talkSeconds = 0L;

        String[] projection = new String[]{
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION
        };

        try (Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                CallLog.Calls.DATE + " >= ?",
                new String[]{String.valueOf(since)},
                null
        )) {
            if (cursor == null) {
                return new InteractionStats(0, 0, 0L);
            }

            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);
            if (numberIndex < 0 || typeIndex < 0 || durationIndex < 0) {
                return new InteractionStats(0, 0, 0L);
            }

            while (cursor.moveToNext()) {
                String raw = cursor.getString(numberIndex);
                String normalized = raw == null ? "" : PhoneNumberUtils.normalizeNumber(raw);
                if (!normalizedTarget.equals(normalized)) {
                    continue;
                }

                calls++;
                int type = cursor.getInt(typeIndex);
                if (type == CallLog.Calls.MISSED_TYPE) {
                    missed++;
                }
                if (type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.OUTGOING_TYPE) {
                    talkSeconds += Math.max(0L, cursor.getLong(durationIndex));
                }
            }
        } catch (SecurityException ignored) {
            return new InteractionStats(0, 0, 0L);
        }

        return new InteractionStats(calls, missed, talkSeconds);
    }

    private void render(
            @NonNull CallDetail detail,
            @Nullable CallNoteEntity note,
            @NonNull InteractionStats stats,
            @NonNull CallerAssessment assessment
    ) {
        binding.detailContent.setVisibility(View.VISIBLE);
        binding.errorText.setVisibility(View.GONE);

        String title = detail.displayTitle();
        binding.callerName.setText(title);
        binding.callerNumber.setText(detail.number);
        binding.callerInitial.setText(initialFor(title));

        String dateTime = DateFormat.getMediumDateFormat(this).format(new Date(detail.timestamp))
                + ", "
                + DateFormat.getTimeFormat(this).format(new Date(detail.timestamp));
        binding.callTypeAndTime.setText(
                getString(R.string.call_detail_type_format, typeLabel(detail.type), dateTime)
        );
        binding.callDuration.setText(
                getString(R.string.call_detail_duration_format, formatDuration(detail.durationSeconds))
        );

        renderRisk(assessment);

        binding.thirtyDayCallsValue.setText(String.valueOf(stats.callCount));
        binding.missedValue.setText(String.valueOf(stats.missedCount));
        binding.talkTimeValue.setText(formatSummaryDuration(stats.talkSeconds));

        binding.noteInput.setText(note == null ? "" : note.noteText);
        selectedFollowUpAt = note == null ? 0L : note.followUpAt;
        followUpSelectionChanged = false;

        suppressFollowUpSelection = true;
        binding.followUpChipGroup.clearCheck();
        suppressFollowUpSelection = false;

        boolean done = note != null && note.followUpDone;
        renderFollowUpPreview(selectedFollowUpAt, done);
        binding.markFollowUpDoneButton.setVisibility(
                selectedFollowUpAt > 0L && !done ? View.VISIBLE : View.GONE
        );

        boolean dialable = isDialable(detail.number);
        binding.callButton.setEnabled(dialable);
        binding.messageButton.setEnabled(dialable);
        binding.lookupButton.setEnabled(dialable);
    }

    private void renderRisk(@NonNull CallerAssessment assessment) {
        int labelRes;
        int foreground;
        int background;

        switch (assessment.getLevel()) {
            case SAFE:
                labelRes = R.string.protection_result_safe;
                foreground = ContextCompat.getColor(this, R.color.csp_safe);
                background = ContextCompat.getColor(this, R.color.csp_safe_container);
                break;
            case SUSPICIOUS:
                labelRes = R.string.protection_result_suspicious;
                foreground = ContextCompat.getColor(this, R.color.csp_unknown);
                background = ContextCompat.getColor(this, R.color.csp_unknown_container);
                break;
            case SPAM:
                labelRes = R.string.protection_result_spam;
                foreground = ContextCompat.getColor(this, R.color.csp_spam);
                background = ContextCompat.getColor(this, R.color.csp_spam_container);
                break;
            case UNKNOWN:
            default:
                labelRes = R.string.protection_result_unknown;
                foreground = ContextCompat.getColor(this, R.color.csp_business);
                background = ContextCompat.getColor(this, R.color.csp_business_container);
                break;
        }

        binding.riskChip.setText(
                getString(
                        R.string.call_detail_risk_format,
                        getString(labelRes),
                        assessment.getRiskScore()
                )
        );
        binding.riskChip.setTextColor(foreground);
        binding.riskChip.setChipBackgroundColor(ColorStateList.valueOf(background));
        binding.riskReason.setText(assessment.getReason());
    }

    private void saveNoteAndFollowUp() {
        CallDetail detail = loadedCall;
        if (detail == null || detailExecutor == null || detailExecutor.isShutdown()) {
            return;
        }

        String noteText = binding.noteInput.getText() == null
                ? ""
                : binding.noteInput.getText().toString();

        long followUpAt = followUpSelectionChanged
                ? selectedFollowUpAt
                : loadedNote == null ? 0L : loadedNote.followUpAt;
        boolean done = !followUpSelectionChanged
                && loadedNote != null
                && loadedNote.followUpDone;

        if (followUpSelectionChanged && followUpAt > 0L) {
            done = false;
        }

        int generation = operationGeneration.incrementAndGet();
        setSaving(true);
        boolean finalDone = done;
        detailExecutor.execute(() -> {
            try {
                noteRepository.save(
                        detail.id,
                        detail.number,
                        detail.timestamp,
                        noteText,
                        followUpAt,
                        finalDone
                );
                CallNoteEntity refreshed = noteRepository.find(detail.id);
                runOnUiThread(() -> {
                    if (binding == null || generation != operationGeneration.get()) {
                        return;
                    }
                    loadedNote = refreshed;
                    selectedFollowUpAt = refreshed == null ? 0L : refreshed.followUpAt;
                    followUpSelectionChanged = false;
                    setSaving(false);
                    renderFollowUpPreview(
                            selectedFollowUpAt,
                            refreshed != null && refreshed.followUpDone
                    );
                    binding.markFollowUpDoneButton.setVisibility(
                            refreshed != null && refreshed.followUpAt > 0L && !refreshed.followUpDone
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                    Toast.makeText(this, R.string.call_detail_saved, Toast.LENGTH_SHORT).show();
                });
            } catch (RuntimeException exception) {
                runOnUiThread(() -> {
                    if (binding != null && generation == operationGeneration.get()) {
                        setSaving(false);
                        Toast.makeText(
                                this,
                                R.string.call_detail_save_failed,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            }
        });
    }

    private void markFollowUpDone() {
        CallDetail detail = loadedCall;
        CallNoteEntity note = loadedNote;
        if (detail == null || note == null || note.followUpAt <= 0L
                || detailExecutor == null || detailExecutor.isShutdown()) {
            return;
        }

        int generation = operationGeneration.incrementAndGet();
        setSaving(true);
        detailExecutor.execute(() -> {
            noteRepository.markFollowUpDone(detail.id);
            CallNoteEntity refreshed = noteRepository.find(detail.id);
            runOnUiThread(() -> {
                if (binding == null || generation != operationGeneration.get()) {
                    return;
                }
                loadedNote = refreshed;
                setSaving(false);
                renderFollowUpPreview(
                        refreshed == null ? 0L : refreshed.followUpAt,
                        refreshed != null && refreshed.followUpDone
                );
                binding.markFollowUpDoneButton.setVisibility(View.GONE);
            });
        });
    }

    private void renderFollowUpPreview(long followUpAt, boolean done) {
        if (done && followUpAt > 0L) {
            binding.followUpStatusChip.setText(R.string.call_detail_followup_completed);
            binding.followUpStatusChip.setTextColor(
                    ContextCompat.getColor(this, R.color.csp_safe)
            );
            binding.followUpStatusChip.setChipBackgroundColor(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.csp_safe_container))
            );
            return;
        }

        if (followUpAt <= 0L) {
            binding.followUpStatusChip.setText(R.string.call_detail_followup_none);
            binding.followUpStatusChip.setTextColor(
                    ContextCompat.getColor(this, R.color.csp_text_secondary)
            );
            binding.followUpStatusChip.setChipBackgroundColor(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.csp_surface_variant))
            );
            return;
        }

        String dateTime = DateFormat.getMediumDateFormat(this).format(new Date(followUpAt))
                + " "
                + DateFormat.getTimeFormat(this).format(new Date(followUpAt));
        binding.followUpStatusChip.setText(
                getString(R.string.call_detail_followup_pending_format, dateTime)
        );
        binding.followUpStatusChip.setTextColor(
                ContextCompat.getColor(this, R.color.csp_unknown)
        );
        binding.followUpStatusChip.setChipBackgroundColor(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.csp_unknown_container))
        );
    }

    private void callNumber() {
        CallDetail detail = loadedCall;
        if (detail == null || !isDialable(detail.number)) {
            showPrivateActionUnavailable();
            return;
        }

        try {
            startActivity(new Intent(
                    Intent.ACTION_DIAL,
                    Uri.fromParts("tel", detail.number, null)
            ));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.call_detail_no_phone_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void messageNumber() {
        CallDetail detail = loadedCall;
        if (detail == null || !isDialable(detail.number)) {
            showPrivateActionUnavailable();
            return;
        }

        try {
            startActivity(new Intent(
                    Intent.ACTION_SENDTO,
                    Uri.fromParts("smsto", detail.number, null)
            ));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.call_detail_no_message_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void lookupNumber() {
        CallDetail detail = loadedCall;
        if (detail == null || !isDialable(detail.number)) {
            showPrivateActionUnavailable();
            return;
        }

        Intent intent = new Intent(this, NumberLookupActivity.class);
        intent.putExtra(NumberLookupActivity.EXTRA_NUMBER, detail.number);
        startActivity(intent);
    }

    private void showPrivateActionUnavailable() {
        Toast.makeText(
                this,
                R.string.call_detail_private_action_unavailable,
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean isDialable(@NonNull String number) {
        String normalized = PhoneNumberUtils.normalizeNumber(number);
        return normalized != null && !normalized.isEmpty();
    }

    private long futureTime(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTimeInMillis();
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

    @NonNull
    private String initialFor(@NonNull String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty()
                ? "#"
                : trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    @NonNull
    private String typeLabel(int type) {
        if (type == CallLog.Calls.INCOMING_TYPE) {
            return getString(R.string.calls_incoming);
        }
        if (type == CallLog.Calls.OUTGOING_TYPE) {
            return getString(R.string.calls_outgoing);
        }
        if (type == CallLog.Calls.MISSED_TYPE) {
            return getString(R.string.calls_missed);
        }
        if (type == CallLog.Calls.BLOCKED_TYPE) {
            return getString(R.string.calls_blocked);
        }
        if (type == CallLog.Calls.REJECTED_TYPE) {
            return getString(R.string.calls_rejected);
        }
        return getString(R.string.calls_other);
    }

    @NonNull
    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.getDefault(), "%dh %dm %ds", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format(Locale.getDefault(), "%dm %ds", minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%ds", seconds);
    }

    @NonNull
    private String formatSummaryDuration(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (hours > 0L) {
            return getString(R.string.calls_duration_hours_format, hours, minutes);
        }
        if (minutes > 0L) {
            return getString(R.string.calls_duration_minutes_format, minutes);
        }
        return getString(R.string.calls_duration_minutes_format, 0);
    }

    private void setLoading(boolean loading) {
        binding.loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            binding.detailContent.setVisibility(View.GONE);
            binding.errorText.setVisibility(View.GONE);
        }
    }

    private void setSaving(boolean saving) {
        binding.saveButton.setEnabled(!saving);
        binding.markFollowUpDoneButton.setEnabled(!saving);
    }

    private void showError(int messageRes) {
        binding.loadingIndicator.setVisibility(View.GONE);
        binding.detailContent.setVisibility(View.GONE);
        binding.errorText.setVisibility(View.VISIBLE);
        binding.errorText.setText(messageRes);
    }

    @Override
    protected void onDestroy() {
        operationGeneration.incrementAndGet();
        if (detailExecutor != null) {
            detailExecutor.shutdownNow();
            detailExecutor = null;
        }
        binding = null;
        super.onDestroy();
    }

    private static final class CallDetail {
        private final long id;
        @NonNull
        private final String number;
        @Nullable
        private final String cachedName;
        private final int type;
        private final long timestamp;
        private final long durationSeconds;

        private CallDetail(
                long id,
                @NonNull String number,
                @Nullable String cachedName,
                int type,
                long timestamp,
                long durationSeconds
        ) {
            this.id = id;
            this.number = number;
            this.cachedName = cachedName;
            this.type = type;
            this.timestamp = timestamp;
            this.durationSeconds = durationSeconds;
        }

        @NonNull
        private String displayTitle() {
            if (cachedName != null && !cachedName.trim().isEmpty()) {
                return cachedName.trim();
            }
            return number;
        }
    }

    private static final class InteractionStats {
        private final int callCount;
        private final int missedCount;
        private final long talkSeconds;

        private InteractionStats(int callCount, int missedCount, long talkSeconds) {
            this.callCount = callCount;
            this.missedCount = missedCount;
            this.talkSeconds = talkSeconds;
        }
    }
}
