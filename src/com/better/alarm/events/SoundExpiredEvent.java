package com.better.alarm.events;

import com.better.alarm.model.interfaces.Intents;

public class SoundExpiredEvent extends AlarmEvent {
    @Override
    protected String getAction() {
        return Intents.ACTION_SOUND_EXPIRED;
    }
}
