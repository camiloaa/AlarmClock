/*
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
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

import java.util.Calendar;

import javax.inject.Inject;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.format.DateFormat;

import com.better.alarm.Component;
import com.better.alarm.events.AlarmSceduledEvent;
import com.better.alarm.events.AlarmUnscheduledEvent;
import com.better.alarm.events.IBus;
import com.better.alarm.model.interfaces.AlarmNotFoundException;
import com.better.alarm.model.interfaces.IAlarmsManager;
import com.github.androidutils.logger.Logger;
import com.squareup.otto.Subscribe;

/**
 * This class reacts on {@link } and {@link } and
 * 
 * @author Yuriy
 * 
 */
public class ScheduledPresenter extends Component {
    private static final String DM12 = "E h:mm aa";
    private static final String DM24 = "E kk:mm";
    @Inject private IAlarmsManager alarms;
    @Inject private Logger logger;
    @Inject private IBus bus;
    @Inject private Context context;

    @Override
    public void init() {
        bus.register(this);
    }

    @Subscribe
    public void handle(AlarmSceduledEvent event) throws AlarmNotFoundException {
        // Broadcast intent for the notification bar
        Intent alarmChanged = new Intent("android.intent.action.ALARM_CHANGED");
        alarmChanged.putExtra("alarmSet", true);
        context.sendBroadcast(alarmChanged);

        // Update systems settings, so that interested Apps (like
        // KeyGuard)
        // will react accordingly
        String format = android.text.format.DateFormat.is24HourFormat(context) ? DM24 : DM12;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(event.nextNormalTimeInMillis);
        String timeString = (String) DateFormat.format(format, calendar);
        Settings.System.putString(context.getContentResolver(), Settings.System.NEXT_ALARM_FORMATTED,
                timeString);
    }

    @Subscribe
    public void handle(AlarmUnscheduledEvent event) {
        // Broadcast intent for the notification bar
        Intent alarmChanged = new Intent("android.intent.action.ALARM_CHANGED");
        alarmChanged.putExtra("alarmSet", false);
        context.sendBroadcast(alarmChanged);
        // Update systems settings, so that interested Apps (like KeyGuard)
        // will react accordingly
        Settings.System.putString(context.getContentResolver(), Settings.System.NEXT_ALARM_FORMATTED, "");
    }
}
