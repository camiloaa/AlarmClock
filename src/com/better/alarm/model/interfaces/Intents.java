/*
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.better.alarm.model.interfaces;

import javax.inject.Inject;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.better.alarm.events.AlarmEvent;
import com.better.alarm.model.AlarmsService;

public class Intents {
    @Inject Context context;
    /**
     * Key of the AlarmCore attached as a parceble extra
     */
    public static final String EXTRA_ID = "intent.extra.alarm";
    public static final String EXTRA_EVENT_CLASS = "intent.extra.event.class";
    public static final String ACTION_ALARM_EVENT = "com.better.alarm.ACTION_ALARM_EVENT";

    public PendingIntent createPendingIntent(Class<? extends AlarmEvent> clazz, int id) {
        // Action must be different for every event, otherwise android will not
        // see the difference and will replace older intent
        Intent intent = new Intent(ACTION_ALARM_EVENT + "$" + clazz.getCanonicalName() + "@" + id);
        intent.putExtra(Intents.EXTRA_ID, id);
        intent.putExtra(Intents.EXTRA_EVENT_CLASS, clazz.getCanonicalName());
        intent.setClass(context, AlarmsService.class);
        return PendingIntent.getService(context, id, intent, 0);
    }
}
