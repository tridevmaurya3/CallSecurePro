package com.tridev.callsecurepro.telecom;

import android.telecom.Call;
import android.telecom.VideoProfile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Process-local registry and explicit user-action gateway for active Telecom calls.
 *
 * Every state-changing method is intended to be called only from the visible in-call UI.
 */
public final class CallSessionManager {

    public interface Listener {
        void onCallSessionsChanged(@NonNull List<Call> activeCalls);
    }

    private static final CallSessionManager INSTANCE = new CallSessionManager();

    private final CopyOnWriteArrayList<Call> activeCalls = new CopyOnWriteArrayList<>();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(@NonNull Call call, int state) {
            notifyListeners();
        }

        @Override
        public void onDetailsChanged(@NonNull Call call, @NonNull Call.Details details) {
            notifyListeners();
        }

        @Override
        public void onParentChanged(@NonNull Call call, @Nullable Call parent) {
            notifyListeners();
        }

        @Override
        public void onChildrenChanged(
                @NonNull Call call,
                @NonNull List<Call> children
        ) {
            notifyListeners();
        }

        @Override
        public void onConferenceableCallsChanged(
                @NonNull Call call,
                @NonNull List<Call> conferenceableCalls
        ) {
            notifyListeners();
        }
    };

    private CallSessionManager() {
        // Singleton.
    }

    @NonNull
    public static CallSessionManager getInstance() {
        return INSTANCE;
    }

    public void registerCall(@NonNull Call call) {
        if (activeCalls.contains(call)) {
            return;
        }

        activeCalls.add(call);
        call.registerCallback(callCallback);
        notifyListeners();
    }

    public void unregisterCall(@NonNull Call call) {
        call.unregisterCallback(callCallback);
        activeCalls.remove(call);
        notifyListeners();
    }

    public void clear() {
        for (Call call : activeCalls) {
            call.unregisterCallback(callCallback);
        }
        activeCalls.clear();
        notifyListeners();
    }

    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
        listener.onCallSessionsChanged(getActiveCallsSnapshot());
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public List<Call> getActiveCallsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(activeCalls));
    }

    @Nullable
    public Call getPrimaryCall() {
        if (activeCalls.isEmpty()) {
            return null;
        }

        // Call waiting safety: a ringing call must take UI priority over an already-active call
        // so Answer/Reject always target the incoming call the user can currently see.
        Call ringing = findCallInState(Call.STATE_RINGING);
        if (ringing != null) {
            return ringing;
        }

        Call dialing = findCallInState(Call.STATE_DIALING);
        if (dialing != null) {
            return dialing;
        }

        Call connecting = findCallInState(Call.STATE_CONNECTING);
        if (connecting != null) {
            return connecting;
        }

        Call active = findCallInState(Call.STATE_ACTIVE);
        if (active != null) {
            return active;
        }

        Call holding = findCallInState(Call.STATE_HOLDING);
        if (holding != null) {
            return holding;
        }

        return activeCalls.get(0);
    }

    @Nullable
    public Call getSecondaryCall() {
        Call primary = getPrimaryCall();
        for (Call call : activeCalls) {
            if (call != primary && call.getState() != Call.STATE_DISCONNECTED) {
                return call;
            }
        }
        return null;
    }

    public boolean answerPrimaryCall() {
        Call call = getPrimaryCall();
        if (call == null || call.getState() != Call.STATE_RINGING) {
            return false;
        }
        call.answer(VideoProfile.STATE_AUDIO_ONLY);
        return true;
    }

    public boolean rejectPrimaryCall() {
        Call call = getPrimaryCall();
        if (call == null || call.getState() != Call.STATE_RINGING) {
            return false;
        }
        call.reject(false, null);
        return true;
    }

    public boolean disconnectPrimaryCall() {
        Call call = getPrimaryCall();
        if (call == null) {
            return false;
        }
        call.disconnect();
        return true;
    }

    public boolean setPrimaryCallHeld(boolean held) {
        Call call = getPrimaryCall();
        if (call == null || !canHold(call)) {
            return false;
        }

        if (held && call.getState() == Call.STATE_ACTIVE) {
            call.hold();
            return true;
        }

        if (!held && call.getState() == Call.STATE_HOLDING) {
            call.unhold();
            return true;
        }

        return false;
    }

    public boolean canPrimaryCallHold() {
        Call call = getPrimaryCall();
        return call != null && canHold(call);
    }

    public boolean canSwapCalls() {
        Call active = findCallInState(Call.STATE_ACTIVE);
        Call held = findCallInState(Call.STATE_HOLDING);
        return active != null && held != null && canHold(active) && canHold(held);
    }

    public boolean swapActiveAndHeldCalls() {
        Call active = findCallInState(Call.STATE_ACTIVE);
        Call held = findCallInState(Call.STATE_HOLDING);
        if (active == null || held == null || !canHold(active) || !canHold(held)) {
            return false;
        }

        try {
            // Telecom/carrier coordinates the complementary active->held transition when the
            // held call is resumed. This avoids racing two asynchronous state changes.
            held.unhold();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean canMergePrimaryCall() {
        Call call = getPrimaryCall();
        if (call == null) {
            return false;
        }

        Call.Details details = call.getDetails();
        if (details != null && details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
            return true;
        }

        List<Call> conferenceableCalls = call.getConferenceableCalls();
        return conferenceableCalls != null && !conferenceableCalls.isEmpty();
    }

    public boolean mergePrimaryCall() {
        Call call = getPrimaryCall();
        if (call == null) {
            return false;
        }

        try {
            Call.Details details = call.getDetails();
            if (details != null && details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                call.mergeConference();
                return true;
            }

            List<Call> conferenceableCalls = call.getConferenceableCalls();
            if (conferenceableCalls != null && !conferenceableCalls.isEmpty()) {
                call.conference(conferenceableCalls.get(0));
                return true;
            }
        } catch (RuntimeException ignored) {
            return false;
        }

        return false;
    }

    public boolean playPrimaryDtmfTone(char digit) {
        if (!isValidDtmfDigit(digit)) {
            return false;
        }

        Call call = getPrimaryCall();
        if (call == null || call.getState() != Call.STATE_ACTIVE) {
            return false;
        }

        try {
            call.playDtmfTone(digit);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void stopPrimaryDtmfTone() {
        Call call = getPrimaryCall();
        if (call != null) {
            try {
                call.stopDtmfTone();
            } catch (RuntimeException ignored) {
                // Call may have ended while the short tone was playing.
            }
        }
    }

    private boolean canHold(@NonNull Call call) {
        Call.Details details = call.getDetails();
        return details != null
                && (details.can(Call.Details.CAPABILITY_HOLD)
                || details.can(Call.Details.CAPABILITY_SUPPORT_HOLD));
    }

    @Nullable
    private Call findCallInState(int state) {
        for (Call call : activeCalls) {
            if (call.getState() == state) {
                return call;
            }
        }
        return null;
    }

    private boolean isValidDtmfDigit(char digit) {
        return (digit >= '0' && digit <= '9') || digit == '*' || digit == '#';
    }

    private void notifyListeners() {
        List<Call> snapshot = getActiveCallsSnapshot();
        for (Listener listener : listeners) {
            listener.onCallSessionsChanged(snapshot);
        }
    }
}
