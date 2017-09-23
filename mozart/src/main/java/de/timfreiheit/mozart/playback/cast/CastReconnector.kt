package de.timfreiheit.mozart.playback.cast

import android.content.Context
import android.content.Intent

import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

import de.timfreiheit.mozart.Mozart
import de.timfreiheit.mozart.MozartServiceActions
import de.timfreiheit.mozart.utils.TvHelper
import timber.log.Timber

/**
 * reconnects the MozartService if the service is not already running
 */
class CastReconnector(context: Context) {

    private val context: Context = context.applicationContext

    init {
        val playServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this.context)
        if (!TvHelper.isTvUiMode(this.context) && playServicesAvailable == ConnectionResult.SUCCESS) {
            val castSessionManager: SessionManager
            try {
                castSessionManager = CastContext.getSharedInstance(context).sessionManager
                val castSessionManagerListener = CastSessionManagerListener()
                castSessionManager.addSessionManagerListener(castSessionManagerListener,
                        CastSession::class.java)
            } catch (e: Exception) {
                Timber.w("Cast is not configured")
            }

        }
    }

    /**
     * Session Manager Listener responsible for switching the Playback instances
     * depending on whether it is connected to a remote player.
     */
    private inner class CastSessionManagerListener : SessionManagerListener<CastSession> {

        override fun onSessionEnded(session: CastSession, error: Int) {}

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            if (Mozart.mediaSessionToken == null) {
                Timber.d("onSessionResumed: reconnect MozartService")
                val intent = MozartServiceActions.startIdle(context)
                context.startService(intent)
            }
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {}

        override fun onSessionStarting(session: CastSession) {}

        override fun onSessionStartFailed(session: CastSession, error: Int) {}

        override fun onSessionEnding(session: CastSession) {}

        override fun onSessionResuming(session: CastSession, sessionId: String) {}

        override fun onSessionResumeFailed(session: CastSession, error: Int) {}

        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

}
