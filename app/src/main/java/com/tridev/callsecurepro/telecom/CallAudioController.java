package com.tridev.callsecurepro.telecom;

import android.telecom.CallAudioState;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Process-local bridge between the user-facing in-call UI and Android InCallService audio APIs.
 *
 * The service instance is held weakly so the UI never owns the Telecom service lifecycle.
 */
public final class CallAudioController {

    public interface Listener {
        void onCallAudioStateChanged(@Nullable CallAudioState audioState);
    }

    private static final CallAudioController INSTANCE = new CallAudioController();

    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private WeakReference<CallSecureInCallService> serviceReference = new WeakReference<>(null);
    @Nullable
    private volatile CallAudioState latestAudioState;

    private CallAudioController() {
        // Singleton.
    }

    @NonNull
    public static CallAudioController getInstance() {
        return INSTANCE;
    }

    void attachService(@NonNull CallSecureInCallService service) {
        serviceReference = new WeakReference<>(service);
    }

    void detachService(@NonNull CallSecureInCallService service) {
        CallSecureInCallService current = serviceReference.get();
        if (current == service) {
            serviceReference.clear();
            latestAudioState = null;
            notifyListeners();
        }
    }

    void updateAudioState(@Nullable CallAudioState audioState) {
        latestAudioState = audioState;
        notifyListeners();
    }

    @Nullable
    public CallAudioState getLatestAudioState() {
        return latestAudioState;
    }

    public boolean setMuted(boolean muted) {
        CallSecureInCallService service = serviceReference.get();
        if (service == null) {
            return false;
        }
        service.setMuted(muted);
        return true;
    }

    public boolean setSpeakerEnabled(boolean enabled) {
        CallSecureInCallService service = serviceReference.get();
        if (service == null) {
            return false;
        }

        CallAudioState state = latestAudioState;
        if (enabled) {
            service.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
            return true;
        }

        int fallbackRoute = CallAudioState.ROUTE_EARPIECE;
        if (state != null) {
            int supported = state.getSupportedRouteMask();
            if ((supported & CallAudioState.ROUTE_EARPIECE) == 0
                    && (supported & CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
                fallbackRoute = CallAudioState.ROUTE_WIRED_HEADSET;
            } else if ((supported & CallAudioState.ROUTE_EARPIECE) == 0
                    && (supported & CallAudioState.ROUTE_BLUETOOTH) != 0) {
                fallbackRoute = CallAudioState.ROUTE_BLUETOOTH;
            }
        }

        service.setAudioRoute(fallbackRoute);
        return true;
    }

    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
        listener.onCallAudioStateChanged(latestAudioState);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        CallAudioState state = latestAudioState;
        for (Listener listener : listeners) {
            listener.onCallAudioStateChanged(state);
        }
    }
}
