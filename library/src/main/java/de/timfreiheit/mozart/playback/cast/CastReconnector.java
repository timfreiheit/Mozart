package de.timfreiheit.mozart.playback.cast;

import android.content.Context;
import android.content.Intent;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import de.timfreiheit.mozart.Mozart;
import de.timfreiheit.mozart.MozartServiceActions;
import de.timfreiheit.mozart.utils.TvHelper;
import timber.log.Timber;

/**
 * reconnects the MozartService if the service is not already running
 */
public class CastReconnector {

    private Context context;

    public CastReconnector(Context context) {
        this.context = context.getApplicationContext();
        int playServicesAvailable =
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this.context);
        if (!TvHelper.isTvUiMode(this.context) && playServicesAvailable == ConnectionResult.SUCCESS) {
            SessionManager castSessionManager;
            try {
                castSessionManager = CastContext.getSharedInstance(context).getSessionManager();
                SessionManagerListener<CastSession> castSessionManagerListener = new CastSessionManagerListener();
                castSessionManager.addSessionManagerListener(castSessionManagerListener,
                        CastSession.class);
            } catch (Exception e) {
                Timber.w("Cast is not configured");
            }
        }
    }

    /**
     * Session Manager Listener responsible for switching the Playback instances
     * depending on whether it is connected to a remote player.
     */
    private class CastSessionManagerListener implements SessionManagerListener<CastSession> {

        @Override
        public void onSessionEnded(CastSession session, int error) {
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            if (Mozart.get(context).getMediaSessionToken() == null) {
                Timber.d("onSessionResumed: reconnect MozartService");
                Intent intent = MozartServiceActions.startIdle(context);
                context.startService(intent);
            }
        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
        }

        @Override
        public void onSessionStarting(CastSession session) {
        }

        @Override
        public void onSessionStartFailed(CastSession session, int error) {
        }

        @Override
        public void onSessionEnding(CastSession session) {
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
