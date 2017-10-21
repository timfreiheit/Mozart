package de.timfreiheit.mozart

import android.content.Context
import android.os.RemoteException
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import com.gojuno.koptional.None
import com.gojuno.koptional.Optional
import com.gojuno.koptional.rxjava2.filterSome
import com.gojuno.koptional.toOptional
import de.timfreiheit.mozart.playback.cast.CastReconnector
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject

object Mozart {

    private var initialized = false
    private var context: Context? = null

    private val mediaSessionSubject = BehaviorSubject.createDefault<Optional<MediaSessionCompat.Token>>(None)

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
        context?.let { context ->
            context.startService(MozartServiceActions.executeCommand(context, command))
        }
    }

    var mediaSessionToken: MediaSessionCompat.Token?
        get() = mediaSessionSubject.value.toNullable()
        set(token) = mediaSessionSubject.onNext(token.toOptional())

    fun mediaSessionToken(): Observable<Optional<MediaSessionCompat.Token>> {
        return mediaSessionSubject
    }

    fun mediaController(): Observable<MediaControllerCompat> {
        return mediaSessionSubject
                .filterSome()
                .map { token -> MediaControllerCompat(context, token) }
    }

    val mediaController: MediaControllerCompat?
        get() {
            checkIfInitialized()
            mediaSessionToken?.let { mediaSessionToken ->
                return try {
                    MediaControllerCompat(context, mediaSessionToken)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                    null
                }
            }
            return null
        }

}
