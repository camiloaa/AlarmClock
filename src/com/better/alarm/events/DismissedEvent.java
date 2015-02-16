package com.better.alarm.events;

import com.better.alarm.model.interfaces.Intents;

public class DismissedEvent extends AlarmEvent {
    @Override
    protected String getAction() {
        return Intents.ALARM_DISMISS_ACTION;
    }
}
