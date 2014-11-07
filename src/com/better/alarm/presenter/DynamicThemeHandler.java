package com.better.alarm.presenter;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import com.better.alarm.R;
import com.better.alarm.presenter.alert.AlarmAlert;
import com.better.alarm.presenter.alert.AlarmAlertFullScreen;
import com.google.inject.Inject;

public class DynamicThemeHandler {
    public static final String KEY_THEME = "theme";
    public static final String DEFAULT = "default";

    private final Map<String, Map<String, Integer>> themes;
    @Inject private SharedPreferences sp;

    private class HashMapWithDefault extends HashMap<String, Integer> {
        private static final long serialVersionUID = 6169875120194964563L;

        @Override
        public Integer get(Object key) {
            Object id = super.get(key);
            if (id == null) return super.get(DEFAULT);
            else return (Integer) id;
        }

        public HashMapWithDefault(Integer defaultValue) {
            super(5);
            put(DEFAULT, defaultValue);
        }
    }

    public int getIdForName(String name) {
        String activeThemeName = sp.getString(KEY_THEME, "green");
        Map<String, Integer> activeThemeMap = themes.get(activeThemeName);
        Integer themeForName = activeThemeMap.get(name);
        return themeForName;
    }

    public DynamicThemeHandler() {
        Map<String, Integer> darkThemes = new HashMapWithDefault(R.style.DefaultDarkTheme);
        darkThemes.put(AlarmAlert.class.getName(), R.style.AlarmAlertDarkTheme);
        darkThemes.put(AlarmAlertFullScreen.class.getName(), R.style.AlarmAlertFullScreenDarkTheme);
        darkThemes.put(TimePickerDialogFragment.class.getName(), R.style.TimePickerDialogFragmentDark);

        Map<String, Integer> lightThemes = new HashMapWithDefault(R.style.DefaultLightTheme);
        lightThemes.put(AlarmAlert.class.getName(), R.style.AlarmAlertLightTheme);
        lightThemes.put(AlarmAlertFullScreen.class.getName(), R.style.AlarmAlertFullScreenLightTheme);
        lightThemes.put(TimePickerDialogFragment.class.getName(), R.style.TimePickerDialogFragmentLight);

        Map<String, Integer> greenThemes = new HashMapWithDefault(R.style.GreenTheme);
        greenThemes.put(AlarmAlert.class.getName(), R.style.AlarmAlertGreenTheme);
        greenThemes.put(AlarmAlertFullScreen.class.getName(), R.style.AlarmAlertFullScreenGreenTheme);
        greenThemes.put(TimePickerDialogFragment.class.getName(), R.style.TimePickerDialogFragmentGreen);

        themes = new HashMap<String, Map<String, Integer>>(3);
        themes.put("light", lightThemes);
        themes.put("dark", darkThemes);
        themes.put("green", greenThemes);
        // fallback
        themes.put("Light", lightThemes);
        themes.put("Dark", darkThemes);
        themes.put("Green", greenThemes);
    }

    public void setThemeFor(ContextWrapper context, Class<? extends Activity> clazz) {
        setThemeFor(context, clazz.getName());
    }

    public void setThemeFor(ContextWrapper context, String name) {
        context.setTheme(getIdForName(name));
    }
}
