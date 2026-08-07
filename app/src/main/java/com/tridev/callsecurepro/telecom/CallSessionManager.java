package com.tridev.callsecurepro.telecom;

import android.telecom.Call;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Process-local registry for active Telecom calls.
 *
 * This class is intentionally limited to observation/state distribution in this step.
 * It does not answer, reject, disconnect, mute, route audio, or otherwise change a call.
 * Those user-controlled actions will be connected to the in-call UI in the next layer.
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

        return activeCalls.get(0);
    }

    private void notifyListeners() {
        List<Call> snapshot = getActiveCallsSnapshot();
        for (Listener listener : listeners) {
            listener.onCallSessionsChanged(snapshot);
        }
    }
}
