package de.timfreiheit.mozart.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.StyleRes;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.hadisatrio.optional.Optional;

import java.util.concurrent.TimeUnit;

import de.timfreiheit.mozart.Mozart;
import de.timfreiheit.mozart.R;
import de.timfreiheit.mozart.databinding.MozartViewMiniControllerBinding;
import de.timfreiheit.mozart.model.MozartPlaybackState;
import de.timfreiheit.mozart.playback.cast.CastPlaybackSwitcher;
import de.timfreiheit.mozart.utils.RxMediaController;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;

public class MiniControllerView extends FrameLayout {

    private MozartViewMiniControllerBinding binding;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final CompositeDisposable audioProgressDisposable = new CompositeDisposable();

    private MediaControllerCompat mediaController;
    private PlaybackStateCompat lastPlaybackState;

    public MiniControllerView(@NonNull Context context) {
        super(context);
        init();
    }

    public MiniControllerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MiniControllerView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public MiniControllerView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        if (isInEditMode()) {
            inflater.inflate(R.layout.mozart_view_mini_controller, this, true);
            return;
        }
        binding = MozartViewMiniControllerBinding.inflate(inflater, this, true);
        setClickable(true);

        setOnClickListener(v -> {
            if (mediaController == null) {
                return;
            }
            PendingIntent sessionActivityIntent = mediaController.getSessionActivity();
            try {
                sessionActivityIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (isInEditMode()) {
            return;
        }

        setVisibility(View.GONE);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);

        if (visibility == View.VISIBLE) {
            registerPlaybackCallbacks();
        } else {
            compositeDisposable.clear();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        compositeDisposable.clear();
    }

    private void registerPlaybackCallbacks() {
        compositeDisposable.clear();
        compositeDisposable.add(Mozart.get(getContext()).mediaController()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(mediaControllerCompat -> this.mediaController = mediaControllerCompat)
                .flatMap(mediaControllerCompat -> {

                    Observable<Optional<PlaybackStateCompat>> playbackStateObservable = RxMediaController.playbackState(mediaControllerCompat)
                            .doOnNext(playbackState -> {
                                updatePlaybackState(playbackState.orNull());
                            });

                    Observable<Optional<MediaMetadataCompat>> metadataObservable = RxMediaController.metadata(mediaControllerCompat)
                            .doOnNext(metadata -> {
                                updateMetadata(metadata.orNull());
                            });

                    return Observable.merge(playbackStateObservable, metadataObservable);
                })
                .subscribe(playbackState -> {
                }, Throwable::printStackTrace));
    }

    protected void updatePlaybackState(PlaybackStateCompat state) {
        lastPlaybackState = state;
        audioProgressDisposable.clear();
        if (state == null) {
            return;
        }

        updateExtraInfo();

        switch (state.getState()) {
            case PlaybackStateCompat.STATE_PLAYING:
                setVisibility(View.VISIBLE);
                binding.statusProgressBar.setVisibility(View.GONE);
                binding.playPause.setImageResource(R.drawable.ic_pause_black_36dp);
                binding.playPause.setVisibility(View.VISIBLE);
                binding.playPause.setOnClickListener(v -> {
                    mediaController.getTransportControls().pause();
                });

                Disposable disposable = Observable.interval(1, TimeUnit.SECONDS)
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .doOnNext(aLong -> updateProgress())
                        .subscribe(aLong -> {}, Throwable::printStackTrace);
                audioProgressDisposable.add(disposable);
                compositeDisposable.add(disposable);

                break;
            case PlaybackStateCompat.STATE_PAUSED:
                setVisibility(View.VISIBLE);
                binding.statusProgressBar.setVisibility(View.GONE);
                binding.playPause.setImageResource(R.drawable.ic_play_arrow_black_36dp);
                binding.playPause.setVisibility(View.VISIBLE);
                binding.playPause.setOnClickListener(v -> {
                    mediaController.getTransportControls().play();
                });
                updateProgress();
                break;
            case PlaybackStateCompat.STATE_BUFFERING:
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
            case PlaybackStateCompat.STATE_REWINDING:
                setVisibility(View.VISIBLE);
                binding.statusProgressBar.setVisibility(View.VISIBLE);
                binding.playPause.setVisibility(View.GONE);
                break;
            case PlaybackStateCompat.STATE_ERROR:
            case PlaybackStateCompat.STATE_NONE:
            default:
                setVisibility(View.GONE);
        }
    }

    private void updateProgress() {
        if (lastPlaybackState == null) {
            return;
        }
        long currentPosition = lastPlaybackState.getPosition();
        if (lastPlaybackState.getState() != PlaybackStateCompat.STATE_PAUSED) {
            // Calculate the elapsed time between the last position update and now and unless
            // paused, we can assume (delta * speed) + current position is approximately the
            // latest position. This ensure that we do not repeatedly call the getPlaybackState()
            // on MediaControllerCompat.
            long timeDelta = SystemClock.elapsedRealtime() -
                    lastPlaybackState.getLastPositionUpdateTime();
            currentPosition += (int) timeDelta * lastPlaybackState.getPlaybackSpeed();
        }
        setStreamPosition((int) currentPosition, (int) MozartPlaybackState.getStreamDuration(lastPlaybackState));
    }

    private void setStreamPosition(int position, int duration) {
        Timber.d("setStreamPosition() called with position = [%d], duration = [%d]", position, duration);
        position = Math.min(position, duration);
        if (duration > 0) {
            binding.playbackProgressBar.setMax(duration);
            binding.playbackProgressBar.setProgress(position);
            binding.playbackProgressBar.setVisibility(View.VISIBLE);
        } else {
            binding.playbackProgressBar.setVisibility(View.GONE);
        }
    }

    private void updateMetadata(MediaMetadataCompat mediaMetadata) {
        if (mediaMetadata == null) {
            setVisibility(View.GONE);
            return;
        }

        binding.title.setText(mediaMetadata.getDescription().getTitle());
        binding.subtitle.setText(mediaMetadata.getDescription().getSubtitle());
        binding.cover.setImageBitmap(mediaMetadata.getDescription().getIconBitmap());
        updateExtraInfo();
    }

    private void updateExtraInfo() {

        String castName = mediaController.getExtras().getString(CastPlaybackSwitcher.EXTRA_CONNECTED_CAST);
        if (castName != null) {
            String extraInfo = getResources().getString(R.string.casting_to_device, castName);
            binding.extraInfo.setText(extraInfo);
            binding.extraInfo.setVisibility(View.VISIBLE);
        } else {
            binding.extraInfo.setVisibility(View.GONE);
        }
    }

}
