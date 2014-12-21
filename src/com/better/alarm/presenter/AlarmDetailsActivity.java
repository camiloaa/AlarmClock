/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.better.alarm.presenter;

import java.util.Calendar;

import javax.inject.Inject;

import roboguice.RoboGuice;
import roboguice.activity.RoboPreferenceActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectPreference;
import roboguice.inject.InjectView;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.better.alarm.R;
import com.better.alarm.model.interfaces.Alarm;
import com.better.alarm.model.interfaces.AlarmEditor;
import com.better.alarm.model.interfaces.AlarmNotFoundException;
import com.better.alarm.model.interfaces.IAlarmsManager;
import com.better.alarm.model.interfaces.Intents;
import com.better.alarm.view.AlarmPreference;
import com.better.alarm.view.RepeatPreference;
import com.github.androidutils.logger.Logger;

/**
 * Manages each alarm
 */
@ContentView(R.layout.details_activity)
public class AlarmDetailsActivity extends RoboPreferenceActivity implements Preference.OnPreferenceChangeListener,
        OnCancelListener, TimePickerDialogFragment.AlarmTimePickerDialogHandler {
    public final static String M12 = "h:mm aa";
    public final static String M24 = "kk:mm";
    @Inject private IAlarmsManager alarms;
    @Inject private Logger logger;
    private EditText mLabel;
    @InjectPreference("enabled") private CheckBoxPreference mEnabledPref;
    @InjectPreference("time") private Preference mTimePref;
    @InjectPreference("alarm") private AlarmPreference mAlarmPref;
    @InjectPreference("setRepeat") private RepeatPreference mRepeatPref;
    @InjectPreference("prealarm") private CheckBoxPreference mPreAlarmPref;

    private Alarm alarm;
    private boolean isNewAlarm;
    // these are to get data from TimerPicker
    private int mHour;
    private int mMinute;
    private TimePickerDialog mTimePickerDialog;

    @Inject private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle icicle) {
        RoboGuice.getInjector(this).getInstance(DynamicThemeHandler.class)
                .setThemeFor(this, AlarmDetailsActivity.class);
        super.onCreate(icicle);

        if (!getResources().getBoolean(R.bool.isTablet)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        // TODO Stop using preferences for this view. Save on done, not after
        // each change.
        addPreferencesFromResource(R.xml.alarm_details);

        mLabel = (EditText) getLayoutInflater().inflate(R.layout.details_label, null);
        ((ListView) findViewById(android.R.id.list)).addFooterView(mLabel);

        // Get each preference so we can retrieve the value later.
        // remove enable preference from screen
        getPreferenceScreen().removePreference(mEnabledPref);
        mAlarmPref.setOnPreferenceChangeListener(this);
        mRepeatPref.setOnPreferenceChangeListener(this);
        mPreAlarmPref.setOnPreferenceChangeListener(this);

        Intent intent = getIntent();
        String action = intent.getAction();
        if (intent.hasExtra(Intents.EXTRA_ID)) {
            editExistingAlarm(intent);
        } else {
            createNewDefaultAlarm(intent);
        }

        // Populate the prefs with the original alarm data. updatePrefs also
        // sets mId so it must be called before checking mId below.
        updatePrefs();

        // We have to do this to get the save/cancel buttons to highlight on
        // their own.
        getListView().setItemsCanFocus(true);

        // Attach actions to each button.
        Button b = (Button) findViewById(R.id.details_activity_button_save);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAlarm();
                finish();
            }
        });
        Button revert = (Button) findViewById(R.id.details_activity_button_revert);
        revert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                revert();
                finish();
            }
        });
        if (isNewAlarm) {
            TimePickerDialogFragment.showTimePicker(getFragmentManager());
        }
    }

    private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            refreshPrealarmVisibilityAndSummary();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        sp.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
        refreshPrealarmVisibilityAndSummary();
    }

    private void refreshPrealarmVisibilityAndSummary() {
        int duration = Integer.parseInt(sp.getString("prealarm_duration", "-1"));
        if (duration == -1) {
            getPreferenceScreen().removePreference(mPreAlarmPref);
        } else {
            getPreferenceScreen().addPreference(mPreAlarmPref);
            mPreAlarmPref.setSummaryOff(R.string.prealarm_off_summary);
            mPreAlarmPref.setSummaryOn(getResources().getString(R.string.prealarm_summary, duration));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(
                onSharedPreferenceChangeListener);
    }

    /**
     * Edit an existing alarm. Id of the alarm is specified in
     * {@link Intents#EXTRA_ID} as int. To get it use
     * {@link Intent#getIntExtra(String, int)}.
     */
    private void editExistingAlarm(Intent intent) {
        int mId = intent.getIntExtra(Intents.EXTRA_ID, -1);
        try {
            alarm = alarms.getAlarm(mId);
        } catch (AlarmNotFoundException e) {
            logger.d("Alarm not found");
            finish();
        }
    }

    /**
     * A new alarm has to be created.
     */
    private void createNewDefaultAlarm(Intent intent) {
        // No alarm means create a new alarm.
        alarm = alarms.createNewAlarm();
        isNewAlarm = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // we inflate anyway even though currently we have only one entry. There
        // may be more in future
        getMenuInflater().inflate(R.menu.details_menu, menu);
        if (isNewAlarm) {
            menu.removeItem(R.id.set_alarm_menu_delete_alarm);
        }
        getActionBar().setDisplayHomeAsUpEnabled(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.set_alarm_menu_delete_alarm:
            deleteAlarm();
            break;

        case android.R.id.home:
            Intent parentActivityIntent = new Intent(this, AlarmsListActivity.class);
            parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(parentActivityIntent);
            finish();
            return true;

        default:
            break;
        }
        return true;
    }

    /*
     * @Override protected void onSaveInstanceState(Bundle outState) {
     * super.onSaveInstanceState(outState);
     * outState.putParcelable(KEY_ORIGINAL_ALARM, mOriginalAlarm);
     * outState.putParcelable(KEY_CURRENT_ALARM, buildAlarmFromUi()); if
     * (mTimePickerDialog != null) { if (mTimePickerDialog.isShowing()) {
     * outState.putParcelable(KEY_TIME_PICKER_BUNDLE,
     * mTimePickerDialog.onSaveInstanceState()); mTimePickerDialog.dismiss(); }
     * mTimePickerDialog = null; } }
     * 
     * @Override protected void onRestoreInstanceState(Bundle state) {
     * super.onRestoreInstanceState(state);
     * 
     * Alarm alarmFromBundle = state.getParcelable(KEY_ORIGINAL_ALARM); if
     * (alarmFromBundle != null) { mOriginalAlarm = alarmFromBundle; }
     * 
     * alarmFromBundle = state.getParcelable(KEY_CURRENT_ALARM); if
     * (alarmFromBundle != null) { updatePrefs(alarmFromBundle); }
     * 
     * Bundle b = state.getParcelable(KEY_TIME_PICKER_BUNDLE); if (b != null) {
     * showTimePicker(); mTimePickerDialog.onRestoreInstanceState(b); } }
     */

    @Override
    public boolean onPreferenceChange(final Preference p, Object newValue) {
        // this method is called _before_
        // the value of the preference has changed.
        // Editing any preference (except enable) enables the alarm.
        if (p != mEnabledPref) {
            mEnabledPref.setChecked(true);
        }
        return true;
    }

    private void updatePrefs() {
        mLabel.setText(alarm.getLabel());
        mEnabledPref.setChecked(alarm.isEnabled());
        mHour = alarm.getHour();
        mMinute = alarm.getMinutes();
        mRepeatPref.setDaysOfWeek(alarm.getDaysOfWeek());
        // Give the alert uri to the preference.
        mAlarmPref.setAlert(alarm.getAlert());
        mPreAlarmPref.setChecked(alarm.isPrealarm());
        updateTime();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mTimePref) {
            TimePickerDialogFragment.showTimePicker(getFragmentManager());
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onBackPressed() {
        revert();
        finish();
    }

    @Override
    public void onDialogTimeSet(int hourOfDay, int minute) {
        // onTimeSet is called when the user clicks "Set"
        mHour = hourOfDay;
        mMinute = minute;
        updateTime();
        // If the time has been changed, enable the alarm.
        mEnabledPref.setChecked(true);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        mTimePickerDialog = null;
    }

    private void updateTime() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, mHour);
        c.set(Calendar.MINUTE, mMinute);
        String format = android.text.format.DateFormat.is24HourFormat(this) ? M24 : M12;
        CharSequence summary = c == null ? "" : (String) DateFormat.format(format, c);
        mTimePref.setSummary(summary);
    }

    private void saveAlarm() {
        AlarmEditor editor = alarm.edit();
        //@formatter:off
        editor.setEnabled(mEnabledPref.isChecked())
              .setHour(mHour)
              .setMinutes(mMinute)
              .setDaysOfWeek(mRepeatPref.getDaysOfWeek())
              .setVibrate(true)
              .setLabel(mLabel.getText().toString())
              .setAlert(mAlarmPref.getAlert())
              .setPrealarm(mPreAlarmPref.isChecked());
        //@formatter:on
        editor.commit();
        isNewAlarm = false;
    }

    private void deleteAlarm() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.delete_alarm))
                .setMessage(getString(R.string.delete_alarm_confirm))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        alarms.delete(alarm);
                        finish();
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void revert() {
        // "Revert" on a newly created alarm should delete it.
        if (isNewAlarm) {
            alarms.delete(alarm);
        } else {
            // do not save changes
        }
    }
}
