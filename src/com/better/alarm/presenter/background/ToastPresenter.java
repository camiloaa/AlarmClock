/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2013 Yuriy Kulikov
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
import android.widget.Toast;

import com.better.alarm.Component;
import com.better.alarm.R;
import com.better.alarm.events.AlarmSetEvent;
import com.better.alarm.events.IBus;
import com.better.alarm.model.interfaces.Alarm;
import com.better.alarm.model.interfaces.AlarmNotFoundException;
import com.better.alarm.model.interfaces.IAlarmsManager;
import com.github.androidutils.logger.Logger;
import com.squareup.otto.Subscribe;

public class ToastPresenter extends Component {
    private static Toast sToast = null;
    @Inject private IAlarmsManager alarms;
    @Inject private Logger logger;
    @Inject private IBus bus;
    @Inject private Context context;

    @Override
    public void init() {
        bus.register(this);
    }

    @Subscribe
    public void handle(AlarmSetEvent alarmSetEvent) throws AlarmNotFoundException {
        Alarm alarm = alarms.getAlarm(alarmSetEvent.id);
        if (alarm.isEnabled()) {
            popAlarmSetToast(context, alarm, alarmSetEvent.millis);
        } else {
            logger.w("Alarm " + alarmSetEvent.id + " is already disabled");
        }
    }

    public static void setToast(Toast toast) {
        if (sToast != null) {
            sToast.cancel();
        }
        sToast = toast;
    }

    public static void cancelToast() {
        if (sToast != null) {
            sToast.cancel();
        }
        sToast = null;
    }

    static void popAlarmSetToast(Context context, Alarm alarm, long timeInMillis) {
        String toastText;
        toastText = formatToast(context, timeInMillis);
        Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_LONG);
        ToastPresenter.setToast(toast);
        toast.show();
    }

    /**
     * format "Alarm set for 2 days 7 hours and 53 minutes from now"
     * 
     * If prealarm is on it will be
     * 
     * "Alarm set for 2 days 7 hours and 53 minutes from now. Prealarm will
     * start 30 minutes before the main alarm".
     */
    static String formatToast(Context context, long timeInMillis) {
        long delta = timeInMillis - System.currentTimeMillis();
        long hours = delta / (1000 * 60 * 60);
        long minutes = delta / (1000 * 60) % 60;
        long days = hours / 24;
        hours = hours % 24;

        String daySeq = days == 0 ? "" : days == 1 ? context.getString(R.string.day) : context.getString(R.string.days,
                Long.toString(days));

        String minSeq = minutes == 0 ? "" : minutes == 1 ? context.getString(R.string.minute) : context.getString(
                R.string.minutes, Long.toString(minutes));

        String hourSeq = hours == 0 ? "" : hours == 1 ? context.getString(R.string.hour) : context.getString(
                R.string.hours, Long.toString(hours));

        boolean dispDays = days > 0;
        boolean dispHour = hours > 0;
        boolean dispMinute = minutes > 0;

        int index = (dispDays ? 1 : 0) | (dispHour ? 2 : 0) | (dispMinute ? 4 : 0);

        String[] formats = context.getResources().getStringArray(R.array.alarm_set);
        return String.format(formats[index], daySeq, hourSeq, minSeq);
    }
}
