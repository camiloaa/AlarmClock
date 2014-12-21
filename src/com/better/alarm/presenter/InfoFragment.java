package com.better.alarm.presenter;

import java.util.Calendar;

import javax.inject.Inject;

import roboguice.fragment.provided.RoboFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewSwitcher.ViewFactory;

import com.better.alarm.R;
import com.better.alarm.events.AlarmSceduledEvent;
import com.better.alarm.events.AlarmUnscheduledEvent;
import com.better.alarm.events.IBus;
import com.better.alarm.events.RequestScheduledUnscheduledStatus;
import com.better.alarm.model.interfaces.Alarm;
import com.better.alarm.model.interfaces.AlarmNotFoundException;
import com.better.alarm.model.interfaces.IAlarmsManager;
import com.github.androidutils.logger.Logger;
import com.squareup.otto.Subscribe;

/**
 * 
 * @author Yuriy
 * 
 */
public class InfoFragment extends RoboFragment implements ViewFactory {

    @Inject private IAlarmsManager alarms;
    @Inject private Logger logger;
    @Inject private IBus bus;
    @Inject private Context context;
    @Inject private SharedPreferences sp;

    private static final String DM12 = "E h:mm aa";
    private static final String DM24 = "E kk:mm";

    private TextSwitcher textView;
    private TextSwitcher remainingTime;

    private Alarm alarm;
    private TickReceiver mTickReceiver;

    private long milliseconds;

    private boolean isPrealarm;

    private final class TickReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (alarm != null) {
                formatString();
            }
        }
    }

    @Subscribe
    public void handle(AlarmSceduledEvent event) throws AlarmNotFoundException {
        logger.d(event);
        alarm = alarms.getAlarm(event.id);
        String format = android.text.format.DateFormat.is24HourFormat(context) ? DM24 : DM12;

        milliseconds = event.nextNormalTimeInMillis;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(milliseconds);

        String timeString = (String) DateFormat.format(format, calendar);
        textView.setText(timeString);
        isPrealarm = event.isPrealarm;
        formatString();
    }

    @Subscribe
    public void handle(AlarmUnscheduledEvent event) {
        logger.d(event);
        textView.setText("");
        remainingTime.setText("");
        alarm = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.info_fragment, container, false);
        textView = (TextSwitcher) view.findViewById(R.id.info_fragment_text_view);
        remainingTime = (TextSwitcher) view.findViewById(R.id.info_fragment_text_view_remaining_time);

        textView.setFactory(this);
        remainingTime.setFactory(this);

        Animation in = AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in);
        Animation out = AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out);
        textView.setInAnimation(in);
        textView.setOutAnimation(out);
        remainingTime.setInAnimation(in);
        remainingTime.setOutAnimation(out);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        logger.d("onResume");
        bus.register(this);
        bus.post(new RequestScheduledUnscheduledStatus());
        mTickReceiver = new TickReceiver();
        getActivity().registerReceiver(mTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    public void onPause() {
        super.onPause();
        logger.d("onPause");
        bus.unregister(this);
        getActivity().unregisterReceiver(mTickReceiver);
    }

    /**
     * format "Alarm set for 2 days 7 hours and 53 minutes from now"
     */
    private String formatRemainingTimeString(long timeInMillis) {
        long delta = timeInMillis - System.currentTimeMillis();
        long hours = delta / (1000 * 60 * 60);
        long minutes = delta / (1000 * 60) % 60;
        long days = hours / 24;
        hours = hours % 24;

        String daySeq = days == 0 ? "" : days == 1 ? getActivity().getString(R.string.day) : getActivity().getString(
                R.string.days, Long.toString(days));

        String minSeq = minutes == 0 ? "" : minutes == 1 ? getActivity().getString(R.string.minute) : getActivity()
                .getString(R.string.minutes, Long.toString(minutes));

        String hourSeq = hours == 0 ? "" : hours == 1 ? getActivity().getString(R.string.hour) : getActivity()
                .getString(R.string.hours, Long.toString(hours));

        boolean dispDays = days > 0;
        boolean dispHour = hours > 0;
        boolean dispMinute = minutes > 0;

        int index = (dispDays ? 1 : 0) | (dispHour ? 2 : 0) | (dispMinute ? 4 : 0);

        String[] formats = getActivity().getResources().getStringArray(R.array.alarm_set_short);
        return String.format(formats[index], daySeq, hourSeq, minSeq);
    }

    private void formatString() {
        if (isPrealarm) {
            int duration = Integer.parseInt(sp.getString("prealarm_duration", "-1"));
            remainingTime.setText(formatRemainingTimeString(milliseconds) + "\n"
                    + getResources().getString(R.string.prealarm_summary, duration));
        } else {
            remainingTime.setText(formatRemainingTimeString(milliseconds));
        }
    }

    @Override
    public View makeView() {
        TextView t = (TextView) getActivity().getLayoutInflater().inflate(R.layout.info_fragment_text, null);
        return t;
    }
}