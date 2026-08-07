package com.tridev.callsecurepro.ui.calls;

import android.Manifest;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ActivityCallAnalyticsBinding;
import com.tridev.callsecurepro.theme.AppVisualThemeManager;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CallAnalyticsActivity extends AppCompatActivity {

    private ActivityCallAnalyticsBinding binding;
    private CallAnalyticsRepository repository;
    private ExecutorService executor;
    private final AtomicInteger generation = new AtomicInteger();

    private final ActivityResultLauncher<String> callLogPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> loadAnalytics()
            );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCallAnalyticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new CallAnalyticsRepository(this);
        executor = Executors.newSingleThreadExecutor();

        applyInsets();
        binding.backButton.setOnClickListener(view -> finish());
        binding.allowButton.setOnClickListener(view ->
                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        );
        loadAnalytics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) {
            loadAnalytics();
        }
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.analyticsRoot, (view, insets) -> {
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
        ViewCompat.requestApplyInsets(binding.analyticsRoot);
    }

    private void loadAnalytics() {
        ExecutorService currentExecutor = executor;
        CallAnalyticsRepository currentRepository = repository;
        if (binding == null
                || currentExecutor == null
                || currentExecutor.isShutdown()
                || currentRepository == null) {
            return;
        }

        int operation = generation.incrementAndGet();
        binding.loadingIndicator.setVisibility(View.VISIBLE);
        currentExecutor.execute(() -> {
            CallAnalyticsRepository.Snapshot snapshot = currentRepository.load();
            runOnUiThread(() -> render(operation, snapshot));
        });
    }

    private void render(int operation, @NonNull CallAnalyticsRepository.Snapshot snapshot) {
        if (binding == null || operation != generation.get()) {
            return;
        }

        binding.loadingIndicator.setVisibility(View.GONE);
        binding.permissionCard.setVisibility(
                snapshot.permissionGranted ? View.GONE : View.VISIBLE
        );
        binding.analyticsContent.setVisibility(
                snapshot.permissionGranted ? View.VISIBLE : View.GONE
        );
        if (!snapshot.permissionGranted) {
            return;
        }

        binding.totalCallsValue.setText(String.valueOf(snapshot.total30));
        binding.talkTimeValue.setText(formatDuration(snapshot.talkSeconds30));
        binding.averageCallValue.setText(formatDuration(snapshot.averageAnsweredSeconds));
        binding.uniqueCallersValue.setText(String.valueOf(snapshot.uniqueCallers30));

        renderCallMix(snapshot);
        renderTrend(snapshot);
        renderPatterns(snapshot);
        renderTopContacts(snapshot.topContacts);
    }

    private void renderCallMix(@NonNull CallAnalyticsRepository.Snapshot snapshot) {
        int mixTotal = snapshot.incoming30
                + snapshot.outgoing30
                + snapshot.missed30
                + snapshot.blockedRejected30;
        int incomingPercent = percent(snapshot.incoming30, mixTotal);
        int outgoingPercent = percent(snapshot.outgoing30, mixTotal);
        int missedPercent = percent(snapshot.missed30, mixTotal);
        int blockedPercent = percent(snapshot.blockedRejected30, mixTotal);

        binding.incomingLabel.setText(
                getString(R.string.call_analytics_incoming)
                        + " • "
                        + getString(
                        R.string.call_analytics_count_percent,
                        snapshot.incoming30,
                        incomingPercent
                )
        );
        binding.outgoingLabel.setText(
                getString(R.string.call_analytics_outgoing)
                        + " • "
                        + getString(
                        R.string.call_analytics_count_percent,
                        snapshot.outgoing30,
                        outgoingPercent
                )
        );
        binding.missedLabel.setText(
                getString(R.string.call_analytics_missed)
                        + " • "
                        + getString(
                        R.string.call_analytics_count_percent,
                        snapshot.missed30,
                        missedPercent
                )
        );
        binding.blockedLabel.setText(
                getString(R.string.call_analytics_blocked_rejected)
                        + " • "
                        + getString(
                        R.string.call_analytics_count_percent,
                        snapshot.blockedRejected30,
                        blockedPercent
                )
        );

        int accent = AppVisualThemeManager.accentColor(this);
        applyProgress(binding.incomingProgress, incomingPercent, accent);
        applyProgress(binding.outgoingProgress, outgoingPercent, accent);
        applyProgress(binding.missedProgress, missedPercent, accent);
    }

    private void applyProgress(
            @NonNull LinearProgressIndicator indicator,
            int progress,
            int color
    ) {
        indicator.setIndicatorColor(color);
        indicator.setTrackColor(ColorStateList.valueOf(color).withAlpha(35).getDefaultColor());
        indicator.setProgressCompat(progress, false);
    }

    private void renderTrend(@NonNull CallAnalyticsRepository.Snapshot snapshot) {
        int delta = snapshot.current7 - snapshot.previous7;
        if (delta > 0) {
            binding.trendSummary.setText(getString(
                    R.string.call_analytics_trend_up,
                    snapshot.current7,
                    delta
            ));
        } else if (delta < 0) {
            binding.trendSummary.setText(getString(
                    R.string.call_analytics_trend_down,
                    snapshot.current7,
                    delta
            ));
        } else {
            binding.trendSummary.setText(getString(
                    R.string.call_analytics_trend_same,
                    snapshot.current7
            ));
        }

        binding.trendContainer.removeAllViews();
        int max = 1;
        for (int value : snapshot.last7DayCounts) {
            max = Math.max(max, value);
        }

        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_YEAR, -6);
        SimpleDateFormat formatter = new SimpleDateFormat("EEE", Locale.getDefault());
        int accent = AppVisualThemeManager.accentColor(this);

        for (int index = 0; index < snapshot.last7DayCounts.length; index++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(2), 0, dp(2));

            TextView label = new TextView(this);
            label.setText(formatter.format(day.getTime()));
            label.setTextSize(11f);
            label.setTextColor(themeSecondaryText());
            row.addView(label, new LinearLayout.LayoutParams(dp(42), dp(28)));

            LinearProgressIndicator bar = new LinearProgressIndicator(this);
            bar.setMax(max);
            bar.setIndicatorColor(accent);
            bar.setTrackColor(ColorStateList.valueOf(accent).withAlpha(30).getDefaultColor());
            bar.setTrackThickness(dp(6));
            bar.setProgressCompat(snapshot.last7DayCounts[index], false);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            barParams.setMarginStart(dp(4));
            barParams.setMarginEnd(dp(8));
            row.addView(bar, barParams);

            TextView count = new TextView(this);
            count.setText(String.valueOf(snapshot.last7DayCounts[index]));
            count.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
            count.setTextSize(11f);
            count.setTextColor(themePrimaryText());
            row.addView(count, new LinearLayout.LayoutParams(dp(28), dp(28)));

            binding.trendContainer.addView(row);
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void renderPatterns(@NonNull CallAnalyticsRepository.Snapshot snapshot) {
        binding.busiestDayValue.setText(busiestDayLabel(snapshot.busiestWeekdayIndex));
        binding.busiestTimeValue.setText(busiestTimeLabel(snapshot.busiestTimeWindow));
        binding.repeatMissedValue.setText(getString(
                R.string.call_analytics_repeat_missed_value,
                snapshot.repeatMissedCallers7
        ));
    }

    @NonNull
    private String busiestDayLabel(int weekdayIndex) {
        if (weekdayIndex < 0 || weekdayIndex > 6) {
            return getString(R.string.call_analytics_no_data);
        }
        String[] weekdays = DateFormatSymbols.getInstance(Locale.getDefault()).getWeekdays();
        int calendarDay = weekdayIndex + Calendar.SUNDAY;
        if (calendarDay < 1 || calendarDay >= weekdays.length) {
            return getString(R.string.call_analytics_no_data);
        }
        String value = weekdays[calendarDay];
        return value == null || value.trim().isEmpty()
                ? getString(R.string.call_analytics_no_data)
                : value;
    }

    @NonNull
    private String busiestTimeLabel(int timeWindow) {
        switch (timeWindow) {
            case CallAnalyticsRepository.TIME_MORNING:
                return getString(R.string.call_analytics_morning);
            case CallAnalyticsRepository.TIME_AFTERNOON:
                return getString(R.string.call_analytics_afternoon);
            case CallAnalyticsRepository.TIME_EVENING:
                return getString(R.string.call_analytics_evening);
            case CallAnalyticsRepository.TIME_NIGHT:
                return getString(R.string.call_analytics_night);
            default:
                return getString(R.string.call_analytics_no_data);
        }
    }

    private void renderTopContacts(@NonNull List<CallAnalyticsRepository.TopContact> contacts) {
        binding.topContactsContainer.removeAllViews();
        if (contacts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.call_analytics_top_empty);
            empty.setTextSize(12f);
            empty.setTextColor(themeSecondaryText());
            empty.setPadding(0, dp(4), 0, dp(4));
            binding.topContactsContainer.addView(empty);
            return;
        }

        for (int index = 0; index < contacts.size(); index++) {
            CallAnalyticsRepository.TopContact contact = contacts.get(index);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView title = new TextView(this);
            String titleText = contact.displayName.equals(contact.number)
                    ? contact.displayName
                    : contact.displayName + " • " + contact.number;
            title.setText(titleText);
            title.setTextSize(14f);
            title.setTextColor(themePrimaryText());
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(title);

            TextView meta = new TextView(this);
            meta.setText(getString(
                    R.string.call_analytics_top_contact_meta,
                    contact.interactionCount,
                    formatDuration(contact.talkSeconds)
            ));
            meta.setTextSize(11f);
            meta.setTextColor(themeSecondaryText());
            meta.setPadding(0, dp(2), 0, 0);
            row.addView(meta);

            binding.topContactsContainer.addView(row);

            if (index < contacts.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(ColorStateList.valueOf(themeSecondaryText())
                        .withAlpha(35)
                        .getDefaultColor());
                binding.topContactsContainer.addView(
                        divider,
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(1)
                        )
                );
            }
        }
    }

    private int percent(int value, int total) {
        if (value <= 0 || total <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, Math.round(value * 100f / total)));
    }

    @NonNull
    private String formatDuration(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0L) {
            return getString(R.string.call_analytics_hours_minutes, hours, minutes);
        }
        if (minutes > 0L) {
            return getString(R.string.call_analytics_minutes, minutes);
        }
        return getString(R.string.call_analytics_seconds, seconds);
    }

    private int themePrimaryText() {
        return AppVisualThemeManager.isDarkBackground(this)
                ? android.graphics.Color.rgb(245, 247, 250)
                : android.graphics.Color.rgb(25, 30, 38);
    }

    private int themeSecondaryText() {
        return AppVisualThemeManager.isDarkBackground(this)
                ? android.graphics.Color.rgb(198, 205, 216)
                : android.graphics.Color.rgb(92, 103, 118);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        generation.incrementAndGet();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        repository = null;
        binding = null;
        super.onDestroy();
    }
}
