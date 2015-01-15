package com.better.alarm.presenter.background;

import javax.inject.Inject;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.better.alarm.Component;
import com.better.alarm.events.DismissedEvent;
import com.better.alarm.events.IBus;
import com.better.alarm.events.MuteEvent;
import com.better.alarm.events.PrealarmFiredEvent;
import com.better.alarm.events.SnoozedEvent;
import com.better.alarm.events.SoundExpiredEvent;
import com.better.alarm.presenter.background.VibrationPresenter.AlertConditionHelper;
import com.better.alarm.presenter.background.VibrationPresenter.AlertConditionHelper.AlertStrategy;
import com.github.androidutils.logger.Logger;
import com.squareup.otto.Subscribe;

public class CameraFlashlightPresenter extends Component {
    @Inject private Logger log;
    @Inject private TelephonyManager tm;
    @Inject private IBus bus;
    private AlertConditionHelper alertConditionHelper;
    private Camera camera;

    @Override
    public void init() {
        tm.listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                alertConditionHelper.setInCall(state != TelephonyManager.CALL_STATE_IDLE);
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);
        alertConditionHelper = new AlertConditionHelper(new AlertStrategy() {
            @Override
            public void start() {
                camera = Camera.open();
                if (camera != null) {
                    // Set the torch flash mode
                    Parameters param = camera.getParameters();
                    param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);

                    camera.setParameters(param);
                    camera.startPreview();
                }
            }

            @Override
            public void stop() {
                if (camera != null) {
                    camera.stopPreview();
                    camera.release();
                    camera = null;
                }
            }
        });
        alertConditionHelper.setEnabled(true);
        alertConditionHelper.setMuted(false);
        bus.register(this);
    }

    @Subscribe
    public void handle(PrealarmFiredEvent event) {
        alertConditionHelper.setStarted(true);
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
        stopAndCleanup();
    }

    private void stopAndCleanup() {
        alertConditionHelper.setStarted(false);
    }
}
