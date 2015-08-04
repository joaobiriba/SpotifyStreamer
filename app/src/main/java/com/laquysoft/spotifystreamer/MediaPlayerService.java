package com.laquysoft.spotifystreamer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.laquysoft.spotifystreamer.common.MainThreadBus;
import com.laquysoft.spotifystreamer.components.DaggerEventBusComponent;
import com.laquysoft.spotifystreamer.components.EventBusComponent;
import com.laquysoft.spotifystreamer.events.TrackPlayingEvent;
import com.laquysoft.spotifystreamer.model.ParcelableSpotifyObject;
import com.laquysoft.spotifystreamer.modules.EventBusModule;

import java.io.IOException;
import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Created by joaobiriba on 06/07/15.
 */
public class MediaPlayerService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    public static final String LOG_TAG = MediaPlayerService.class.getSimpleName();

    /**
     * Implementation notes
     * https://discussions.udacity.com/t/returning-to-the-song-currently-playing/21779/3
     * http://developer.android.com/guide/topics/media/mediaplayer.html#mpandservices
     */
    //Available Actions
    public static final String ACTION_PLAY_TRACK = "action_play_track";
    public static final String ACTION_PAUSE_TRACK = "action_pause_track";
    public static final String ACTION_RESUME_TRACK = "action_resume_track";
    public static final String ACTION_PLAY_PREVIOUS_TRACK = "action_previous_track";
    public static final String ACTION_PLAY_NEXT_TRACK = "action_next_track";
    public static final String ACTION_SET_TRACKS = "action_add_tracks";
    public static final String ACTION_SET_TRACK_PROGRESS_TO = "action_set_track_progress_to";
    public static final String ACTION_BROADCAST_CURRENT_TRACK = "action_broadcast_current_track";
    public static final int NOTIFICATION_ID = 3000;

    //Constants
    private static final String TRACKS_LIST = "tracks_list";
    private static final String TRACK_ID = "track_id";
    private static final String TRACK_PROGRESS = "track_progress";
    private static final String PREF_SHOW_PLAYBACK_CONTROLS_IN_LOCKSCREEN = "pref_show_playback_controls_in_lockscreen";

    //Variables
    ParcelableSpotifyObject mCurrentTrack;
    int mCurrentTrackIndex;
    MediaPlayer mMediaPlayer;
    BroadcastTrackProgressTask mBroadcastTrackProgressTask;
    ArrayList<ParcelableSpotifyObject> mTracksList;
    int mCurrentTrackThumbnailBitmap;

    @Inject
    MainThreadBus bus;

    /**
     * Constructor
     */
    public MediaPlayerService() {
        EventBusComponent component = DaggerEventBusComponent.builder().eventBusModule(new EventBusModule()).build();

        bus = component.provideMainThreadBus();
        bus.register(this);

    }

    /**
     * StartService Helpers
     */
    public static void setTracks(Context context, ArrayList<ParcelableSpotifyObject> tracksList) {
        Intent serviceIntent = new Intent(context, MediaPlayerService.class);
        serviceIntent.setAction(ACTION_SET_TRACKS);
        serviceIntent.putExtra(TRACKS_LIST, tracksList);
        context.startService(serviceIntent);
    }

    public static void playTrack(Context context, int trackId) {
        Intent serviceIntent = new Intent(context, MediaPlayerService.class);
        serviceIntent.setAction(ACTION_PLAY_TRACK);
        serviceIntent.putExtra(TRACK_ID, trackId);
        context.startService(serviceIntent);
    }

    public static void pauseTrack(Context context) {
        context.startService(getPauseTrackIntent(context));
    }

    public static Intent getPauseTrackIntent(Context context) {
        Intent serviceIntent = new Intent(context, MediaPlayerService.class);
        serviceIntent.setAction(ACTION_PAUSE_TRACK);
        return serviceIntent;
    }

    public static void resumeTrack(Context context) {
        context.startService(getResumeTrackIntent(context));
    }

    public static Intent getResumeTrackIntent(Context context) {
        Intent serviceIntent = new Intent(context, MediaPlayerService.class);
        serviceIntent.setAction(ACTION_RESUME_TRACK);
        return serviceIntent;
    }

    public static void playNextTrack(Context context) {
        context.startService(getPlayNextTrackIntent(context));
    }

    public static Intent getPlayNextTrackIntent(Context context) {
        Intent serviceIntent = new Intent(context, MediaPlayerService.class);
        serviceIntent.setAction(ACTION_PLAY_NEXT_TRACK);
        return serviceIntent;
    }

    public static void playPreviousTrack(Context context) {
        context.startService(getPlayPreviousTrackIntent(context));
    }

    public static Intent getPlayPreviousTrackIntent(Context context) {
        Intent serviceIntent = new Intent(context, MediaPlayerService.class);
        serviceIntent.setAction(ACTION_PLAY_PREVIOUS_TRACK);
        return serviceIntent;
    }

    public static void setTrackProgressTo(Context context, int progress) {
        Intent serviceIntent = new Intent(context, MediaPlayerService.class);
        serviceIntent.setAction(ACTION_SET_TRACK_PROGRESS_TO);
        serviceIntent.putExtra(TRACK_PROGRESS, progress);
        context.startService(serviceIntent);
    }

    public static void broadcastCurrentTrack(Context context) {
        Intent serviceIntent = new Intent(context, MediaPlayerService.class);
        serviceIntent.setAction(ACTION_BROADCAST_CURRENT_TRACK);
        context.startService(serviceIntent);
    }

    /**
     * Binder interface
     */
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not available");
    }


    /**
     * Custom methods
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        //Cancel notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //Set tracks
        if (intent.getAction().equals(ACTION_SET_TRACKS)) {
            setTracks(intent);
        }

        //Previous track
        if (intent.getAction().equals(ACTION_PLAY_PREVIOUS_TRACK)) {
            playPreviousTrack();
        }

        //Play track
        if (intent.getAction().equals(ACTION_PLAY_TRACK)) {
            int trackId = intent.getIntExtra(TRACK_ID, -1);
            playTrack(trackId);
        }

        //Pause track
        if (intent.getAction().equals(ACTION_PAUSE_TRACK)) {
            pauseTrack();
        }

        //Resume track
        if (intent.getAction().equals(ACTION_RESUME_TRACK)) {
            resumeTrack();
        }

        //Next track
        if (intent.getAction().equals(ACTION_PLAY_NEXT_TRACK)) {
            playNextTrack();
        }

        //Set track progress
        if (intent.getAction().equals(ACTION_SET_TRACK_PROGRESS_TO)) {
            int progress = intent.getIntExtra(TRACK_PROGRESS, 0);
            setTrackProgressTo(progress);
        }

        //Request current track broadcast
        if (intent.getAction().equals(ACTION_BROADCAST_CURRENT_TRACK)) {
            //if (mCurrentTrack != null)
            //broadcastTrackToBePlayed();
        }

        return START_NOT_STICKY;
    }

//    private Track getTrackById(String trackId) {
//        for (Track track : mTracksList) {
//            if (track.id.equals(trackId))
//                return track;
//        }
//
//        //Should not happen
//        return null;
//    }

    private void setTracks(Intent data) {

        mTracksList = data.getParcelableArrayListExtra(TRACKS_LIST);
    }

    private void playPreviousTrack() {
        int previousTrackIndex = mCurrentTrackIndex - 1;
        if (mTracksList == null || previousTrackIndex < 0)
            return;

        playTrack(previousTrackIndex);
    }

    private void playNextTrack() {
        int nextTrackIndex = mCurrentTrackIndex + 1;

        if (mTracksList == null || nextTrackIndex >= mTracksList.size())
            return;

        playTrack(nextTrackIndex);
    }

    private void stopPlayback() {
        if (mMediaPlayer == null)
            return;

        if (mMediaPlayer.isPlaying())
            mMediaPlayer.stop();

        mMediaPlayer.setOnPreparedListener(null);
        mMediaPlayer.reset();
        mMediaPlayer.release();
        mMediaPlayer = null;

        if (mBroadcastTrackProgressTask != null)
            mBroadcastTrackProgressTask.cancel(true);
    }

    private void playTrack(int trackId) {
        //Stop playback
        stopPlayback();

        //Get track
        mCurrentTrack = mTracksList.get(trackId); //getTrackById(trackId);
        mCurrentTrackIndex = mTracksList.indexOf(mCurrentTrack);
        String trackUrl = mCurrentTrack.previewUrl;

        //Notify track to be played
        // broadcastTrackToBePlayed();

        //Set current track in app
        //SpotifyStreamerApp app = (SpotifyStreamerApp) getApplication();
        //app.setCurrentTrack(mCurrentTrack);

        //Start Media Player
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        try {
            mMediaPlayer.setDataSource(trackUrl);
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pauseTrack() {
        if (mMediaPlayer == null)
            return;

        mMediaPlayer.pause();

        if (mBroadcastTrackProgressTask != null)
            mBroadcastTrackProgressTask.cancel(true);

        showNotification();
    }

    private void resumeTrack() {
        if (mMediaPlayer == null)
            return;

        mMediaPlayer.start();

        mBroadcastTrackProgressTask = new BroadcastTrackProgressTask();
        mBroadcastTrackProgressTask.execute();

        showNotification();
    }

    private void setTrackProgressTo(int progress) {
        //if (mMediaPlayer == null)
            //broadcastTrackPlaybackCompleted();

            if (mMediaPlayer.isPlaying())
                mMediaPlayer.seekTo(progress);
    }

    /**
     * Player broadcasts
     */
//    private void broadcastTrackToBePlayed() {
//        TrackToBePlayedEvent event = new TrackToBePlayedEvent(mCurrentTrack);
//        EventBus.getDefault().post(event);
//
//        showNotification();
//    }
//
//    private void broadcastTrackPlayingProgress() {
//        TrackPlayingProgressEvent event = TrackPlayingProgressEvent.newInstance(
//                mCurrentTrack,
//                mMediaPlayer.getCurrentPosition(),
//                mMediaPlayer.getDuration()
//        );
//        EventBus.getDefault().post(event);
//    }
//
//    private void broadcastTrackPlaybackCompleted() {
//        TrackPlaybackCompletedEvent event = new TrackPlaybackCompletedEvent(mCurrentTrack);
//        EventBus.getDefault().post(event);
//
//        showNotification();
//    }


    /**
     * Notifications
     */
    private void showNotificationUsingCustomLayout() {

        //New Remote View
        RemoteViews remoteView = new RemoteViews(getPackageName(), R.layout.view_notification);
        //remoteView.setTextViewText(R.id.track_name, mCurrentTrack.name);
        //remoteView.setTextViewText(R.id.artist_name, mCurrentTrack.artists.get(0).name);

        //Playback controls
        //Previous Track Intent
        remoteView.setOnClickPendingIntent(
                R.id.ib_rewind,
                PendingIntent.getService(this, 0, getPlayPreviousTrackIntent(this), 0)
        );

        //Resume/Pause
        remoteView.setViewVisibility(R.id.ib_play_pause, View.VISIBLE);
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            remoteView.setViewVisibility(R.id.ib_play_pause, View.GONE);
            remoteView.setOnClickPendingIntent(
                    R.id.ib_play_pause,
                    PendingIntent.getService(this, 0, getPauseTrackIntent(this), 0)
            );
        } else {
            remoteView.setViewVisibility(R.id.ib_play_pause, View.GONE);
            remoteView.setOnClickPendingIntent(
                    R.id.ib_play_pause,
                    PendingIntent.getService(this, 0, getResumeTrackIntent(this), 0)
            );
        }


        //Next Track Intent
        remoteView.setOnClickPendingIntent(
                R.id.ib_fast_forward,
                PendingIntent.getService(this, 0, getPlayNextTrackIntent(this), 0)
        );

        //Content action
        //Show App Intent
        Intent showAppIntent = new Intent(this, MainActivity.class);
        showAppIntent.setAction(Intent.ACTION_MAIN);
        showAppIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent showAppPendingIntent = PendingIntent.getActivity(this, 0, showAppIntent, 0);

        //Prepare notification
        android.support.v4.app.NotificationCompat.Builder notificationBuilder = new android.support.v4.app.NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContent(remoteView)
                .setContentIntent(showAppPendingIntent);

        //Check if ongoing notification
        notificationBuilder.setOngoing(mMediaPlayer != null && mMediaPlayer.isPlaying());

        //Show playback controls in lockscreen
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showPlaybackControlsInLockScreen = sharedPreferences.getBoolean(PREF_SHOW_PLAYBACK_CONTROLS_IN_LOCKSCREEN, true);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH && showPlaybackControlsInLockScreen) {
            notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        //Display notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = notificationBuilder.build();
        notificationManager.notify(NOTIFICATION_ID, notification);

        //Thumbnail
        //String thumbnailUrl = Utils.getThumbnailUrl(mCurrentTrack.album.images, 0);
        //if (thumbnailUrl != null)
        //  Picasso.with(this).load(thumbnailUrl).into(remoteView, R.id.album_thumbnail, NOTIFICATION_ID, notification);
    }

    private void showNotificationUsingCompat() {

        //Build notification
        android.support.v4.app.NotificationCompat.Builder notificationBuilder = new android.support.v4.app.NotificationCompat.Builder(this);
        notificationBuilder.setContentTitle(mCurrentTrack.mName);
        notificationBuilder.setContentText(mCurrentTrack.mArtistName);
        notificationBuilder.setSmallIcon(android.R.drawable.ic_media_play);

        //Show playback controls in lockscreen
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showPlaybackControlsInLockScreen = sharedPreferences.getBoolean(PREF_SHOW_PLAYBACK_CONTROLS_IN_LOCKSCREEN, true);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH && showPlaybackControlsInLockScreen) {
            notificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        //Playback controls
        //Previous track action
        notificationBuilder.addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                PendingIntent.getService(this, 0, getPlayPreviousTrackIntent(this), 0)
        );
        //Pause / Resume track action
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            notificationBuilder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    PendingIntent.getService(this, 0, getPauseTrackIntent(this), 0)
            );
        } else {
            notificationBuilder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    PendingIntent.getService(this, 0, getResumeTrackIntent(this), 0)
            );
        }
        //Next track action
        notificationBuilder.addAction(
                android.R.drawable.ic_media_next,
                "Next",
                PendingIntent.getService(this, 0, getPlayNextTrackIntent(this), 0)
        );

        //Check if ongoing notification
        notificationBuilder.setOngoing(mMediaPlayer != null && mMediaPlayer.isPlaying());

        //Show App Intent
        Intent showAppIntent = new Intent(this, MainActivity.class);
        showAppIntent.setAction(Intent.ACTION_MAIN);
        showAppIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationBuilder.setContentIntent(PendingIntent.getActivity(this, 0, showAppIntent, 0));

        //Display notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = notificationBuilder.build();
        notificationManager.notify(NOTIFICATION_ID, notification);

        //Show thumbnail if available
        //String thumbnailUrl = Utils.getThumbnailUrl(mCurrentTrack.album.images, 0);
    }

    private void showNotification() {
        Log.d(LOG_TAG, "Displaying notification");
        showNotificationUsingCustomLayout();
        //showNotificationUsingCompat();
    }


    /**
     * BroadcastTrackProgressTask: reports song that is being played and progress
     */
    class BroadcastTrackProgressTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {

            while (!isCancelled()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (!mMediaPlayer.isPlaying())
                    return null;

                broadcastTrackPlayingProgress();
            }

            return null;
        }
    }

    private void broadcastTrackPlayingProgress() {
        TrackPlayingEvent event = TrackPlayingEvent.newInstance(
                mCurrentTrack,
                mMediaPlayer.getCurrentPosition()
        );
        bus.post(event);
    }


    /**
     * MediaPlayer implementation
     */
    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        broadcastTrackPlayingProgress();
        resumeTrack();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        Log.e(LOG_TAG, "Error during Playback!");
        //Toast.makeText(this, R.string.playback_error, Toast.LENGTH_LONG).show();
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        //broadcastTrackPlaybackCompleted();
    }


}