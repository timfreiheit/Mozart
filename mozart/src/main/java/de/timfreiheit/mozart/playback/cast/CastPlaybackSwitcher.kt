package de.timfreiheit.mozart.playback.cast

import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import de.timfreiheit.mozart.MozartMusicService
import de.timfreiheit.mozart.utils.TvHelper
import timber.log.Timber

class CastPlaybackSwitcher(private val service: MozartMusicService) {

    private var castSessionManager: SessionManager? = null
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null

    fun onCreate() {
        val playServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(service)
        if (!TvHelper.isTvUiMode(service) && playServicesAvailable == ConnectionResult.SUCCESS) {
            try {
                castSessionManager = CastContext.getSharedInstance(service).sessionManager
                castSessionManagerListener = CastSessionManagerListener()
                castSessionManager?.addSessionManagerListener(castSessionManagerListener,
                        CastSession::class.java)
                if (castSessionManager?.currentCastSession != null) {
                    castSessionManagerListener?.onSessionStarted(castSessionManager?.currentCastSession, castSessionManager?.currentCastSession?.toString())
                }
            } catch (e: Exception) {
                Timber.w(e, "Cast is not configured")
                castSessionManager = null
            }

        }
    }

    fun onDestroy() {
        castSessionManager?.removeSessionManagerListener(castSessionManagerListener,
                CastSession::class.java)
    }

    fun stopCasting() {
        val sessionManager = CastContext.getSharedInstance(service).sessionManager
        sessionManager?.endCurrentSession(true)
    }

    /**
     * Session Manager Listener responsible for switching the Playback instances
     * depending on whether it is connected to a remote player.
     */
    private inner class CastSessionManagerListener : SessionManagerListener<CastSession> {

        override fun onSessionStarted(session: CastSession, sessionId: String) {

            val playback = service.createCastPlayback(session)

            // In case we are casting, send the device name as an extra on MediaSession metadata.
            val sessionExtras = service.getSessionExtras()
            sessionExtras.putString(EXTRA_CONNECTED_CAST,
                    session.castDevice.friendlyName)
            service.setSessionExtras(sessionExtras)
            service.mediaRouter.setMediaSessionCompat(service.mediaSession)
            service.playbackManager.switchToPlayback(playback, true)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}

        override fun onSessionStarting(session: CastSession) {}

        override fun onSessionStartFailed(session: CastSession, error: Int) {}

        override fun onSessionEnded(session: CastSession, error: Int) {
            Timber.d("onSessionEnded")
            val sessionExtras = service.getSessionExtras()
            if (!sessionExtras.containsKey(EXTRA_CONNECTED_CAST)) {
                // we are not casting at the moment
                return
            }
            sessionExtras.remove(EXTRA_CONNECTED_CAST)
            service.setSessionExtras(sessionExtras)
            val playback = service.createLocalPlayback()
            service.mediaRouter.setMediaSessionCompat(null)
            service.playbackManager.switchToPlayback(playback, false)
        }

        override fun onSessionEnding(session: CastSession) {
            // This is our final chance to update the underlying stream position
            // In onSessionEnded(), the underlying CastPlayback#mRemoteMediaClient
            // is disconnected and hence we update our local value of stream position
            // to the latest position.
            service.playbackManager.playback.updateLastKnownStreamPosition()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {}

        override fun onSessionResumeFailed(session: CastSession, error: Int) {}

        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    companion object {

        // Extra on MediaSession that contains the Cast device name currently connected to
        val EXTRA_CONNECTED_CAST = "de.timfreiheit.mozart.CAST_NAME"
    }
}
