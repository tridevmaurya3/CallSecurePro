package com.tridev.callsecurepro.ui.community;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.community.CommunityReportRepository;
import com.tridev.callsecurepro.data.community.CommunityReportEntity;
import com.tridev.callsecurepro.databinding.ActivityCommunityNetworkBinding;
import com.tridev.callsecurepro.protection.ProtectionRepository;
import com.tridev.callsecurepro.theme.AppVisualThemeManager;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CommunityNetworkActivity extends AppCompatActivity {

    public static final String EXTRA_NUMBER =
            "com.tridev.callsecurepro.extra.COMMUNITY_NUMBER";

    private ActivityCommunityNetworkBinding binding;
    private CommunityReportRepository repository;
    private ExecutorService executor;
    private final AtomicInteger generation = new AtomicInteger();

    @Nullable
    private CommunityReportRepository.Category selectedCategory;

    private final List<CommunityReportRepository.Category> categoryValues = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCommunityNetworkBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new CommunityReportRepository(this);
        executor = Executors.newSingleThreadExecutor();

        applyInsets();
        setupCategories();
        setupActions();
        consumeInitialNumber();
        refreshSnapshot();
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.communityRoot, (view, insets) -> {
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
        ViewCompat.requestApplyInsets(binding.communityRoot);
    }

    private void setupCategories() {
        categoryValues.clear();
        categoryValues.add(CommunityReportRepository.Category.SPAM);
        categoryValues.add(CommunityReportRepository.Category.SCAM_FRAUD);
        categoryValues.add(CommunityReportRepository.Category.TELEMARKETING);
        categoryValues.add(CommunityReportRepository.Category.BUSINESS);
        categoryValues.add(CommunityReportRepository.Category.SAFE_LEGITIMATE);
        categoryValues.add(CommunityReportRepository.Category.OTHER);

        List<String> labels = new ArrayList<>();
        for (CommunityReportRepository.Category category : categoryValues) {
            labels.add(getCategoryLabel(category));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                labels
        );
        binding.categoryDropdown.setAdapter(adapter);
        binding.categoryDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < categoryValues.size()) {
                selectedCategory = categoryValues.get(position);
                binding.categoryInputLayout.setError(null);
            }
        });
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.submitButton.setOnClickListener(view -> validateAndConfirmReport());
        binding.syncButton.setOnClickListener(view -> requestSync());
    }

    private void consumeInitialNumber() {
        String number = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_NUMBER);
        if (number == null || number.trim().isEmpty()) {
            return;
        }
        binding.numberInput.setText(number.trim());
        binding.numberInput.setSelection(binding.numberInput.length());
    }

    private void validateAndConfirmReport() {
        CharSequence rawText = binding.numberInput.getText();
        String rawNumber = rawText == null ? "" : rawText.toString().trim();
        if (ProtectionRepository.normalize(rawNumber).isEmpty()) {
            binding.numberInputLayout.setError(getString(R.string.community_number_required));
            binding.numberInput.requestFocus();
            return;
        }
        binding.numberInputLayout.setError(null);

        CommunityReportRepository.Category category = selectedCategory;
        if (category == null) {
            binding.categoryInputLayout.setError(getString(R.string.community_category_required));
            binding.categoryDropdown.requestFocus();
            return;
        }
        binding.categoryInputLayout.setError(null);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.community_confirm_title)
                .setMessage(R.string.community_confirm_body)
                .setPositiveButton(R.string.community_submit, (dialog, which) ->
                        saveReport(rawNumber, category)
                )
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveReport(
            @NonNull String number,
            @NonNull CommunityReportRepository.Category category
    ) {
        ExecutorService currentExecutor = executor;
        CommunityReportRepository currentRepository = repository;
        if (currentExecutor == null || currentExecutor.isShutdown() || currentRepository == null) {
            return;
        }

        binding.submitButton.setEnabled(false);
        currentExecutor.execute(() -> {
            CommunityReportRepository.SubmitResult result = currentRepository.submit(number, category);
            CommunityReportRepository.Snapshot snapshot = currentRepository.getSnapshot();
            runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }
                binding.submitButton.setEnabled(true);
                renderSnapshot(snapshot);
                if (result == CommunityReportRepository.SubmitResult.SAVED_PENDING) {
                    Toast.makeText(
                            this,
                            R.string.community_saved_pending,
                            Toast.LENGTH_SHORT
                    ).show();
                } else if (result == CommunityReportRepository.SubmitResult.DUPLICATE_RECENT) {
                    Toast.makeText(
                            this,
                            R.string.community_duplicate,
                            Toast.LENGTH_LONG
                    ).show();
                } else {
                    binding.numberInputLayout.setError(
                            getString(R.string.community_number_required)
                    );
                }
            });
        });
    }

    private void requestSync() {
        CommunityReportRepository currentRepository = repository;
        if (currentRepository == null) {
            return;
        }
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isShutdown()) {
            return;
        }
        currentExecutor.execute(() -> {
            CommunityReportRepository.Snapshot snapshot = currentRepository.getSnapshot();
            if (snapshot.cloudAvailable) {
                currentRepository.enqueueSyncIfAvailable();
            }
            runOnUiThread(() -> Toast.makeText(
                    this,
                    snapshot.cloudAvailable
                            ? R.string.community_sync_requested
                            : R.string.community_sync_unavailable,
                    Toast.LENGTH_SHORT
            ).show());
        });
    }

    private void refreshSnapshot() {
        ExecutorService currentExecutor = executor;
        CommunityReportRepository currentRepository = repository;
        if (binding == null
                || currentExecutor == null
                || currentExecutor.isShutdown()
                || currentRepository == null) {
            return;
        }
        int operation = generation.incrementAndGet();
        currentExecutor.execute(() -> {
            CommunityReportRepository.Snapshot snapshot = currentRepository.getSnapshot();
            runOnUiThread(() -> {
                if (binding == null || operation != generation.get()) {
                    return;
                }
                renderSnapshot(snapshot);
            });
        });
    }

    private void renderSnapshot(@NonNull CommunityReportRepository.Snapshot snapshot) {
        binding.cloudStatusText.setText(
                snapshot.cloudAvailable
                        ? R.string.community_cloud_ready
                        : R.string.community_cloud_unavailable
        );
        binding.cloudStatusBody.setText(
                snapshot.cloudAvailable
                        ? snapshot.cloudStatus
                        : getString(R.string.community_cloud_unavailable_body)
        );
        binding.syncButton.setEnabled(snapshot.cloudAvailable && snapshot.pendingCount > 0);
        binding.pendingCount.setText(String.valueOf(snapshot.pendingCount));
        binding.sentCount.setText(String.valueOf(snapshot.sentCount));
        renderRecentReports(snapshot.recentReports);
    }

    private void renderRecentReports(@NonNull List<CommunityReportEntity> reports) {
        binding.recentReportsContainer.removeAllViews();
        binding.recentEmptyText.setVisibility(reports.isEmpty() ? View.VISIBLE : View.GONE);
        if (reports.isEmpty()) {
            return;
        }

        int primary = AppVisualThemeManager.isDarkBackground(this)
                ? android.graphics.Color.rgb(245, 247, 250)
                : android.graphics.Color.rgb(25, 30, 38);
        int secondary = AppVisualThemeManager.isDarkBackground(this)
                ? android.graphics.Color.rgb(198, 205, 216)
                : android.graphics.Color.rgb(92, 103, 118);
        DateFormat dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
        );

        for (CommunityReportEntity report : reports) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            TextView title = new TextView(this);
            title.setText(getString(
                    R.string.community_report_status,
                    report.displayNumber,
                    categoryLabelFromStored(report.category)
            ));
            title.setTextSize(14f);
            title.setTextColor(primary);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(title);

            TextView meta = new TextView(this);
            String status = CommunityReportRepository.STATUS_SENT.equals(report.status)
                    ? getString(R.string.community_status_sent)
                    : getString(R.string.community_status_pending);
            meta.setText(getString(
                    R.string.community_report_meta,
                    status,
                    dateFormat.format(new Date(report.createdAt))
            ));
            meta.setTextSize(11f);
            meta.setTextColor(secondary);
            meta.setPadding(0, dp(2), 0, 0);
            row.addView(meta);

            binding.recentReportsContainer.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(android.graphics.Color.argb(35, 120, 130, 145));
            binding.recentReportsContainer.addView(
                    divider,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(1)
                    )
            );
        }
    }

    @NonNull
    private String categoryLabelFromStored(@NonNull String value) {
        try {
            return getCategoryLabel(CommunityReportRepository.Category.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return getString(R.string.community_category_other);
        }
    }

    @NonNull
    private String getCategoryLabel(@NonNull CommunityReportRepository.Category category) {
        switch (category) {
            case SPAM:
                return getString(R.string.community_category_spam);
            case SCAM_FRAUD:
                return getString(R.string.community_category_scam);
            case TELEMARKETING:
                return getString(R.string.community_category_telemarketing);
            case BUSINESS:
                return getString(R.string.community_category_business);
            case SAFE_LEGITIMATE:
                return getString(R.string.community_category_safe);
            case OTHER:
            default:
                return getString(R.string.community_category_other);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && repository != null) {
            refreshSnapshot();
        }
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
