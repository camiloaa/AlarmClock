/*
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
package com.better.alarm.model;

import javax.inject.Inject;

import roboguice.context.event.OnCreateEvent;
import roboguice.context.event.OnDestroyEvent;
import roboguice.event.Observes;
import roboguice.receiver.RoboBroadcastReceiver;
import roboguice.service.RoboService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.better.alarm.events.AlarmEvent;
import com.better.alarm.events.RequestDismiss;
import com.better.alarm.events.RequestSnooze;
import com.better.alarm.model.interfaces.AlarmNotFoundException;
import com.better.alarm.model.interfaces.Intents;
import com.github.androidutils.logger.Logger;
import com.github.androidutils.wakelock.WakeLockManager;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

public class AlarmsService extends RoboService implements Handler.Callback {
    /**
     * TODO SM should report when it is done
     */
    private static final int WAKELOCK_HOLD_TIME = 5000;
    private static final int EVENT_RELEASE_WAKELOCK = 1;
    @Inject private Alarms alarms;
    @Inject private Logger logger;
    @Inject private WakeLockManager wakelocks;
    @Inject private Bus bus;
    private Handler handler;

    /**
     * 
     * Dispatches intents to the KlaxonPresenter
     */
    public static class Receiver extends RoboBroadcastReceiver {
        @Inject private WakeLockManager wakelocks;

        @Override
        protected void handleReceive(Context context, Intent intent) {
            intent.setClass(context, AlarmsService.class);
            wakelocks.acquirePartialWakeLock(intent, "AlarmsService");
            context.startService(intent);
        }
    }

    public void onCreate(@Observes OnCreateEvent<Service> onCreate) {
        handler = new Handler(this);
        bus.register(this);
    }

    public void onDestroy(@Observes OnDestroyEvent<Service> onDestroyEvent) {
        bus.unregister(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            String action = intent.getAction();
            if (action.equals(AlarmsScheduler.ACTION_FIRED)) {
                int id = intent.getIntExtra(AlarmsScheduler.EXTRA_ID, -1);
                AlarmCore alarm = alarms.getAlarm(id);
                alarms.onAlarmFired(alarm,
                        CalendarType.valueOf(intent.getExtras().getString(AlarmsScheduler.EXTRA_TYPE)));
                logger.d("AlarmCore fired " + id);

            } else if (action.equals(Intent.ACTION_BOOT_COMPLETED) || action.equals(Intent.ACTION_TIMEZONE_CHANGED)
                    || action.equals(Intent.ACTION_LOCALE_CHANGED)) {
                logger.d("Refreshing alarms because of " + action);
                alarms.refresh();

            } else if (action.equals(Intent.ACTION_TIME_CHANGED)) {
                alarms.onTimeSet();

            } else if (action.startsWith(Intents.ACTION_ALARM_EVENT)) {
                String className = intent.getStringExtra(Intents.EXTRA_EVENT_CLASS);
                int id = intent.getIntExtra(Intents.EXTRA_ID, -1);
                try {
                    @SuppressWarnings("unchecked") AlarmEvent event = (AlarmEvent) Class.forName(className)
                            .newInstance();
                    event.id = id;
                    event.alarm = alarms.getAlarm(id);
                    bus.post(event);
                } catch (Exception e) {
                    logger.e("Terrible failure!", e);
                }
            }
        } catch (AlarmNotFoundException e) {
            logger.d("Alarm not found");
        }

        Message msg = handler.obtainMessage(EVENT_RELEASE_WAKELOCK);
        msg.obj = intent;
        handler.sendMessageDelayed(msg, WAKELOCK_HOLD_TIME);

        return START_NOT_STICKY;
    }

    @Subscribe
    public void handle(RequestDismiss event) {
        alarms.dismiss(event.alarm);
    }

    @Subscribe
    public void handle(RequestSnooze event) {
        alarms.snooze(event.alarm);
    }

    @Override
    public boolean handleMessage(Message msg) {
        wakelocks.releasePartialWakeLock((Intent) msg.obj);
        return true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
