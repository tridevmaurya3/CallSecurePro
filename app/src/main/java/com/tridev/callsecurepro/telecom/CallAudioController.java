package com.tridev.callsecurepro.telecom;

import android.telecom.CallAudioState;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Process-local bridge between the visible in-call UI and Android InCallService audio APIs.
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
        CallAudioState state = latestAudioState;
        if (enabled) {
            return setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        }

        if (state == null) {
            return false;
        }

        int supported = state.getSupportedRouteMask();
        if ((supported & CallAudioState.ROUTE_EARPIECE) != 0) {
            return setAudioRoute(CallAudioState.ROUTE_EARPIECE);
        }
        if ((supported & CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
            return setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET);
        }
        if ((supported & CallAudioState.ROUTE_BLUETOOTH) != 0) {
            return setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
        }
        return false;
    }

    public boolean setAudioRoute(int route) {
        CallSecureInCallService service = serviceReference.get();
        CallAudioState state = latestAudioState;
        if (service == null || state == null) {
            return false;
        }

        int supported = state.getSupportedRouteMask();
        if ((supported & route) == 0) {
            return false;
        }

        service.setAudioRoute(route);
        return true;
    }

    public boolean isRouteSupported(int route) {
        CallAudioState state = latestAudioState;
        return state != null && (state.getSupportedRouteMask() & route) != 0;
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
