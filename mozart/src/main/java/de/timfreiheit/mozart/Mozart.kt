package de.timfreiheit.mozart

import android.content.Context
import android.os.RemoteException
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import com.hadisatrio.optional.Optional
import de.timfreiheit.mozart.playback.cast.CastReconnector
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject

object Mozart {

    private var initialized = false
    private var context: Context? = null

    private val mediaSessionSubject = BehaviorSubject.createDefault(Optional.absent<MediaSessionCompat.Token>())

    fun init(context: Context) {
        if (initialized) {
            return
        }
        initialized = true
        this.context = context.applicationContext
        CastReconnector(context)
    }

    private fun checkIfInitialized() {
        if (!initialized) {
            throw IllegalStateException("Mozart is not initialized")
        }
    }

    fun executeCommand(command: MozartPlayCommand) {
        checkIfInitialized()
        context?.startService(MozartServiceActions.executeCommand(context, command))
    }

    var mediaSessionToken: MediaSessionCompat.Token?
        get() = mediaSessionSubject.value.orNull()
        set(token) = mediaSessionSubject.onNext(Optional.ofNullable<MediaSessionCompat.Token>(token))

    fun mediaSessionToken(): Observable<Optional<MediaSessionCompat.Token>> {
        return mediaSessionSubject
    }

    fun mediaController(): Observable<MediaControllerCompat> {
        return mediaSessionSubject
                .filter { it.isPresent }
                .map { token -> MediaControllerCompat(context, token.get()) }
    }

    val mediaController: MediaControllerCompat?
        get() {
            checkIfInitialized()
            if (mediaSessionSubject.value != null && mediaSessionSubject.value.isPresent) {
                try {
                    return MediaControllerCompat(context, mediaSessionSubject.value.get())
                } catch (e: RemoteException) {
                    e.printStackTrace()
                    return null
                }

            }
            return null
        }

}
