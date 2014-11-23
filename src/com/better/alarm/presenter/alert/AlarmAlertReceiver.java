/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.better.alarm.presenter.alert;

import java.util.Calendar;

import javax.inject.Inject;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateFormat;

import com.better.alarm.Component;
import com.better.alarm.R;
import com.better.alarm.events.AlarmFiredEvent;
import com.better.alarm.events.DismissedEvent;
import com.better.alarm.events.IBus;
import com.better.alarm.events.PrealarmFiredEvent;
import com.better.alarm.events.RequestDismiss;
import com.better.alarm.events.RequestSnooze;
import com.better.alarm.events.SnoozeCanceledEvent;
import com.better.alarm.events.SnoozedEvent;
import com.better.alarm.events.SoundExpiredEvent;
import com.better.alarm.model.interfaces.Alarm;
import com.better.alarm.model.interfaces.AlarmNotFoundException;
import com.better.alarm.model.interfaces.IAlarmsManager;
import com.better.alarm.model.interfaces.Intents;
import com.better.alarm.presenter.TransparentActivity;
import com.github.androidutils.logger.Logger;
import com.squareup.otto.Subscribe;

/**
 * Glue class: connects AlarmAlert IntentReceiver to AlarmAlert activity. Passes
 * through Alarm ID.
 */
public class AlarmAlertReceiver extends Component {
    private static final String DM12 = "E h:mm aa";
    private static final String DM24 = "E kk:mm";
    private static final int NOTIFICATION_OFFSET = 1000;
    @Inject private Context mContext;
    @Inject private NotificationManager nm;
    @Inject private IAlarmsManager alarmsManager;
    @Inject private Logger logger;
    @Inject private IBus bus;
    @Inject private Intents intents;

    @Override
    public void init() {
        bus.register(this);
    }

    @Subscribe
    public void handle(AlarmFiredEvent event) throws AlarmNotFoundException {
        // our alarm fired again, remove snooze notification
        nm.cancel(event.id + NOTIFICATION_OFFSET);
        Alarm alarm = alarmsManager.getAlarm(event.id);
        onAlert(alarm);
    }

    @Subscribe
    public void handle(PrealarmFiredEvent event) throws AlarmNotFoundException {
        // our alarm fired again, remove snooze notification
        nm.cancel(event.id + NOTIFICATION_OFFSET);
        Alarm alarm = alarmsManager.getAlarm(event.id);
        onAlert(alarm);
    }

    @Subscribe
    public void handle(DismissedEvent event) {
        nm.cancel(event.id);
        nm.cancel(event.id + NOTIFICATION_OFFSET);
    }

    @Subscribe
    public void handle(SnoozeCanceledEvent event) {
        nm.cancel(event.id);
        nm.cancel(event.id + NOTIFICATION_OFFSET);
    }

    private void onAlert(Alarm alarm) {
        int id = alarm.getId();

        /* Close dialogs and window shade */
        Intent closeDialogs = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        mContext.sendBroadcast(closeDialogs);

        // Decide which activity to start based on the state of the
        // keyguard - is the screen locked or not.
        Class<? extends AlarmAlertFullScreen> c = AlarmAlert.class;
        KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode()) {
            // Use the full screen activity to unlock the screen.
            c = AlarmAlertFullScreen.class;
        }

        // Trigger a notification that, when clicked, will show the alarm
        // alert dialog. No need to check for fullscreen since this will always
        // be launched from a user action.
        Intent notify = new Intent(mContext, c);
        notify.putExtra(Intents.EXTRA_ID, id);
        PendingIntent pendingNotify = PendingIntent.getActivity(mContext, id, notify, 0);
        PendingIntent pendingSnooze = intents.createPendingIntent(RequestSnooze.class, id);
        PendingIntent pendingDismiss = intents.createPendingIntent(RequestDismiss.class, id);

        //@formatter:off
        Notification status = new NotificationCompat.Builder(mContext)
                .setContentTitle(alarm.getLabelOrDefault(mContext))
                .setContentText(mContext.getString(R.string.alarm_notify_text))
                .setSmallIcon(R.drawable.stat_notify_alarm)
                // setFullScreenIntent to show the user AlarmAlert dialog at the same time 
                // when the Notification Bar was created.
                .setFullScreenIntent(pendingNotify, true)
                // setContentIntent to show the user AlarmAlert dialog  
                // when he will click on the Notification Bar.
                .setContentIntent(pendingNotify)
                .setOngoing(true)
                .addAction(R.drawable.ic_action_snooze, mContext.getString(R.string.alarm_alert_snooze_text), pendingSnooze)
                .addAction(R.drawable.ic_action_dismiss, mContext.getString(R.string.alarm_alert_dismiss_text), pendingDismiss)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .build();
        //@formatter:on

        // Send the notification using the alarm id to easily identify the
        // correct notification.
        nm.notify(id, status);
    }

    @Subscribe
    public void handle(SnoozedEvent event) throws AlarmNotFoundException {
        int id = event.id;
        Alarm alarm = alarmsManager.getAlarm(event.id);
        nm.cancel(id);

        // What to do, when a user clicks on the notification bar
        PendingIntent pCancelSnooze = intents.createPendingIntent(RequestDismiss.class, id);

        // When button Reschedule is clicked, the TransparentActivity with
        // TimePickerFragment to set new alarm time is launched
        Intent reschedule = new Intent(mContext, TransparentActivity.class);
        reschedule.putExtra(Intents.EXTRA_ID, id);
        PendingIntent pendingReschedule = PendingIntent.getActivity(mContext, id, reschedule, 0);

        PendingIntent pendingDismiss = intents.createPendingIntent(RequestDismiss.class, id);

        String label = alarm.getLabelOrDefault(mContext);

        //@formatter:off
        Notification status = new NotificationCompat.Builder(mContext)
                // Get the display time for the snooze and update the notification.
                .setContentTitle(mContext.getString(R.string.alarm_notify_snooze_label, label))
                .setContentText(mContext.getString(R.string.alarm_notify_snooze_text, formatTimeString(alarm)))
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setContentIntent(pCancelSnooze)
                .setOngoing(true)
                .addAction(R.drawable.ic_action_reschedule_snooze, mContext.getString(R.string.alarm_alert_reschedule_text), pendingReschedule)
                .addAction(R.drawable.ic_action_dismiss, mContext.getString(R.string.alarm_alert_dismiss_text), pendingDismiss)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .build();
        //@formatter:on

        // Send the notification using the alarm id to easily identify the
        // correct notification.
        nm.notify(id + NOTIFICATION_OFFSET, status);
    }

    private String formatTimeString(Alarm alarm) {
        String format = android.text.format.DateFormat.is24HourFormat(mContext) ? DM24 : DM12;
        Calendar calendar = alarm.getSnoozedTime();
        String timeString = (String) DateFormat.format(format, calendar);
        return timeString;
    }

    @Subscribe
    public void handle(SoundExpiredEvent event) throws AlarmNotFoundException {
        int id = event.id;
        Alarm alarm = alarmsManager.getAlarm(event.id);
        PendingIntent intent = intents.createPendingIntent(RequestDismiss.class, id);
        // Update the notification to indicate that the alert has been
        // silenced.
        String label = alarm.getLabelOrDefault(mContext);
        int autoSilenceMinutes = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(mContext).getString(
                "auto_silence", "10"));
        String text = mContext.getString(R.string.alarm_alert_alert_silenced, autoSilenceMinutes);

        Notification n = new Notification.Builder(mContext)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setWhen(Calendar.getInstance().getTimeInMillis())
                .setContentIntent(intent)
                .setContentTitle(label)
                .setContentText(text)
                .setTicker(text)
                .getNotification();

        // We have to cancel the original notification since it is in the
        // ongoing section and we want the "killed" notification to be a plain
        // notification.
        nm.cancel(id);
        nm.notify(id, n);
    }
}
