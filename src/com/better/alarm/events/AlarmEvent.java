package com.better.alarm.events;

import android.content.Intent;

import com.better.alarm.model.interfaces.Alarm;
import com.better.alarm.model.interfaces.Intents;

public abstract class AlarmEvent {
    public int id;
    public Alarm alarm;
    private final Class<? extends AlarmEvent> eventClass = this.getClass();

    public Alarm getAlarm() {
        return alarm;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [id=" + id + ", alarm=" + alarm + "]";
    }

    public AlarmEvent setId(int id) {
        this.id = id;
        return this;
    }

    public AlarmEvent setAlarm(Alarm alarm) {
        setId(alarm.getId());
        this.alarm = alarm;
        return this;
    }

    public Class<?> getEventClass() {
        return this.getClass();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((alarm == null) ? 0 : alarm.hashCode());
        result = prime * result + ((eventClass == null) ? 0 : eventClass.hashCode());
        result = prime * result + id;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        AlarmEvent other = (AlarmEvent) obj;
        if (alarm == null) {
            if (other.alarm != null) return false;
        } else if (!alarm.equals(other.alarm)) return false;
        if (eventClass == null) {
            if (other.eventClass != null) return false;
        } else if (!eventClass.equals(other.eventClass)) return false;
        if (id != other.id) return false;
        return true;
    }

    public Intent toIntent() {
        Intent intent = new Intent(getAction());
        intent.putExtra(Intents.EXTRA_ID, id);
        return intent;
    }

    protected abstract String getAction();
}
