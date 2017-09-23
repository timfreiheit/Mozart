package de.timfreiheit.mozart.playback.cast;

import android.os.Bundle;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import de.timfreiheit.mozart.MozartMusicService;
import de.timfreiheit.mozart.playback.Playback;
import de.timfreiheit.mozart.utils.TvHelper;
import timber.log.Timber;

public class CastPlaybackSwitcher {

    // Extra on MediaSession that contains the Cast device name currently connected to
    public static final String EXTRA_CONNECTED_CAST = "de.timfreiheit.mozart.CAST_NAME";

    private SessionManager castSessionManager;
    private SessionManagerListener<CastSession> castSessionManagerListener;
    private MozartMusicService service;

    public CastPlaybackSwitcher(MozartMusicService service) {
        this.service = service;
    }

    public void onCreate() {
        int playServicesAvailable =
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(service);
        if (!TvHelper.isTvUiMode(service) && playServicesAvailable == ConnectionResult.SUCCESS) {
            try {
                castSessionManager = CastContext.getSharedInstance(service).getSessionManager();
                castSessionManagerListener = new CastSessionManagerListener();
                castSessionManager.addSessionManagerListener(castSessionManagerListener,
                        CastSession.class);
                if (castSessionManager.getCurrentCastSession() != null) {
                    castSessionManagerListener.onSessionStarted(castSessionManager.getCurrentCastSession(), castSessionManager.getCurrentCastSession().toString());
                }
            } catch (Exception e) {
                Timber.w(e, "Cast is not configured");
                castSessionManager = null;
            }
        }
    }

    public void onDestroy() {
        if (castSessionManager != null) {
            castSessionManager.removeSessionManagerListener(castSessionManagerListener,
                    CastSession.class);
        }
    }

    public void stopCasting() {
        SessionManager sessionManager = CastContext.getSharedInstance(service).getSessionManager();
        if (sessionManager != null) {
            sessionManager.endCurrentSession(true);
        }
    }

    /**
     * Session Manager Listener responsible for switching the Playback instances
     * depending on whether it is connected to a remote player.
     */
    private class CastSessionManagerListener implements SessionManagerListener<CastSession> {

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {

            Playback playback = service.createCastPlayback(session);

            // In case we are casting, send the device name as an extra on MediaSession metadata.
            Bundle sessionExtras = service.getSessionExtras();
            sessionExtras.putString(EXTRA_CONNECTED_CAST,
                    session.getCastDevice().getFriendlyName());
            service.setSessionExtras(sessionExtras);
            service.getMediaRouter().setMediaSessionCompat(service.getMediaSession());
            service.getPlaybackManager().switchToPlayback(playback, true);
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
        }

        @Override
        public void onSessionStarting(CastSession session) {
        }

        @Override
        public void onSessionStartFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionEnded(CastSession session, int error) {
            Timber.d("onSessionEnded");
            Bundle sessionExtras = service.getSessionExtras();
            if (!sessionExtras.containsKey(EXTRA_CONNECTED_CAST)) {
                // we are not casting at the moment
                return;
            }
            sessionExtras.remove(EXTRA_CONNECTED_CAST);
            service.setSessionExtras(sessionExtras);
            Playback playback = service.createLocalPlayback();
            service.getMediaRouter().setMediaSessionCompat(null);
            service.getPlaybackManager().switchToPlayback(playback, false);
        }

        @Override
        public void onSessionEnding(CastSession session) {
            // This is our final chance to update the underlying stream position
            // In onSessionEnded(), the underlying CastPlayback#mRemoteMediaClient
            // is disconnected and hence we update our local value of stream position
            // to the latest position.
            service.getPlaybackManager().getPlayback().updateLastKnownStreamPosition();
        }

        @Override
        public void onSessionResuming(CastSession session, String sessionId) {
        }

        @Override
        public void onSessionResumeFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionSuspended(CastSession session, int reason) {
        }
    }
}
