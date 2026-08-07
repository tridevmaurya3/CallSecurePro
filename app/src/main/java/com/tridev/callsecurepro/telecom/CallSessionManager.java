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
 * Process-local registry and user-action gateway for active Telecom calls.
 *
 * All state-changing methods are called only from explicit in-call UI actions.
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
        if (call == null) {
            return false;
        }

        Call.Details details = call.getDetails();
        boolean canHold = details != null
                && (details.can(Call.Details.CAPABILITY_HOLD)
                || details.can(Call.Details.CAPABILITY_SUPPORT_HOLD));
        if (!canHold) {
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
        if (call == null || call.getDetails() == null) {
            return false;
        }
        Call.Details details = call.getDetails();
        return details.can(Call.Details.CAPABILITY_HOLD)
                || details.can(Call.Details.CAPABILITY_SUPPORT_HOLD);
    }

    private void notifyListeners() {
        List<Call> snapshot = getActiveCallsSnapshot();
        for (Listener listener : listeners) {
            listener.onCallSessionsChanged(snapshot);
        }
    }
}
