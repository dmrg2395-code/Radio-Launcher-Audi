package com.radioaudi.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;

import java.util.ArrayList;
import java.util.List;

/**
 * Sirve para leer qué suena y para que dos fuentes de audio (por ejemplo Bluetooth y
 * YouTube Music) no suenen a la vez: cuando una empieza, las demás se pausan.
 */
public class NowPlayingService extends NotificationListenerService {
    private MediaSessionManager manager;
    private final List<MediaController> attached = new ArrayList<MediaController>();
    private final List<MediaController.Callback> callbacks = new ArrayList<MediaController.Callback>();

    private final MediaSessionManager.OnActiveSessionsChangedListener listener =
        new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override public void onActiveSessionsChanged(List<MediaController> controllers) {
                attach(controllers);
            }
        };

    @Override public void onListenerConnected() {
        try {
            manager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, NowPlayingService.class);
            manager.addOnActiveSessionsChangedListener(listener, me);
            attach(manager.getActiveSessions(me));
        } catch (Exception e) {
            // sin permiso todavía
        }
    }

    @Override public void onListenerDisconnected() {
        try {
            if (manager != null) manager.removeOnActiveSessionsChangedListener(listener);
        } catch (Exception e) {
            // nada
        }
        detach();
    }

    private void detach() {
        for (int i = 0; i < attached.size(); i++) {
            attached.get(i).unregisterCallback(callbacks.get(i));
        }
        attached.clear();
        callbacks.clear();
    }

    private void attach(List<MediaController> controllers) {
        detach();
        if (controllers == null) return;
        for (final MediaController c : controllers) {
            MediaController.Callback cb = new MediaController.Callback() {
                @Override public void onPlaybackStateChanged(PlaybackState state) {
                    stateChanged(c, state);
                }
            };
            c.registerCallback(cb);
            attached.add(c);
            callbacks.add(cb);
        }
    }

    private void stateChanged(MediaController who, PlaybackState state) {
        if (state == null || state.getState() != PlaybackState.STATE_PLAYING) return;
        if (!getSharedPreferences("lanzador", MODE_PRIVATE).getBoolean("exclusive", true)) return;
        for (MediaController other : attached) {
            if (other == who) continue;
            PlaybackState s = other.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                other.getTransportControls().pause();
            }
        }
    }
}
