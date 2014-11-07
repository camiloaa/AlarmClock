package com.better.alarm.events;

import android.os.Handler;

import com.squareup.otto.Bus;

public class BusImpl implements IBus {
    private final Bus bus = new Bus();
    private final Handler handler = new Handler();

    @Override
    public void post(final Object event) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                bus.post(event);
            }
        });
    }

    @Override
    public void register(Object listener) {
        bus.register(listener);
    }

    @Override
    public void unregister(Object listener) {
        bus.unregister(listener);
    }
}
