package com.better.alarm.model.persistance;

import javax.inject.Inject;

import roboguice.service.RoboIntentService;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;

import com.better.alarm.model.interfaces.Intents;
import com.github.androidutils.wakelock.WakeLockManager;

public class DataBaseService extends RoboIntentService {

    public static final String SAVE_ALARM_ACTION = "com.better.alarm.ACTION_SAVE_ALARM";

    @Inject private ContentResolver mContentResolver;
    @Inject private WakeLockManager wakelocks;

    public DataBaseService() {
        super("DataBaseService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getAction().equals(SAVE_ALARM_ACTION)) {
            int id = intent.getIntExtra(Intents.EXTRA_ID, -1);
            ContentValues values = intent.getParcelableExtra("extra_values");
            Uri uriWithAppendedId = ContentUris.withAppendedId(AlarmContainer.Columns.CONTENT_URI, id);
            mContentResolver.update(uriWithAppendedId, values, null, null);
            wakelocks.releasePartialWakeLock(intent);
        }
    }
}
