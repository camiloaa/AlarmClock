package com.better.alarm.events;

import com.better.alarm.model.interfaces.Alarm;

public class AlarmEvent {
    public int id;
    public Alarm alarm;

    public Alarm getAlarm() {
        return alarm;
    }
}
