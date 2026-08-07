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

        for (Call call : activeCalls) {
            int state = call.getState();
            if (state == Call.STATE_RINGING
                    || state == Call.STATE_DIALING
                    || state == Call.STATE_CONNECTING
                    || state == Call.STATE_ACTIVE) {
                return call;
            }
        }

        for (Call call : activeCalls) {
            if (call.getState() == Call.STATE_HOLDING) {
                return call;
            }
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

        active.hold();
        held.unhold();
        return true;
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

        call.playDtmfTone(digit);
        return true;
    }

    public void stopPrimaryDtmfTone() {
        Call call = getPrimaryCall();
        if (call != null) {
            call.stopDtmfTone();
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
