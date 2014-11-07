/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.better.alarm.presenter.background;

import javax.inject.Inject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.better.alarm.Component;
import com.better.alarm.R;
import com.better.alarm.events.AlarmFiredEvent;
import com.better.alarm.events.DemuteEvent;
import com.better.alarm.events.DismissedEvent;
import com.better.alarm.events.IBus;
import com.better.alarm.events.MuteEvent;
import com.better.alarm.events.PrealarmFiredEvent;
import com.better.alarm.events.SnoozedEvent;
import com.better.alarm.events.SoundExpiredEvent;
import com.better.alarm.events.StartAlarmSampleEvent;
import com.better.alarm.events.StartPrealarmSampleEvent;
import com.better.alarm.events.StopAlarmSampleEvent;
import com.better.alarm.events.StopPrealarmSampleEvent;
import com.better.alarm.model.interfaces.Alarm;
import com.better.alarm.model.interfaces.AlarmNotFoundException;
import com.better.alarm.model.interfaces.IAlarmsManager;
import com.better.alarm.presenter.SettingsActivity;
import com.better.alarm.view.VolumePreference;
import com.github.androidutils.logger.Logger;
import com.github.androidutils.wakelock.WakeLockManager;
import com.squareup.otto.Subscribe;

/**
 * Manages alarms and vibe. Runs as a service so that it can continue to play if
 * another activity overrides the AlarmAlert dialog.
 */
public class KlaxonPresenter extends Component {
    @Inject private TelephonyManager mTelephonyManager;
    @Inject private Logger log;
    @Inject private Volume volume;
    @Inject private PowerManager pm;
    @Inject private SharedPreferences sp;
    @Inject private WakeLockManager wakelocks;
    @Inject private IAlarmsManager alarms;
    @Inject private Context context;
    @Inject private AudioManager audioManager;
    @Inject private IBus bus;

    private volatile IMediaPlayer mMediaPlayer;
    // TODO private WakeLock wakeLock;
    private Alarm lastAlarm;
    private boolean lastInCallState;

    private final PhoneStateListener phoneStateListenerImpl = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String ignored) {
            boolean newState = state != TelephonyManager.CALL_STATE_IDLE;
            if (lastInCallState != newState) {
                lastInCallState = newState;
                if (lastInCallState) {
                    log.d("Call has started. Mute.");
                    volume.mute();
                } else {
                    log.d("Call has ended. fadeInFast.");
                    if (lastAlarm != null && !lastAlarm.isSilent()) {
                        initializePlayer(getAlertOrDefault(lastAlarm));
                        volume.fadeInFast();
                    }
                }
            }
        }
    };

    private static class Volume implements OnSharedPreferenceChangeListener {
        private static final int FAST_FADE_IN_TIME = 5000;

        private static final int FADE_IN_STEPS = 100;

        // Volume suggested by media team for in-call alarms.
        private static final float IN_CALL_VOLUME = 0.125f;

        // TODO XML
        // i^2/maxi^2
        private static final float[] ALARM_VOLUMES = { 0f, 0.01f, 0.04f, 0.09f, 0.16f, 0.25f, 0.36f, 0.49f, 0.64f,
                0.81f, 1.0f };

        @Inject private SharedPreferences sp;

        private final class FadeInTimer extends CountDownTimer {
            private final long fadeInTime;
            private final long fadeInStep;
            private final float targetVolume;
            private final double multiplier;

            private FadeInTimer(long millisInFuture, long countDownInterval) {
                super(millisInFuture, countDownInterval);
                fadeInTime = millisInFuture;
                fadeInStep = countDownInterval;
                targetVolume = ALARM_VOLUMES[type == Type.NORMAL ? alarmVolume : preAlarmVolume];
                multiplier = targetVolume / Math.pow(fadeInTime / fadeInStep, 2);
            }

            @Override
            public void onTick(long millisUntilFinished) {
                long elapsed = fadeInTime - millisUntilFinished;
                float i = elapsed / fadeInStep;
                float adjustedVolume = (float) (multiplier * (Math.pow(i, 2)));
                player.setVolume(adjustedVolume, adjustedVolume);
            }

            @Override
            public void onFinish() {
                log.d("Fade in completed");
            }
        }

        public enum Type {
            NORMAL, PREALARM
        };

        private Type type = Type.NORMAL;
        private IMediaPlayer player;
        @Inject private Logger log;
        private int preAlarmVolume = 0;
        private int alarmVolume = 4;

        private CountDownTimer timer;

        public void setMode(Type type) {
            this.type = type;
        }

        public void setPlayer(IMediaPlayer player) {
            this.player = player;
        }

        /**
         * Instantly apply the targetVolume. To fade in use
         * {@link #fadeInAsSetInSettings()}
         */
        public void apply() {
            float fvolume;
            try {
                if (type == Type.PREALARM) {
                    fvolume = ALARM_VOLUMES[preAlarmVolume];
                } else {
                    fvolume = ALARM_VOLUMES[alarmVolume];
                }
            } catch (IndexOutOfBoundsException e) {
                fvolume = 1f;
            }
            player.setVolume(fvolume, fvolume);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(VolumePreference.KEY_PREALARM_VOLUME)) {
                preAlarmVolume = sharedPreferences.getInt(key, VolumePreference.DEFAULT_PREALARM_VOLUME);
                if (preAlarmVolume > ALARM_VOLUMES.length) {
                    preAlarmVolume = ALARM_VOLUMES.length;
                    log.w("Truncated targetVolume!");
                }
                if (player.isPlaying() && type == Type.PREALARM) {
                    player.setVolume(ALARM_VOLUMES[preAlarmVolume], ALARM_VOLUMES[preAlarmVolume]);
                }

            } else if (key.equals(VolumePreference.KEY_ALARM_VOLUME)) {
                alarmVolume = sharedPreferences.getInt(key, VolumePreference.DEFAULT_ALARM_VOLUME);
                if (alarmVolume > ALARM_VOLUMES.length) {
                    alarmVolume = ALARM_VOLUMES.length;
                    log.w("Truncated targetVolume!");
                }
                if (player.isPlaying() && type == Type.NORMAL) {
                    player.setVolume(ALARM_VOLUMES[alarmVolume], ALARM_VOLUMES[alarmVolume]);
                }
            }
        }

        /**
         * Fade in to set targetVolume
         */
        public void fadeInAsSetInSettings() {
            String asString = sp.getString(SettingsActivity.KEY_FADE_IN_TIME_SEC, "30");
            int time = Integer.parseInt(asString) * 1000;
            fadeIn(time);
        }

        public void fadeInFast() {
            fadeIn(FAST_FADE_IN_TIME);
        }

        public void cancelFadeIn() {
            if (timer != null) {
                timer.cancel();
            }
        }

        public void mute() {
            cancelFadeIn();
            player.setVolume(ALARM_VOLUMES[0], ALARM_VOLUMES[0]);
        }

        private void fadeIn(int time) {
            cancelFadeIn();
            player.setVolume(0, 0);
            timer = new FadeInTimer(time, time / FADE_IN_STEPS);
            timer.start();
        }
    }

    @Override
    public void init() {
        mMediaPlayer = new NullMediaPlayer();
        // wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
        // "KlaxonPresenter");
        // wakeLock.acquire();
        // Listen for incoming calls to kill the lastAlarm.
        volume.setPlayer(mMediaPlayer);
        lastInCallState = mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE;
        mTelephonyManager.listen(phoneStateListenerImpl, PhoneStateListener.LISTEN_CALL_STATE);
        sp.registerOnSharedPreferenceChangeListener(volume);
        volume.onSharedPreferenceChanged(sp, VolumePreference.KEY_PREALARM_VOLUME);
        volume.onSharedPreferenceChanged(sp, VolumePreference.KEY_ALARM_VOLUME);
        bus.register(this);
    }

    @Subscribe
    public void handle(DismissedEvent event) {
        stopAndCleanup();// return START_NOT_STICKY;
    }

    @Subscribe
    public void handle(SnoozedEvent event) {
        stopAndCleanup();// return START_NOT_STICKY;
    }

    @Subscribe
    public void handle(SoundExpiredEvent event) {
        stopAndCleanup();// return START_NOT_STICKY;
    }

    @Subscribe
    public void handle(MuteEvent event) {
        volume.mute();// return START_STICKY;
    }

    @Subscribe
    public void handle(DemuteEvent event) {
        volume.fadeInFast();// return START_STICKY;
    }

    //
    // } else if (action.equals(Intents.ACTION_STOP_PREALARM_SAMPLE)) {
    // stopAndCleanup();
    // return START_NOT_STICKY;
    //
    // } else if (action.equals(Intents.ACTION_START_ALARM_SAMPLE)) {
    // onStartAlarmSample(Volume.Type.NORMAL);
    // return START_STICKY;
    //
    // } else if (action.equals(Intents.ACTION_STOP_ALARM_SAMPLE)) {
    // stopAndCleanup();
    // return START_NOT_STICKY;
    //
    //
    // } else if (action.equals(Intents.ACTION_STOP_ALARM_SAMPLE)) {
    // stopAndCleanup();
    // return START_NOT_STICKY;

    @Subscribe
    public void handle(AlarmFiredEvent alarmFiredEvent) throws AlarmNotFoundException {
        volume.cancelFadeIn();
        volume.setMode(Volume.Type.NORMAL);
        Alarm alarm = alarms.getAlarm(alarmFiredEvent.id);
        if (!alarm.isSilent()) {
            initializePlayer(getAlertOrDefault(alarm));
            volume.fadeInAsSetInSettings();
        }
    }

    @Subscribe
    public void handle(PrealarmFiredEvent prealarmFiredEvent) throws AlarmNotFoundException {
        volume.cancelFadeIn();
        volume.setMode(Volume.Type.PREALARM);
        Alarm alarm = alarms.getAlarm(prealarmFiredEvent.id);
        if (!alarm.isSilent()) {
            initializePlayer(getAlertOrDefault(alarm));
            volume.fadeInAsSetInSettings();
        }
    }

    @Subscribe
    public void handle(StartPrealarmSampleEvent prealarmFiredEvent) {
        onStartAlarmSample(Volume.Type.PREALARM);
    }

    @Subscribe
    public void handle(StartAlarmSampleEvent prealarmFiredEvent) {
        onStartAlarmSample(Volume.Type.NORMAL);
    }

    @Subscribe
    public void handle(StopAlarmSampleEvent prealarmFiredEvent) {
        stopAndCleanup();
    }

    @Subscribe
    public void handle(StopPrealarmSampleEvent prealarmFiredEvent) {
        stopAndCleanup();
    }

    private void onStartAlarmSample(Volume.Type type) {
        volume.cancelFadeIn();
        volume.setMode(type);
        // if already playing do nothing. In this case signal continues.
        if (!mMediaPlayer.isPlaying()) {
            initializePlayer(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        }
        volume.apply();
    }

    private Uri getAlertOrDefault(Alarm alarm) {
        Uri alert = alarm.getAlert();
        // Fall back on the default lastAlarm if the database does not have an
        // lastAlarm stored.
        if (alert == null) {
            alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            log.d("Using default lastAlarm: " + alert.toString());
        }
        return alert;
    }

    // Do the common stuff when starting the lastAlarm.
    private void startAlarm(IMediaPlayer player) throws java.io.IOException, IllegalArgumentException,
            IllegalStateException {
        // do not play alarms if stream targetVolume is 0
        // (typically because ringer mode is silent).
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
            player.setAudioStreamType(AudioManager.STREAM_ALARM);
            player.setLooping(true);
            player.prepare();
            player.start();
        }
    }

    /**
     * Inits player and sets volume to 0
     * 
     * @param alert
     */
    private void initializePlayer(Uri alert) {
        // stop() checks to see if we are already playing.
        stop();

        // TODO: Reuse mMediaPlayer instead of creating a new one and/or use
        // RingtoneManager.
        mMediaPlayer = new MediaPlayerWrapper(new MediaPlayer());
        mMediaPlayer.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                log.e("Error occurred while playing audio.");
                volume.cancelFadeIn();
                mMediaPlayer.stop();
                mMediaPlayer.release();
                nullifyMediaPlayer();
                return true;
            }
        });

        volume.setPlayer(mMediaPlayer);
        volume.mute();
        try {
            // Check if we are in a call. If we are, use the in-call lastAlarm
            // resource at a low targetVolume to not disrupt the call.
            if (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                log.d("Using the in-call lastAlarm");
                setDataSourceFromResource(context.getResources(), mMediaPlayer, R.raw.in_call_alarm);
            } else {
                mMediaPlayer.setDataSource(context, alert);
            }
            startAlarm(mMediaPlayer);
        } catch (Exception ex) {
            log.w("Using the fallback ringtone");
            // The alert may be on the sd card which could be busy right
            // now. Use the fallback ringtone.
            try {
                // Must reset the media player to clear the error state.
                mMediaPlayer.reset();
                setDataSourceFromResource(context.getResources(), mMediaPlayer, R.raw.fallbackring);
                startAlarm(mMediaPlayer);
            } catch (Exception ex2) {
                // At this point we just don't play anything.
                log.e("Failed to play fallback ringtone", ex2);
            }
        }
    }

    private void setDataSourceFromResource(Resources resources, IMediaPlayer player, int res)
            throws java.io.IOException {
        AssetFileDescriptor afd = resources.openRawResourceFd(res);
        if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        }
    }

    /**
     * Stops lastAlarm audio
     */
    private void stop() {
        log.d("stopping media player");
        // Stop audio playing
        try {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.release();
        } catch (IllegalStateException e) {
            log.e("stop failed with ", e);
        } finally {
            nullifyMediaPlayer();
        }

        // Stop listening for incoming calls.
        // TODO! this is not how it should be done!=))
        // mTelephonyManager.listen(phoneStateListenerImpl,
        // PhoneStateListener.LISTEN_NONE);
        // sp.unregisterOnSharedPreferenceChangeListener(volume);
        // wakeLock.release();
    }

    private void stopAndCleanup() {
        volume.cancelFadeIn();
        stop();
    }

    private void nullifyMediaPlayer() {
        mMediaPlayer = new NullMediaPlayer();
        volume.setPlayer(mMediaPlayer);
    }
}
