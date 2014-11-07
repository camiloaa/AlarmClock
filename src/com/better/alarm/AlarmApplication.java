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
package com.better.alarm;

import java.io.File;
import java.lang.reflect.Field;

import org.acra.ReportField;
import org.acra.annotation.ReportsCrashes;

import roboguice.RoboGuice;
import android.app.Application;
import android.content.Context;
import android.preference.PreferenceManager;
import android.view.ViewConfiguration;

import com.better.alarm.events.BusImpl;
import com.better.alarm.events.IBus;
import com.better.alarm.model.Alarms;
import com.better.alarm.model.AlarmsScheduler;
import com.better.alarm.model.IAlarmsScheduler;
import com.better.alarm.model.interfaces.IAlarmsManager;
import com.better.alarm.presenter.DynamicThemeHandler;
import com.better.alarm.presenter.alert.AlarmAlertReceiver;
import com.better.alarm.presenter.background.KlaxonPresenter;
import com.better.alarm.presenter.background.ScheduledPresenter;
import com.better.alarm.presenter.background.ToastPresenter;
import com.better.alarm.presenter.background.VibrationPresenter;
import com.github.androidutils.logger.FileLogWriter;
import com.github.androidutils.logger.LogcatLogWriterWithLines;
import com.github.androidutils.logger.Logger;
import com.github.androidutils.logger.LoggingExceptionHandler;
import com.github.androidutils.logger.StartupLogWriter;
import com.github.androidutils.wakelock.WakeLockManager;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;

// @formatter:off
@ReportsCrashes(
        formKey = "dFFwNk1yQ1lXeFNpVG1YVE1OZ05YNVE6MQ",
        applicationLogFileLines = 150,
        customReportContent = {
                ReportField.IS_SILENT,
                ReportField.APP_VERSION_CODE,
                ReportField.PHONE_MODEL,
                ReportField.DEVICE_ID,
                ReportField.ANDROID_VERSION,
                ReportField.CUSTOM_DATA,
                ReportField.LOGCAT,
                ReportField.STACK_TRACE,
                ReportField.SHARED_PREFERENCES,
                })
// @formatter:on
public class AlarmApplication extends Application {
    @Override
    public void onCreate() {
        RoboGuice.setUseAnnotationDatabases(false);

        final WakeLockManager wakeLockManager = new WakeLockManager(this, new Logger(), false);

        Injector injector = RoboGuice.getOrCreateBaseApplicationInjector(this, RoboGuice.DEFAULT_STAGE,
                Modules.override(RoboGuice.newDefaultRoboModule(this)).with(new Module() {
                    @Override
                    public void configure(Binder binder) {
                        binder.bind(Logger.class).in(Scopes.SINGLETON);
                        binder.bind(Alarms.class).in(Scopes.SINGLETON);
                        binder.bind(IAlarmsManager.class).to(Alarms.class).in(Scopes.SINGLETON);
                        binder.bind(WakeLockManager.class).toInstance(wakeLockManager);
                        binder.bind(IBus.class).to(BusImpl.class).in(Scopes.SINGLETON);
                        binder.bind(AlarmsScheduler.class).in(Scopes.SINGLETON);
                        binder.bind(IAlarmsScheduler.class).to(AlarmsScheduler.class).in(Scopes.SINGLETON);
                        binder.bind(DynamicThemeHandler.class).in(Scopes.SINGLETON);
                    }
                }));

        // The following line triggers the initialization of ACRA
        // ACRA.init(this);

        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception ex) {
            // Ignore
        }

        // ACRA.getErrorReporter().setExceptionHandlerInitializer(new
        // ExceptionHandlerInitializer() {
        // @Override
        // public void initializeExceptionHandler(ErrorReporter reporter) {
        // reporter.putCustomData("STARTUP_LOG",
        // StartupLogWriter.getInstance().getMessagesAsString());
        // }
        // });
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        Logger logger = injector.getInstance(Logger.class);
        logger.addLogWriter(LogcatLogWriterWithLines.getInstance());
        logger.addLogWriter(FileLogWriter.getInstance(this, false));
        logger.addLogWriter(StartupLogWriter.getInstance());
        LoggingExceptionHandler.addLoggingExceptionHandlerToAllThreads(logger);

        deleteLogs(logger, getApplicationContext());

        injector.getInstance(DynamicThemeHandler.class).setThemeFor(this, DynamicThemeHandler.DEFAULT);

        injector.getInstance(AlarmsScheduler.class).init();
        injector.getInstance(Alarms.class).init();

        injector.getInstance(ToastPresenter.class).init();
        injector.getInstance(VibrationPresenter.class).init();
        injector.getInstance(ScheduledPresenter.class).init();
        injector.getInstance(KlaxonPresenter.class).init();
        injector.getInstance(AlarmAlertReceiver.class).init();

        logger.d("onCreate");
        super.onCreate();
    }

    private void deleteLogs(Logger logger, Context context) {
        final File logFile = new File(context.getFilesDir(), "applog.log");
        if (logFile.exists()) {
            logFile.delete();
            logger.d("Deleted log file");
        } else {
            logger.d("Log file was already deleted");
        }
    }
}
