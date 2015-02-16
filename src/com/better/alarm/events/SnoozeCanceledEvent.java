package com.better.alarm.events;

import com.better.alarm.model.interfaces.Intents;

public class SnoozeCanceledEvent extends AlarmEvent {
    @Override
    protected String getAction() {
        return Intents.ACTION_CANCEL_SNOOZE;
    }
}
