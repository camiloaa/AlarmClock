package com.better.alarm.presenter.background;

import javax.inject.Inject;

import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.os.Vibrator;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.better.alarm.Component;
import com.better.alarm.events.AlarmFiredEvent;
import com.better.alarm.events.DemuteEvent;
import com.better.alarm.events.DismissedEvent;
import com.better.alarm.events.IBus;
import com.better.alarm.events.MuteEvent;
import com.better.alarm.events.SnoozedEvent;
import com.better.alarm.events.SoundExpiredEvent;
import com.better.alarm.presenter.SettingsActivity;
import com.better.alarm.presenter.background.VibrationPresenter.AlertConditionHelper.AlertStrategy;
import com.github.androidutils.logger.Logger;
import com.github.androidutils.wakelock.WakeLockManager;
import com.squareup.otto.Subscribe;

public class VibrationPresenter extends Component {
    private static final long[] sVibratePattern = new long[] { 500, 500 };
    @Inject private Vibrator mVibrator;
    @Inject private Logger log;
    @Inject private PowerManager pm;
    @Inject private SharedPreferences sp;
    private AlertConditionHelper alertConditionHelper;
    private CountDownTimer countDownTimer;
    @Inject private WakeLockManager wakelocks;
    @Inject private TelephonyManager tm;
    @Inject private IBus bus;

    @Override
    public void init() {
        // TODO wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
        // "VibrationPresenter");
        // TODO wakeLock.acquire();
        tm.listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                alertConditionHelper.setInCall(state != TelephonyManager.CALL_STATE_IDLE);
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);
        alertConditionHelper = new AlertConditionHelper(new AlertStrategy() {
            @Override
            public void start() {
                log.d("Starting vibration");
                mVibrator.vibrate(sVibratePattern, 0);
            }

            @Override
            public void stop() {
                log.d("Canceling vibration");
                mVibrator.cancel();
            }
        });
        alertConditionHelper.setEnabled(sp.getBoolean("vibrate", true));
        bus.register(this);
    }

    @Subscribe
    public void handle(AlarmFiredEvent event) {
        alertConditionHelper.setMuted(false);
        String asString = sp.getString(SettingsActivity.KEY_FADE_IN_TIME_SEC, "30");
        int time = Integer.parseInt(asString) * 1000;
        countDownTimer = new CountDownTimer(time, time) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                alertConditionHelper.setStarted(true);
            }
        }.start();

        // TODO report that we started return START_STICKY;
    }

    @Subscribe
    public void handle(SnoozedEvent event) {
        stopAndCleanup();
    }

    @Subscribe
    public void handle(DismissedEvent event) {
        stopAndCleanup();
    }

    @Subscribe
    public void handle(SoundExpiredEvent event) {
        stopAndCleanup();
    }

    @Subscribe
    public void handle(MuteEvent event) {
        alertConditionHelper.setMuted(true);
        // TODO we are going
    }

    @Subscribe
    public void handle(DemuteEvent event) {
        alertConditionHelper.setMuted(false);
        // TODO we are going
    }

    private void stopAndCleanup() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        alertConditionHelper.setStarted(false);
        log.d("Service destroyed");
        // TODO wakeLock.release(); and report we are done
    }

    public static final class AlertConditionHelper {
        public interface AlertStrategy {
            public void start();

            public void stop();
        }

        private final AlertStrategy alertConditionHelperListener;

        private boolean inCall;
        private boolean isStarted;
        private boolean isMuted;
        private boolean isEnabled;

        private void update() {
            if (isEnabled && !inCall && !isMuted && isStarted) {
                alertConditionHelperListener.start();
            } else {
                alertConditionHelperListener.stop();
            }
        }

        public AlertConditionHelper(AlertStrategy alertConditionHelperListener) {
            this.alertConditionHelperListener = alertConditionHelperListener;
        }

        public void setStarted(boolean isStarted) {
            this.isStarted = isStarted;
            update();
        }

        public void setMuted(boolean isMuted) {
            this.isMuted = isMuted;
            update();
        }

        public void setInCall(boolean inCall) {
            this.inCall = inCall;
            update();
        }

        public void setEnabled(boolean isEnabled) {
            this.isEnabled = isEnabled;
            update();
        }
    }
}
