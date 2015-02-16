package com.better.alarm.events;

import com.better.alarm.model.interfaces.Intents;

public class PrealarmFiredEvent extends AlarmEvent {
    @Override
    protected String getAction() {
        return Intents.ALARM_PREALARM_ACTION;
    }
}
