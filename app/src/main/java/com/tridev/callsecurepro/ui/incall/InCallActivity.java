package com.tridev.callsecurepro.ui.incall;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tridev.callsecurepro.MainActivity;
import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ActivityInCallBinding;
import com.tridev.callsecurepro.identity.CallerIdentityRepository;
import com.tridev.callsecurepro.identity.CallerIdentityResult;
import com.tridev.callsecurepro.protection.CallerAssessment;
import com.tridev.callsecurepro.protection.CallerIntelligenceEngine;
import com.tridev.callsecurepro.protection.ProtectionRepository;
import com.tridev.callsecurepro.telecom.CallAudioController;
import com.tridev.callsecurepro.telecom.CallSessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User-facing Telecom call screen with capability-aware advanced call controls.
 *
 * All call-changing actions are driven by explicit user taps. Unsupported carrier/device
 * operations stay disabled instead of being simulated.
 */
public class InCallActivity extends AppCompatActivity
        implements CallSessionManager.Listener, CallAudioController.Listener {

    private ActivityInCallBinding binding;
    private final CallSessionManager callSessionManager = CallSessionManager.getInstance();
    private final CallAudioController callAudioController = CallAudioController.getInstance();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Handler dtmfHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger identityGeneration = new AtomicInteger();

    private ExecutorService identityExecutor;
    private CallerIdentityRepository identityRepository;
    private CallerIntelligenceEngine intelligenceEngine;

    @Nullable
    private Call primaryCall;
    private long fallbackActiveSince;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            timerHandler.postDelayed(this, 1000L);
        }
    };

    private final Runnable stopDtmfRunnable = callSessionManager::stopPrimaryDtmfTone;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        EdgeToEdge.enable(this);
        binding = ActivityInCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        identityExecutor = Executors.newSingleThreadExecutor();
        identityRepository = new CallerIdentityRepository(this);
        intelligenceEngine = new CallerIntelligenceEngine(this);

        applySystemInsets();
        setupActions();
        setupDtmfKeypad();

        callSessionManager.addListener(this);
        callAudioController.addListener(this);
        timerHandler.post(timerRunnable);
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.incallRoot, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );

            view.setPadding(
                    Math.max(view.getPaddingLeft(), systemBars.left),
                    Math.max(view.getPaddingTop(), systemBars.top),
                    Math.max(view.getPaddingRight(), systemBars.right),
                    Math.max(view.getPaddingBottom(), systemBars.bottom)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.incallRoot);
    }

    private void setupActions() {
        binding.answerButton.setOnClickListener(view -> callSessionManager.answerPrimaryCall());
        binding.rejectButton.setOnClickListener(view -> callSessionManager.rejectPrimaryCall());
        binding.endCallButton.setOnClickListener(view -> callSessionManager.disconnectPrimaryCall());

        binding.muteButton.setOnClickListener(view -> toggleMute());
        binding.speakerButton.setOnClickListener(view -> toggleSpeaker());
        binding.holdButton.setOnClickListener(view -> toggleHold());

        binding.keypadButton.setOnClickListener(view -> toggleDtmfKeypad());
        binding.audioRouteButton.setOnClickListener(view -> showAudioRoutePicker());
        binding.addCallButton.setOnClickListener(view -> openAddCallDialer());
        binding.mergeButton.setOnClickListener(view -> mergeCalls());
        binding.swapCallsButton.setOnClickListener(view -> swapCalls());
    }

    private void setupDtmfKeypad() {
        binding.dtmfKey1.setOnClickListener(view -> sendDtmf('1'));
        binding.dtmfKey2.setOnClickListener(view -> sendDtmf('2'));
        binding.dtmfKey3.setOnClickListener(view -> sendDtmf('3'));
        binding.dtmfKey4.setOnClickListener(view -> sendDtmf('4'));
        binding.dtmfKey5.setOnClickListener(view -> sendDtmf('5'));
        binding.dtmfKey6.setOnClickListener(view -> sendDtmf('6'));
        binding.dtmfKey7.setOnClickListener(view -> sendDtmf('7'));
        binding.dtmfKey8.setOnClickListener(view -> sendDtmf('8'));
        binding.dtmfKey9.setOnClickListener(view -> sendDtmf('9'));
        binding.dtmfKeyStar.setOnClickListener(view -> sendDtmf('*'));
        binding.dtmfKey0.setOnClickListener(view -> sendDtmf('0'));
        binding.dtmfKeyHash.setOnClickListener(view -> sendDtmf('#'));
    }

    @Override
    public void onCallSessionsChanged(@NonNull List<Call> activeCalls) {
        runOnUiThread(() -> renderCalls(activeCalls));
    }

    @Override
    public void onCallAudioStateChanged(@Nullable CallAudioState audioState) {
        runOnUiThread(() -> renderAudioState(audioState));
    }

    private void renderCalls(@NonNull List<Call> activeCalls) {
        if (binding == null) {
            return;
        }

        if (activeCalls.isEmpty()) {
            primaryCall = null;
            binding.callCountChip.setText(R.string.incall_no_active_call);
            finish();
            return;
        }

        binding.callCountChip.setText(
                activeCalls.size() == 1
                        ? getString(R.string.incall_single_call)
                        : getString(R.string.incall_multiple_calls_format, activeCalls.size())
        );

        Call newPrimaryCall = callSessionManager.getPrimaryCall();
        if (newPrimaryCall == null) {
            return;
        }

        if (primaryCall != newPrimaryCall) {
            primaryCall = newPrimaryCall;
            fallbackActiveSince = 0L;
            resolveCallerIdentity(newPrimaryCall);
        }

        renderCallState(newPrimaryCall);
        renderMultiCallState(activeCalls);
        updateTimer();
    }

    private void renderCallState(@NonNull Call call) {
        int state = call.getState();
        binding.callStateChip.setText(getCallStateText(state));

        boolean ringing = state == Call.STATE_RINGING;
        boolean ended = state == Call.STATE_DISCONNECTED;
        boolean interactiveCall = !ringing && !ended;

        binding.incomingActions.setVisibility(ringing ? View.VISIBLE : View.GONE);
        binding.endCallButton.setVisibility(interactiveCall ? View.VISIBLE : View.GONE);

        boolean utilityVisible = state == Call.STATE_DIALING
                || state == Call.STATE_CONNECTING
                || state == Call.STATE_ACTIVE
                || state == Call.STATE_HOLDING;
        binding.utilityControls.setVisibility(utilityVisible ? View.VISIBLE : View.GONE);
        binding.advancedControls.setVisibility(utilityVisible ? View.VISIBLE : View.GONE);

        boolean holdSupported = callSessionManager.canPrimaryCallHold()
                && (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING);
        binding.holdButton.setEnabled(holdSupported);
        binding.holdButton.setText(
                state == Call.STATE_HOLDING ? R.string.incall_resume : R.string.incall_hold
        );

        boolean dtmfAvailable = state == Call.STATE_ACTIVE;
        binding.keypadButton.setEnabled(dtmfAvailable);
        if (!dtmfAvailable && binding.dtmfCard.getVisibility() == View.VISIBLE) {
            hideDtmfKeypad();
        }

        binding.addCallButton.setEnabled(state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING);
        binding.mergeButton.setEnabled(callSessionManager.canMergePrimaryCall());

        if (ringing || ended) {
            binding.advancedControls.setVisibility(View.GONE);
            binding.dtmfCard.setVisibility(View.GONE);
            binding.keypadButton.setText(R.string.incall_keypad);
        }

        if (ended) {
            binding.utilityControls.setVisibility(View.GONE);
            binding.incomingActions.setVisibility(View.GONE);
            binding.endCallButton.setVisibility(View.GONE);
            binding.multiCallCard.setVisibility(View.GONE);
        }
    }

    private void renderMultiCallState(@NonNull List<Call> activeCalls) {
        Call secondary = callSessionManager.getSecondaryCall();
        boolean show = activeCalls.size() > 1 && secondary != null;
        binding.multiCallCard.setVisibility(show ? View.VISIBLE : View.GONE);

        if (!show || secondary == null) {
            return;
        }

        String number = extractPhoneNumber(secondary.getDetails());
        String state = getString(getCallStateText(secondary.getState()));
        binding.secondaryCallText.setText(
                getString(R.string.incall_second_call_format, number, state)
        );
        binding.swapCallsButton.setEnabled(callSessionManager.canSwapCalls());
    }

    private int getCallStateText(int state) {
        switch (state) {
            case Call.STATE_RINGING:
                return R.string.incall_incoming;
            case Call.STATE_DIALING:
                return R.string.incall_dialing;
            case Call.STATE_CONNECTING:
                return R.string.incall_connecting;
            case Call.STATE_ACTIVE:
                return R.string.incall_active;
            case Call.STATE_HOLDING:
                return R.string.incall_on_hold;
            case Call.STATE_DISCONNECTING:
                return R.string.incall_disconnecting;
            case Call.STATE_DISCONNECTED:
                return R.string.incall_disconnected;
            default:
                return R.string.incall_call_state;
        }
    }

    private void resolveCallerIdentity(@NonNull Call call) {
        Call.Details details = call.getDetails();
        String number = extractPhoneNumber(details);
        String telecomName = details == null ? null : details.getCallerDisplayName();

        binding.callerNumber.setText(number);
        if (telecomName != null && !telecomName.trim().isEmpty()) {
            applyCallerName(telecomName.trim());
        } else {
            applyCallerName(getString(R.string.incall_unknown_caller));
        }

        ExecutorService executor = identityExecutor;
        CallerIdentityRepository repository = identityRepository;
        CallerIntelligenceEngine fallbackEngine = intelligenceEngine;
        if (executor == null || executor.isShutdown() || fallbackEngine == null) {
            return;
        }

        int generation = identityGeneration.incrementAndGet();
        executor.execute(() -> {
            CallerIdentityResult result = null;
            CallerAssessment assessment;

            try {
                if (repository != null && !ProtectionRepository.normalize(number).isEmpty()) {
                    result = repository.resolveCaller(number);
                    assessment = result.getAssessment();
                } else {
                    assessment = fallbackEngine.assess(number);
                }
            } catch (RuntimeException ignored) {
                assessment = fallbackEngine.assess(number);
            }

            CallerIdentityResult resolvedResult = result;
            CallerAssessment resolvedAssessment = assessment;
            if (generation != identityGeneration.get()) {
                return;
            }

            runOnUiThread(() -> {
                if (binding == null || generation != identityGeneration.get()) {
                    return;
                }

                if (resolvedResult != null && resolvedResult.hasResolvedName()) {
                    applyCallerName(resolvedResult.getDisplayName());
                    binding.identityNote.setVisibility(View.VISIBLE);
                    binding.identityNote.setText(
                            getString(R.string.lookup_source_format, resolvedResult.getSource())
                    );
                } else {
                    binding.identityNote.setVisibility(View.GONE);
                }

                renderCallerAssessment(resolvedAssessment);
            });
        });
    }

    private void renderCallerAssessment(@NonNull CallerAssessment assessment) {
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

        binding.callerRiskChip.setText(labelRes);
        binding.callerRiskChip.setTextColor(foreground);
        binding.callerRiskChip.setChipBackgroundColor(ColorStateList.valueOf(background));
        binding.callerRiskReason.setText(assessment.getReason());
    }

    @NonNull
    private String extractPhoneNumber(@Nullable Call.Details details) {
        if (details == null) {
            return getString(R.string.incall_unknown_caller);
        }

        Uri handle = details.getHandle();
        if (handle == null || handle.getSchemeSpecificPart() == null) {
            return getString(R.string.incall_private_number);
        }

        String number = handle.getSchemeSpecificPart().trim();
        return number.isEmpty() ? getString(R.string.incall_private_number) : number;
    }

    private void applyCallerName(@NonNull String name) {
        binding.callerName.setText(name);
        String trimmed = name.trim();
        String initial = trimmed.isEmpty()
                ? "#"
                : trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
        binding.callerAvatarInitial.setText(initial);
    }

    private void updateTimer() {
        if (binding == null || primaryCall == null) {
            return;
        }

        int state = primaryCall.getState();
        if (state != Call.STATE_ACTIVE && state != Call.STATE_HOLDING) {
            binding.callTimer.setText(R.string.incall_timer_zero);
            return;
        }

        long connectedAt = primaryCall.getDetails() == null
                ? 0L
                : primaryCall.getDetails().getConnectTimeMillis();

        if (connectedAt <= 0L) {
            if (fallbackActiveSince <= 0L) {
                fallbackActiveSince = System.currentTimeMillis();
            }
            connectedAt = fallbackActiveSince;
        }

        long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - connectedAt) / 1000L);
        long hours = elapsedSeconds / 3600L;
        long minutes = (elapsedSeconds % 3600L) / 60L;
        long seconds = elapsedSeconds % 60L;

        String formatted = hours > 0
                ? String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        binding.callTimer.setText(formatted);
    }

    private void toggleMute() {
        CallAudioState state = callAudioController.getLatestAudioState();
        if (state == null || !callAudioController.setMuted(!state.isMuted())) {
            Toast.makeText(this, R.string.incall_audio_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleSpeaker() {
        CallAudioState state = callAudioController.getLatestAudioState();
        if (state == null) {
            Toast.makeText(this, R.string.incall_audio_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean speakerOn = state.getRoute() == CallAudioState.ROUTE_SPEAKER;
        if (!callAudioController.setSpeakerEnabled(!speakerOn)) {
            Toast.makeText(this, R.string.incall_audio_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleHold() {
        Call call = primaryCall;
        if (call == null || !callSessionManager.canPrimaryCallHold()) {
            Toast.makeText(this, R.string.incall_hold_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean held = call.getState() == Call.STATE_HOLDING;
        if (!callSessionManager.setPrimaryCallHeld(!held)) {
            Toast.makeText(this, R.string.incall_hold_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleDtmfKeypad() {
        if (primaryCall == null || primaryCall.getState() != Call.STATE_ACTIVE) {
            Toast.makeText(this, R.string.incall_dtmf_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        if (binding.dtmfCard.getVisibility() == View.VISIBLE) {
            hideDtmfKeypad();
        } else {
            binding.dtmfCard.setVisibility(View.VISIBLE);
            binding.keypadButton.setText(R.string.incall_hide_keypad);
        }
    }

    private void hideDtmfKeypad() {
        dtmfHandler.removeCallbacks(stopDtmfRunnable);
        callSessionManager.stopPrimaryDtmfTone();
        binding.dtmfCard.setVisibility(View.GONE);
        binding.keypadButton.setText(R.string.incall_keypad);
    }

    private void sendDtmf(char digit) {
        dtmfHandler.removeCallbacks(stopDtmfRunnable);
        if (!callSessionManager.playPrimaryDtmfTone(digit)) {
            Toast.makeText(this, R.string.incall_dtmf_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        dtmfHandler.postDelayed(stopDtmfRunnable, 160L);
    }

    private void showAudioRoutePicker() {
        CallAudioState state = callAudioController.getLatestAudioState();
        if (state == null) {
            Toast.makeText(this, R.string.incall_audio_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        List<Integer> routes = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int checkedItem = -1;

        checkedItem = addAudioRouteIfSupported(
                state,
                CallAudioState.ROUTE_EARPIECE,
                R.string.incall_route_earpiece,
                routes,
                labels,
                checkedItem
        );
        checkedItem = addAudioRouteIfSupported(
                state,
                CallAudioState.ROUTE_WIRED_HEADSET,
                R.string.incall_route_wired,
                routes,
                labels,
                checkedItem
        );
        checkedItem = addAudioRouteIfSupported(
                state,
                CallAudioState.ROUTE_BLUETOOTH,
                R.string.incall_route_bluetooth,
                routes,
                labels,
                checkedItem
        );
        checkedItem = addAudioRouteIfSupported(
                state,
                CallAudioState.ROUTE_SPEAKER,
                R.string.incall_route_speaker,
                routes,
                labels,
                checkedItem
        );

        if (routes.isEmpty()) {
            Toast.makeText(this, R.string.incall_audio_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] routeLabels = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.incall_audio_route_title)
                .setSingleChoiceItems(routeLabels, checkedItem, (dialog, which) -> {
                    if (which >= 0 && which < routes.size()) {
                        if (!callAudioController.setAudioRoute(routes.get(which))) {
                            Toast.makeText(
                                    this,
                                    R.string.incall_audio_unavailable,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private int addAudioRouteIfSupported(
            @NonNull CallAudioState state,
            int route,
            int labelRes,
            @NonNull List<Integer> routes,
            @NonNull List<String> labels,
            int checkedItem
    ) {
        if ((state.getSupportedRouteMask() & route) == 0) {
            return checkedItem;
        }

        if (state.getRoute() == route) {
            checkedItem = routes.size();
        }
        routes.add(route);
        labels.add(getString(labelRes));
        return checkedItem;
    }

    private void openAddCallDialer() {
        Call call = primaryCall;
        if (call == null
                || (call.getState() != Call.STATE_ACTIVE && call.getState() != Call.STATE_HOLDING)) {
            Toast.makeText(this, R.string.incall_add_call_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_DIAL)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void mergeCalls() {
        if (!callSessionManager.mergePrimaryCall()) {
            Toast.makeText(this, R.string.incall_merge_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void swapCalls() {
        if (!callSessionManager.swapActiveAndHeldCalls()) {
            Toast.makeText(this, R.string.incall_swap_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void renderAudioState(@Nullable CallAudioState state) {
        if (binding == null) {
            return;
        }

        boolean ready = state != null;
        binding.muteButton.setEnabled(ready);
        binding.speakerButton.setEnabled(ready);
        binding.audioRouteButton.setEnabled(ready && state.getSupportedRouteMask() != 0);

        boolean muted = ready && state.isMuted();
        boolean speakerOn = ready && state.getRoute() == CallAudioState.ROUTE_SPEAKER;

        binding.muteButton.setText(muted ? R.string.incall_unmute : R.string.incall_mute);
        binding.speakerButton.setText(
                speakerOn ? R.string.incall_speaker_off : R.string.incall_speaker
        );

        applyToggleAppearance(binding.muteButton, muted);
        applyToggleAppearance(binding.speakerButton, speakerOn);
    }

    private void applyToggleAppearance(
            @NonNull com.google.android.material.button.MaterialButton button,
            boolean active
    ) {
        int background = ContextCompat.getColor(
                this,
                active ? R.color.csp_primary_container : R.color.csp_surface
        );
        int foreground = ContextCompat.getColor(
                this,
                active ? R.color.csp_primary : R.color.csp_text_primary
        );

        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(foreground);
        button.setIconTint(ColorStateList.valueOf(foreground));
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        dtmfHandler.removeCallbacks(stopDtmfRunnable);
        callSessionManager.stopPrimaryDtmfTone();
        identityGeneration.incrementAndGet();

        callSessionManager.removeListener(this);
        callAudioController.removeListener(this);

        if (identityExecutor != null) {
            identityExecutor.shutdownNow();
            identityExecutor = null;
        }

        identityRepository = null;
        intelligenceEngine = null;
        binding = null;
        super.onDestroy();
    }
}
