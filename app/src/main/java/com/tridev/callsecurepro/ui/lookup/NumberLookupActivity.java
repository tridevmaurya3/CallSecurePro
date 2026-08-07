package com.tridev.callsecurepro.ui.lookup;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
import com.tridev.callsecurepro.data.identity.LookupHistoryEntity;
import com.tridev.callsecurepro.databinding.ActivityNumberLookupBinding;
import com.tridev.callsecurepro.identity.CallerIdentityRepository;
import com.tridev.callsecurepro.identity.CallerIdentityResult;
import com.tridev.callsecurepro.identity.NumberIntelligenceAnalyzer;
import com.tridev.callsecurepro.protection.CallerAssessment;
import com.tridev.callsecurepro.protection.ProtectionRepository;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NumberLookupActivity extends AppCompatActivity {

    public static final String EXTRA_NUMBER = "com.tridev.callsecurepro.extra.LOOKUP_NUMBER";

    private ActivityNumberLookupBinding binding;
    private CallerIdentityRepository identityRepository;
    private NumberIntelligenceAnalyzer numberIntelligenceAnalyzer;
    private LookupHistoryAdapter historyAdapter;
    private ExecutorService lookupExecutor;
    private final AtomicInteger operationGeneration = new AtomicInteger();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        binding = ActivityNumberLookupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        identityRepository = new CallerIdentityRepository(this);
        numberIntelligenceAnalyzer = new NumberIntelligenceAnalyzer();
        lookupExecutor = Executors.newSingleThreadExecutor();
        historyAdapter = new LookupHistoryAdapter(this::selectHistoryItem);

        applySystemInsets();
        setupHistory();
        setupActions();
        loadHistory();
        consumeInitialNumber(getIntent());
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.lookupRoot, (view, windowInsets) -> {
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
        ViewCompat.requestApplyInsets(binding.lookupRoot);
    }

    private void setupHistory() {
        binding.historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.historyRecyclerView.setAdapter(historyAdapter);
        binding.historyRecyclerView.setHasFixedSize(false);
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.lookupButton.setOnClickListener(view -> lookupCurrentInput());
        binding.clearHistoryButton.setOnClickListener(view -> clearHistory());

        binding.numberInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                lookupCurrentInput();
                return true;
            }
            return false;
        });
    }

    private void consumeInitialNumber(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String number = intent.getStringExtra(EXTRA_NUMBER);
        if (number == null || number.trim().isEmpty()) {
            return;
        }
        binding.numberInput.setText(number.trim());
        binding.numberInput.setSelection(binding.numberInput.length());
        performLookup(number.trim());
    }

    private void lookupCurrentInput() {
        CharSequence text = binding.numberInput.getText();
        String number = text == null ? "" : text.toString().trim();
        if (number.isEmpty()) {
            binding.numberInputLayout.setError(getString(R.string.lookup_number_required));
            binding.numberInput.requestFocus();
            return;
        }
        if (ProtectionRepository.normalize(number).isEmpty()) {
            binding.numberInputLayout.setError(getString(R.string.lookup_invalid_number));
            binding.numberInput.requestFocus();
            return;
        }
        binding.numberInputLayout.setError(null);
        performLookup(number);
    }

    private void performLookup(@NonNull String number) {
        ExecutorService executor = lookupExecutor;
        NumberIntelligenceAnalyzer analyzer = numberIntelligenceAnalyzer;
        if (executor == null || executor.isShutdown() || analyzer == null) {
            return;
        }

        int generation = operationGeneration.incrementAndGet();
        setLoading(true);

        executor.execute(() -> {
            try {
                CallerIdentityResult result = identityRepository.lookup(number);
                NumberIntelligenceAnalyzer.Result numberInfo = analyzer.analyze(number);
                List<LookupHistoryEntity> history = identityRepository.getRecentHistory(20);

                runOnUiThread(() -> {
                    if (binding == null || generation != operationGeneration.get()) {
                        return;
                    }
                    setLoading(false);
                    renderResult(result, numberInfo);
                    renderHistory(history);
                });
            } catch (RuntimeException exception) {
                runOnUiThread(() -> {
                    if (binding == null || generation != operationGeneration.get()) {
                        return;
                    }
                    setLoading(false);
                    Toast.makeText(this, R.string.lookup_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void renderResult(
            @NonNull CallerIdentityResult result,
            @NonNull NumberIntelligenceAnalyzer.Result numberInfo
    ) {
        binding.resultCard.setVisibility(View.VISIBLE);

        String displayName = result.hasResolvedName()
                ? result.getDisplayName()
                : getString(R.string.lookup_unknown_name);
        binding.resultName.setText(displayName);
        binding.resultNumber.setText(result.getDisplayNumber());

        String initialSource = result.hasResolvedName()
                ? result.getDisplayName()
                : result.getDisplayNumber();
        String initial = initialSource == null || initialSource.trim().isEmpty()
                ? "#"
                : initialSource.trim().substring(0, 1).toUpperCase(Locale.getDefault());
        binding.resultInitial.setText(initial);

        binding.identityTypeChip.setText(getIdentityTypeText(result.getIdentityType()));
        binding.verificationChip.setText(getVerificationText(result.getVerificationLevel()));
        styleVerificationChip(result.getVerificationLevel());

        binding.sourceText.setText(getString(R.string.lookup_source_format, result.getSource()));
        binding.confidenceText.setText(
                getString(R.string.lookup_confidence_format, result.getConfidence())
        );

        String category = result.getCategory();
        if (category == null || category.trim().isEmpty()) {
            binding.categoryText.setVisibility(View.GONE);
        } else {
            binding.categoryText.setVisibility(View.VISIBLE);
            binding.categoryText.setText(getString(R.string.lookup_category_format, category));
        }

        renderNumberIntelligence(numberInfo);

        CallerAssessment assessment = result.getAssessment();
        binding.riskChip.setText(getRiskText(assessment.getLevel()));
        binding.riskScoreText.setText(
                getString(R.string.lookup_risk_score_format, assessment.getRiskScore())
        );
        binding.riskReasonText.setText(assessment.getReason());
        styleRiskChip(assessment.getLevel());
    }

    private void renderNumberIntelligence(@NonNull NumberIntelligenceAnalyzer.Result info) {
        binding.numberValidityChip.setText(getValidityText(info.getValidity()));
        styleValidityChip(info.getValidity());

        binding.numberTypeText.setText(
                getString(R.string.lookup_number_type_format, getString(getNumberTypeText(info.getNumberType())))
        );

        if (info.isParsed()
                && info.getCountryCallingCode() > 0
                && !"ZZ".equalsIgnoreCase(info.getRegionCode())) {
            String regionName = info.getRegionDisplayName().trim().isEmpty()
                    ? info.getRegionCode()
                    : info.getRegionDisplayName();
            binding.numberRegionText.setText(
                    getString(
                            R.string.lookup_number_region_format,
                            getString(
                                    R.string.lookup_number_region_code_format,
                                    regionName,
                                    info.getRegionCode(),
                                    info.getCountryCallingCode()
                            )
                    )
            );
        } else {
            binding.numberRegionText.setText(R.string.lookup_number_region_unknown);
        }

        setOptionalText(
                binding.numberInternationalText,
                info.getInternationalFormat(),
                R.string.lookup_number_international_format
        );
        setOptionalText(
                binding.numberNationalText,
                info.getNationalFormat(),
                R.string.lookup_number_national_format
        );
        setOptionalText(
                binding.numberE164Text,
                info.getE164Format(),
                R.string.lookup_number_e164_format
        );
    }

    private void setOptionalText(
            @NonNull android.widget.TextView view,
            @Nullable String value,
            int formatRes
    ) {
        String safeValue = value == null ? "" : value.trim();
        if (safeValue.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(getString(formatRes, safeValue));
    }

    private int getValidityText(@NonNull NumberIntelligenceAnalyzer.Validity validity) {
        switch (validity) {
            case VALID:
                return R.string.lookup_number_valid;
            case POSSIBLE:
                return R.string.lookup_number_possible;
            case INVALID:
            default:
                return R.string.lookup_number_invalid_plan;
        }
    }

    private int getNumberTypeText(@NonNull NumberIntelligenceAnalyzer.NumberType type) {
        switch (type) {
            case MOBILE:
                return R.string.lookup_number_type_mobile;
            case FIXED_LINE:
                return R.string.lookup_number_type_fixed;
            case FIXED_OR_MOBILE:
                return R.string.lookup_number_type_fixed_mobile;
            case TOLL_FREE:
                return R.string.lookup_number_type_toll_free;
            case PREMIUM_RATE:
                return R.string.lookup_number_type_premium;
            case SHARED_COST:
                return R.string.lookup_number_type_shared;
            case VOIP:
                return R.string.lookup_number_type_voip;
            case PERSONAL:
                return R.string.lookup_number_type_personal;
            case PAGER:
                return R.string.lookup_number_type_pager;
            case UAN:
                return R.string.lookup_number_type_uan;
            case VOICEMAIL:
                return R.string.lookup_number_type_voicemail;
            case UNKNOWN:
            default:
                return R.string.lookup_number_type_unknown;
        }
    }

    private void styleValidityChip(@NonNull NumberIntelligenceAnalyzer.Validity validity) {
        int foreground;
        int background;
        switch (validity) {
            case VALID:
                foreground = ContextCompat.getColor(this, R.color.csp_safe);
                background = ContextCompat.getColor(this, R.color.csp_safe_container);
                break;
            case POSSIBLE:
                foreground = ContextCompat.getColor(this, R.color.csp_unknown);
                background = ContextCompat.getColor(this, R.color.csp_unknown_container);
                break;
            case INVALID:
            default:
                foreground = ContextCompat.getColor(this, R.color.csp_spam);
                background = ContextCompat.getColor(this, R.color.csp_spam_container);
                break;
        }
        binding.numberValidityChip.setTextColor(foreground);
        binding.numberValidityChip.setChipBackgroundColor(ColorStateList.valueOf(background));
    }

    private int getIdentityTypeText(@NonNull CallerIdentityResult.IdentityType type) {
        switch (type) {
            case CONTACT:
                return R.string.lookup_identity_contact;
            case PERSON:
                return R.string.lookup_identity_person;
            case BUSINESS:
                return R.string.lookup_identity_business;
            case UNKNOWN:
            default:
                return R.string.lookup_identity_unknown;
        }
    }

    private int getVerificationText(
            @NonNull CallerIdentityResult.VerificationLevel verificationLevel
    ) {
        switch (verificationLevel) {
            case LOCAL_MATCH:
                return R.string.lookup_verification_local;
            case VERIFIED:
                return R.string.lookup_verification_verified;
            case UNVERIFIED:
            default:
                return R.string.lookup_verification_unverified;
        }
    }

    private int getRiskText(@NonNull CallerAssessment.Level level) {
        switch (level) {
            case SAFE:
                return R.string.lookup_risk_safe;
            case SUSPICIOUS:
                return R.string.lookup_risk_suspicious;
            case SPAM:
                return R.string.lookup_risk_spam;
            case UNKNOWN:
            default:
                return R.string.lookup_risk_unknown;
        }
    }

    private void styleVerificationChip(
            @NonNull CallerIdentityResult.VerificationLevel verificationLevel
    ) {
        int foreground;
        int background;
        if (verificationLevel == CallerIdentityResult.VerificationLevel.VERIFIED) {
            foreground = ContextCompat.getColor(this, R.color.csp_verified);
            background = ContextCompat.getColor(this, R.color.csp_verified_container);
        } else if (verificationLevel == CallerIdentityResult.VerificationLevel.LOCAL_MATCH) {
            foreground = ContextCompat.getColor(this, R.color.csp_safe);
            background = ContextCompat.getColor(this, R.color.csp_safe_container);
        } else {
            foreground = ContextCompat.getColor(this, R.color.csp_text_secondary);
            background = ContextCompat.getColor(this, R.color.csp_surface_variant);
        }
        binding.verificationChip.setTextColor(foreground);
        binding.verificationChip.setChipBackgroundColor(ColorStateList.valueOf(background));
    }

    private void styleRiskChip(@NonNull CallerAssessment.Level level) {
        int foreground;
        int background;
        switch (level) {
            case SAFE:
                foreground = ContextCompat.getColor(this, R.color.csp_safe);
                background = ContextCompat.getColor(this, R.color.csp_safe_container);
                break;
            case SUSPICIOUS:
                foreground = ContextCompat.getColor(this, R.color.csp_unknown);
                background = ContextCompat.getColor(this, R.color.csp_unknown_container);
                break;
            case SPAM:
                foreground = ContextCompat.getColor(this, R.color.csp_spam);
                background = ContextCompat.getColor(this, R.color.csp_spam_container);
                break;
            case UNKNOWN:
            default:
                foreground = ContextCompat.getColor(this, R.color.csp_business);
                background = ContextCompat.getColor(this, R.color.csp_business_container);
                break;
        }
        binding.riskChip.setTextColor(foreground);
        binding.riskChip.setChipBackgroundColor(ColorStateList.valueOf(background));
    }

    private void selectHistoryItem(@NonNull LookupHistoryEntity item) {
        binding.numberInput.setText(item.queryNumber);
        binding.numberInput.setSelection(binding.numberInput.length());
        binding.numberInputLayout.setError(null);
        performLookup(item.queryNumber);
    }

    private void loadHistory() {
        ExecutorService executor = lookupExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }
        executor.execute(() -> {
            List<LookupHistoryEntity> history = identityRepository.getRecentHistory(20);
            runOnUiThread(() -> {
                if (binding != null) {
                    renderHistory(history);
                }
            });
        });
    }

    private void clearHistory() {
        ExecutorService executor = lookupExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }
        executor.execute(() -> {
            identityRepository.clearLookupHistory();
            runOnUiThread(() -> {
                if (binding != null) {
                    renderHistory(java.util.Collections.emptyList());
                    Toast.makeText(this, R.string.lookup_history_cleared, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void renderHistory(@NonNull List<LookupHistoryEntity> history) {
        historyAdapter.submitList(history);
        binding.historyEmptyText.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
        binding.historyRecyclerView.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
        binding.clearHistoryButton.setEnabled(!history.isEmpty());
    }

    private void setLoading(boolean loading) {
        binding.loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.lookupButton.setEnabled(!loading);
        binding.numberInput.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        operationGeneration.incrementAndGet();
        if (lookupExecutor != null) {
            lookupExecutor.shutdownNow();
            lookupExecutor = null;
        }
        numberIntelligenceAnalyzer = null;
        binding = null;
        super.onDestroy();
    }
}
