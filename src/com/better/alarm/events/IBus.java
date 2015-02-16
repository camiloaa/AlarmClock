package com.better.alarm.events;

public interface IBus {
    public void post(Object event);

    public void register(Object listener);

    public void unregister(Object listener);
}
