package com.better.alarm.events;

import android.content.Intent;

import com.better.alarm.model.interfaces.Intents;

public class AlarmSetEvent extends AlarmEvent {

    public long millis;

    @Override
    public Intent toIntent() {
        Intent intent = new Intent(getAction());
        intent.putExtra(Intents.EXTRA_ID, id);
        intent.putExtra(Intents.EXTRA_NEXT_NORMAL_TIME_IN_MILLIS, millis);
        return intent;
    }

    @Override
    protected String getAction() {
        return Intents.ACTION_ALARM_SET;
    }
}
