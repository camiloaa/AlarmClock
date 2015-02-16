package com.better.alarm.events;

import com.better.alarm.model.interfaces.Intents;

public class AlarmChangedEvent extends AlarmEvent {
    @Override
    protected String getAction() {
        return Intents.ACTION_ALARM_CHANGED;
    }
}
