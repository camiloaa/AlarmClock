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

import javax.inject.Inject;

import roboguice.RoboGuice;
import roboguice.activity.RoboActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.better.alarm.R;
import com.better.alarm.model.interfaces.Alarm;
import com.better.alarm.model.interfaces.Intents;
import com.better.alarm.presenter.AlarmsListFragment.ShowDetailsStrategy;
import com.better.alarm.presenter.TimePickerDialogFragment.AlarmTimePickerDialogHandler;

/**
 * This activity displays a list of alarms and optionally a details fragment.
 */
@ContentView(R.layout.list_activity)
public class AlarmsListActivity extends RoboActivity implements AlarmTimePickerDialogHandler {
    @Inject private ActionBarHandler mActionBarHandler;
    @Inject private SharedPreferences sp;
    private AlarmsListFragment alarmsListFragment;
    @InjectResource(R.bool.isTablet) private boolean isTablet;
    @InjectView(R.id.list_activity_info_fragment) View nextAlarmFragment;

    private Alarm timePickerAlarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        RoboGuice.getInjector(this).getInstance(DynamicThemeHandler.class).setThemeFor(this, AlarmsListActivity.class);
        super.onCreate(savedInstanceState);
        if (!isTablet) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        alarmsListFragment = (AlarmsListFragment) getFragmentManager().findFragmentById(
                R.id.list_activity_list_fragment);
        alarmsListFragment.setShowDetailsStrategy(showDetailsInActivityFragment);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (sp.getBoolean("show_info_fragment", false)) {
            nextAlarmFragment.setVisibility(View.VISIBLE);
        } else {
            nextAlarmFragment.setVisibility(View.GONE);
        }
    }

    public AlarmsListActivity() {
        super();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return mActionBarHandler.onCreateOptionsMenu(menu, getMenuInflater(), getActionBar());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_item_add_alarm) {
            showDetailsInActivityFragment.showDetails(null);
            return true;
        } else return mActionBarHandler.onOptionsItemSelected(item);
    }

    // private final ShowDetailsStrategy showDetailsInFragmentStrategy = new
    // ShowDetailsStrategy() {
    //
    // @Override
    // public void showDetails(Alarm alarm) {
    // Intent intent = new Intent();
    // intent.putExtra(Intents.EXTRA_ID, alarm.getId());
    //
    // // Check what fragment is currently shown, replace if needed.
    // AlarmDetailsFragment details = (AlarmDetailsFragment)
    // getFragmentManager().findFragmentById(
    // R.id.alarmsDetailsFragmentFrame);
    // if (details == null || details.getIntent() != intent) {
    // // Make new fragment to show this selection.
    // details = AlarmDetailsFragment.newInstance(intent);
    //
    // // Execute a transaction, replacing any existing fragment
    // // with this one inside the frame.
    // FragmentTransaction ft = getFragmentManager().beginTransaction();
    // ft.replace(R.id.alarmsDetailsFragmentFrame, details);
    // ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    // ft.commit();
    // }
    // }
    // };

    private final ShowDetailsStrategy showDetailsInActivityFragment = new ShowDetailsStrategy() {
        @Override
        public void showDetails(Alarm alarm) {
            Intent intent = new Intent(AlarmsListActivity.this, AlarmDetailsActivity.class);
            if (alarm != null) {
                intent.putExtra(Intents.EXTRA_ID, alarm.getId());
            }
            startActivity(intent);
        }
    };

    public void showTimePicker(Alarm alarm) {
        timePickerAlarm = alarm;
        TimePickerDialogFragment.showTimePicker(getFragmentManager());
    }

    @Override
    public void onDialogTimeSet(int hourOfDay, int minute) {
        timePickerAlarm.edit().setEnabled(true).setHour(hourOfDay).setMinutes(minute).commit();
        // this must be invoked synchronously on the Pickers's OK button onClick
        // otherwise fragment is closed too soon and the time is not updated
        alarmsListFragment.updateAlarmsList();
    }
}
