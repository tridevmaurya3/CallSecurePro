package com.tridev.callsecurepro.ui.incall;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.view.View;
import android.view.WindowManager;
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
import com.tridev.callsecurepro.databinding.ActivityInCallBinding;
import com.tridev.callsecurepro.telecom.CallAudioController;
import com.tridev.callsecurepro.telecom.CallSessionManager;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User-facing Telecom call screen.
 *
 * Every state-changing call action is triggered only by an explicit user tap.
 */
public class InCallActivity extends AppCompatActivity
        implements CallSessionManager.Listener, CallAudioController.Listener {

    private ActivityInCallBinding binding;
    private final CallSessionManager callSessionManager = CallSessionManager.getInstance();
    private final CallAudioController callAudioController = CallAudioController.getInstance();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger identityGeneration = new AtomicInteger();

    private ExecutorService contactLookupExecutor;
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

        contactLookupExecutor = Executors.newSingleThreadExecutor();

        applySystemInsets();
        setupActions();

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
        updateTimer();
    }

    private void renderCallState(@NonNull Call call) {
        int state = call.getState();
        int stateText = getCallStateText(state);
        binding.callStateChip.setText(stateText);

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

        boolean holdSupported = callSessionManager.canPrimaryCallHold()
                && (state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING);
        binding.holdButton.setEnabled(holdSupported);
        binding.holdButton.setText(
                state == Call.STATE_HOLDING ? R.string.incall_resume : R.string.incall_hold
        );

        if (ended) {
            binding.utilityControls.setVisibility(View.GONE);
            binding.incomingActions.setVisibility(View.GONE);
            binding.endCallButton.setVisibility(View.GONE);
        }
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

        boolean contactsAllowed = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED;
        binding.identityNote.setVisibility(contactsAllowed ? View.GONE : View.VISIBLE);

        if (!contactsAllowed
                || number.isEmpty()
                || contactLookupExecutor == null
                || contactLookupExecutor.isShutdown()) {
            return;
        }

        int generation = identityGeneration.incrementAndGet();
        contactLookupExecutor.execute(() -> {
            String contactName = queryContactName(number);
            if (contactName == null || generation != identityGeneration.get()) {
                return;
            }

            runOnUiThread(() -> {
                if (binding != null && generation == identityGeneration.get()) {
                    applyCallerName(contactName);
                }
            });
        });
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

    @Nullable
    private String queryContactName(@NonNull String number) {
        Uri lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
        );

        try (Cursor cursor = getContentResolver().query(
                lookupUri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
            if (nameIndex < 0) {
                return null;
            }

            String name = cursor.getString(nameIndex);
            return name == null || name.trim().isEmpty() ? null : name.trim();
        } catch (SecurityException ignored) {
            return null;
        }
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

    private void renderAudioState(@Nullable CallAudioState state) {
        if (binding == null) {
            return;
        }

        boolean ready = state != null;
        binding.muteButton.setEnabled(ready);
        binding.speakerButton.setEnabled(ready);

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
        identityGeneration.incrementAndGet();

        callSessionManager.removeListener(this);
        callAudioController.removeListener(this);

        if (contactLookupExecutor != null) {
            contactLookupExecutor.shutdownNow();
            contactLookupExecutor = null;
        }

        binding = null;
        super.onDestroy();
    }
}
