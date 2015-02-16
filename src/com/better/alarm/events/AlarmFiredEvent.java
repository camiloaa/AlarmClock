package com.better.alarm.events;

import com.better.alarm.model.interfaces.Intents;

public class AlarmFiredEvent extends AlarmEvent {
    @Override
    protected String getAction() {
        return Intents.ALARM_ALERT_ACTION;
    }
}
