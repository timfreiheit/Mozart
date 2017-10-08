package de.timfreiheit.mozart.ui

import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import de.timfreiheit.mozart.Mozart
import de.timfreiheit.mozart.R
import de.timfreiheit.mozart.databinding.MozartViewMiniControllerBinding
import de.timfreiheit.mozart.model.MozartPlaybackState
import de.timfreiheit.mozart.playback.cast.CastPlaybackSwitcher
import de.timfreiheit.mozart.utils.metadataChanges
import de.timfreiheit.mozart.utils.playbackStateChanges
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MiniControllerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = MozartViewMiniControllerBinding.inflate(LayoutInflater.from(context), this, true)

    private val compositeDisposable = CompositeDisposable()
    private val audioProgressDisposable = CompositeDisposable()

    private var mediaController: MediaControllerCompat? = null
    private var lastPlaybackState: PlaybackStateCompat? = null

    init {
        isClickable = true
        setOnClickListener {
            if (mediaController == null) {
                return@setOnClickListener
            }
            try {
                mediaController?.sessionActivity?.send()
            } catch (e: PendingIntent.CanceledException) {
                e.printStackTrace()
            }
        }
        Mozart.init(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isInEditMode) {
            return
        }

        visibility = View.GONE
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)

        if (visibility == View.VISIBLE) {
            registerPlaybackCallbacks()
        } else {
            compositeDisposable.clear()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        compositeDisposable.clear()
    }

    private fun registerPlaybackCallbacks() {
        compositeDisposable.clear()
        compositeDisposable.add(Mozart.mediaController()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { mediaControllerCompat -> this.mediaController = mediaControllerCompat }
                .switchMap { mediaControllerCompat ->

                    val playbackStateObservable = mediaControllerCompat.playbackStateChanges()
                            .doOnNext { playbackState -> updatePlaybackState(playbackState.toNullable()) }

                    val metadataObservable = mediaControllerCompat.metadataChanges()
                            .doOnNext { metadata -> updateMetadata(metadata.toNullable()) }

                    Observable.merge(playbackStateObservable, metadataObservable)
                }
                .subscribe({}, { it.printStackTrace() }))
    }

    protected fun updatePlaybackState(state: PlaybackStateCompat?) {
        lastPlaybackState = state
        audioProgressDisposable.clear()
        if (state == null) {
            return
        }

        updateExtraInfo()

        when (state.state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                visibility = View.VISIBLE
                binding.statusProgressBar.visibility = View.GONE
                binding.playPause.setImageResource(R.drawable.ic_pause_black_36dp)
                binding.playPause.visibility = View.VISIBLE
                binding.playPause.setOnClickListener { mediaController!!.transportControls.pause() }

                val disposable = Observable.interval(1, TimeUnit.SECONDS)
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .doOnNext { updateProgress() }
                        .subscribe({ }, { it.printStackTrace() })
                audioProgressDisposable.add(disposable)
                compositeDisposable.add(disposable)
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                visibility = View.VISIBLE
                binding.statusProgressBar.visibility = View.GONE
                binding.playPause.setImageResource(R.drawable.ic_play_arrow_black_36dp)
                binding.playPause.visibility = View.VISIBLE
                binding.playPause.setOnClickListener { mediaController!!.transportControls.play() }
                updateProgress()
            }
            PlaybackStateCompat.STATE_BUFFERING, PlaybackStateCompat.STATE_CONNECTING, PlaybackStateCompat.STATE_FAST_FORWARDING, PlaybackStateCompat.STATE_SKIPPING_TO_NEXT, PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS, PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM, PlaybackStateCompat.STATE_REWINDING -> {
                visibility = View.VISIBLE
                binding.statusProgressBar.visibility = View.VISIBLE
                binding.playPause.visibility = View.GONE
            }
            else -> {
                visibility = View.GONE
            }
        }
    }

    private fun updateProgress() {
        if (lastPlaybackState == null) {
            return
        }
        var currentPosition = lastPlaybackState!!.position
        if (lastPlaybackState!!.state != PlaybackStateCompat.STATE_PAUSED) {
            // Calculate the elapsed time between the last position update and now and unless
            // paused, we can assume (delta * speed) + current position is approximately the
            // latest position. This ensure that we do not repeatedly call the getPlaybackState()
            // on MediaControllerCompat.
            val timeDelta = SystemClock.elapsedRealtime() - lastPlaybackState!!.lastPositionUpdateTime
            currentPosition += (timeDelta.toInt() * lastPlaybackState!!.playbackSpeed).toLong()
        }
        setStreamPosition(currentPosition.toInt(), MozartPlaybackState.getStreamDuration(lastPlaybackState).toInt())
    }

    private fun setStreamPosition(position: Int, duration: Int) {
        var position = position
        Timber.d("setStreamPosition() called with position = [%d], duration = [%d]", position, duration)
        position = Math.min(position, duration)
        if (duration > 0) {
            binding.playbackProgressBar.max = duration
            binding.playbackProgressBar.progress = position
            binding.playbackProgressBar.visibility = View.VISIBLE
        } else {
            binding.playbackProgressBar.visibility = View.GONE
        }
    }

    private fun updateMetadata(mediaMetadata: MediaMetadataCompat?) {
        if (mediaMetadata == null) {
            visibility = View.GONE
            return
        }

        binding.title.text = mediaMetadata.description.title
        binding.subtitle.text = mediaMetadata.description.subtitle
        binding.cover.setImageBitmap(mediaMetadata.description.iconBitmap)
        updateExtraInfo()
    }

    private fun updateExtraInfo() {

        val castName = mediaController!!.extras.getString(CastPlaybackSwitcher.EXTRA_CONNECTED_CAST)
        if (castName != null) {
            val extraInfo = resources.getString(R.string.casting_to_device, castName)
            binding.extraInfo.text = extraInfo
            binding.extraInfo.visibility = View.VISIBLE
        } else {
            binding.extraInfo.visibility = View.GONE
        }
    }

}
