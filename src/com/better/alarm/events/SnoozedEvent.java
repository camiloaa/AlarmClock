package com.better.alarm.events;

import com.better.alarm.model.interfaces.Intents;

public class SnoozedEvent extends AlarmEvent {
    @Override
    protected String getAction() {
        return Intents.ALARM_SNOOZE_ACTION;
    }
}
