package com.better.alarm.model;

import javax.inject.Inject;

import android.content.Context;

import com.better.alarm.events.AlarmEvent;
import com.better.alarm.events.IBus;

/**
 * Broadcasts alarm state with an intent
 * 
 * @author Yuriy
 * 
 */
public class AlarmStateNotifier implements IBus {

    @Inject private Context mContext;

    @Override
    public void post(Object event) {
        AlarmEvent alarmEvent = (AlarmEvent) event;
        mContext.sendBroadcast(alarmEvent.toIntent());
    }

    @Override
    public void register(Object listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unregister(Object listener) {
        throw new UnsupportedOperationException();
    }
}
