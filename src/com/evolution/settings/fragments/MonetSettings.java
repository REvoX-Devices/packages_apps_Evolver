/*
 * Copyright (C) 2022 Yet Another AOSP Project
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
package com.evolution.settings.fragments;

import android.content.ContentResolver;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.R;
import com.android.settingslib.search.SearchIndexable;

import com.evolution.settings.preference.colorpicker.ColorPickerPreference;

import java.lang.CharSequence;

import org.json.JSONException;
import org.json.JSONObject;

@SearchIndexable
public class MonetSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String OVERLAY_CATEGORY_ACCENT_COLOR =
            "android.theme.customization.accent_color";
    private static final String OVERLAY_CATEGORY_SYSTEM_PALETTE =
            "android.theme.customization.system_palette";
    private static final String OVERLAY_CATEGORY_THEME_STYLE =
            "android.theme.customization.theme_style";
    private static final String OVERLAY_COLOR_SOURCE =
            "android.theme.customization.color_source";
    private static final String OVERLAY_COLOR_BOTH =
            "android.theme.customization.color_both";
    private static final String COLOR_SOURCE_PRESET = "preset";
    private static final String COLOR_SOURCE_HOME = "home_wallpaper";
    private static final String COLOR_SOURCE_LOCK = "lock_wallpaper";

    private static final String PREF_THEME_STYLE = "theme_style";
    private static final String PREF_COLOR_SOURCE = "color_source";
    private static final String PREF_ACCENT_COLOR = "accent_color";

    private ListPreference mThemeStylePref;
    private ListPreference mColorSourcePref;
    private ColorPickerPreference mAccentColorPref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.evolution_settings_monet);

        mThemeStylePref = findPreference(PREF_THEME_STYLE);
        mColorSourcePref = findPreference(PREF_COLOR_SOURCE);
        mAccentColorPref = findPreference(PREF_ACCENT_COLOR);

        final String overlayPackageJson = Settings.Secure.getStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);
        if (overlayPackageJson != null && !overlayPackageJson.isEmpty()) {
            try {
                final JSONObject object = new JSONObject(overlayPackageJson);
                final String style = object.optString(OVERLAY_CATEGORY_THEME_STYLE, null);
                final String source = object.optString(OVERLAY_COLOR_SOURCE, null);
                final boolean both = object.optInt(OVERLAY_COLOR_BOTH, 0) == 1;
                final String color = object.optString(OVERLAY_CATEGORY_SYSTEM_PALETTE, null);
                // style handling
                boolean styleUpdated = false;
                if (style != null && !style.isEmpty()) {
                    for (CharSequence value : mThemeStylePref.getEntryValues()) {
                        if (value.toString().equals(style)) {
                            styleUpdated = true;
                            break;
                        }
                    }
                    if (styleUpdated) {
                        updateListByValue(mThemeStylePref, style);
                    }
                }
                if (!styleUpdated) {
                    updateListByValue(mThemeStylePref,
                            mThemeStylePref.getEntryValues()[0].toString());
                }
                // color handling
                final String sourceVal = (source == null || source.isEmpty() ||
                        (source.equals(COLOR_SOURCE_HOME) && both)) ? "both" : source;
                updateListByValue(mColorSourcePref, sourceVal);
                final boolean enabled = updateAccentEnablement(sourceVal);
                if (enabled && color != null && !color.isEmpty()) {
                    mAccentColorPref.setNewPreviewColor(
                            ColorPickerPreference.convertToColorInt(color));
                }
            } catch (JSONException | IllegalArgumentException ignored) {}
        }

        mThemeStylePref.setOnPreferenceChangeListener(this);
        mColorSourcePref.setOnPreferenceChangeListener(this);
        mAccentColorPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mThemeStylePref) {
            String value = (String) newValue;
            setSettingsValues(value, null, null);
            updateListByValue(mThemeStylePref, value, false);
            return true;
        } else if (preference == mColorSourcePref) {
            String value = (String) newValue;
            setSettingsValues(null, value, null);
            updateListByValue(mColorSourcePref, value, false);
            updateAccentEnablement(value);
            return true;
        } else if (preference == mAccentColorPref) {
            int value = (Integer) newValue;
            setSettingsValues(null, null, value);
            return true;
        } 
        return false;
    }

    private void updateListByValue(ListPreference pref, String value) {
        updateListByValue(pref, value, true);
    }

    private void updateListByValue(ListPreference pref, String value, boolean set) {
        if (set) pref.setValue(value);
        final int index = pref.findIndexOfValue(value);
        pref.setSummary(pref.getEntries()[index]);
    }

    private boolean updateAccentEnablement(String source) {
        final boolean shouldEnable = source != null && source.equals(COLOR_SOURCE_PRESET);
        mAccentColorPref.setEnabled(shouldEnable);
        return shouldEnable;
    }

    private void setSettingsValues(String style, String source, Integer color) {
        final ContentResolver resolver = getActivity().getContentResolver();
        final String overlayPackageJson = Settings.Secure.getStringForUser(
                resolver, Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);

        try {
            JSONObject object;
            if (overlayPackageJson == null || overlayPackageJson.isEmpty())
                object = new JSONObject();
            else
                object = new JSONObject(overlayPackageJson);

            if (style != null) {
                object.putOpt(OVERLAY_CATEGORY_THEME_STYLE, style);
            }
            if (source != null) {
                if (source.equals("both")) {
                    object.putOpt(OVERLAY_COLOR_BOTH, 1);
                    object.putOpt(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_HOME);
                } else {
                    object.remove(OVERLAY_COLOR_BOTH);
                    object.putOpt(OVERLAY_COLOR_SOURCE, source);
                }
                if (!source.equals(COLOR_SOURCE_PRESET)) {
                    object.remove(OVERLAY_CATEGORY_ACCENT_COLOR);
                    object.remove(OVERLAY_CATEGORY_SYSTEM_PALETTE);
                }
            }
            if (color != null) {
                final String rgbColor = ColorPickerPreference.convertToRGB(color).replace("#", "");
                object.putOpt(OVERLAY_CATEGORY_ACCENT_COLOR, rgbColor);
                object.putOpt(OVERLAY_CATEGORY_SYSTEM_PALETTE, rgbColor);
                object.putOpt(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_PRESET);
            }

            Settings.Secure.putStringForUser(
                    resolver, Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                    object.toString(), UserHandle.USER_CURRENT);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.EVOLVER;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.evolution_settings_monet);
}