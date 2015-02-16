package com.better.alarm.events;

public class AlarmSceduledEvent extends AlarmEvent {

    public long nextNormalTimeInMillis;
    public boolean isPrealarm;

    @Override
    protected String getAction() {
        throw new UnsupportedOperationException();
    }

}
