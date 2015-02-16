package com.better.alarm.events;


public class AlarmUnscheduledEvent extends AlarmEvent {
    @Override
    protected String getAction() {
        throw new UnsupportedOperationException();
    }
}
