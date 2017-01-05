package com.skyhookwireless.samples;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.InputFilter;
import android.text.method.DigitsKeyListener;

/**
 * Preferences management code
 */
public class Preferences extends PreferenceActivity {
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (options == null) {
            options = new Option[]{
                    new Option("Key", OptionType.TEXT, "eJwVwcENACAIBLA3w5CA5FCfIrqUcXdjq6TyoanQWXCYQ3mnJ3vtwWZFeMnIaJhZd78PD04LLQ"),
                    new Option(WpsApiTest.SERVER_URL_KEY, OptionType.TEXT, null),
                    new Option("Local File Path", OptionType.TEXT, null),
                    new Option("Period", OptionType.NONNEGATIVE_INTEGER, null),
                    new Option("Iterations", OptionType.NONNEGATIVE_INTEGER, null),
                    new Option("Desired XPS Accuracy", OptionType.NONNEGATIVE_INTEGER, null),
                    new Option("Tiling Path", OptionType.TEXT, getFilesDir().getAbsolutePath()),
                    new Option("Max Data Per Session", OptionType.NONNEGATIVE_INTEGER, null),
                    new Option("Max Data Total", OptionType.NONNEGATIVE_INTEGER, null),
                    new ListOption("Street Address Lookup", OptionType.LIST, null, new String[]{"None", "Limited", "Full"})
            };
        }

        setPreferenceScreen(createRootPreferenceScreen());
    }

    private PreferenceScreen createRootPreferenceScreen() {
        final PreferenceScreen root =
                getPreferenceManager().createPreferenceScreen(this);

        final PreferenceCategory category = new PreferenceCategory(this);
        category.setTitle("WpsApiTest Settings");
        root.addPreference(category);

        for (final Option option : options) {
            Preference setting;
            switch (option.type) {
                case CHECKBOX: {
                    setting = new CheckBoxPreference(this);
                    break;
                }
                case LIST: {
                    if (option instanceof ListOption) {
                        final ListPreference listSetting = new ListPreference(this);
                        final String[] entries = ((ListOption) option).entries;
                        listSetting.setEntries(entries);
                        listSetting.setEntryValues(entries);
                        setting = listSetting;
                        break;
                    }
                }
                default: {
                    final EditTextPreference textSetting = new EditTextPreference(this);
                    textSetting.getEditText().setSingleLine();
                    textSetting.getEditText().setFilters(new InputFilter[]{
                            new InputFilter.LengthFilter(512)
                    });

                    if (option.type == OptionType.NONNEGATIVE_INTEGER)
                        textSetting.getEditText()
                                .setKeyListener(new DigitsKeyListener(false, false));
                    setting = textSetting;
                }
            }

            setting.setKey(option.name);
            setting.setTitle(option.name);
            if (option.defaultValue != null)
                setting.setDefaultValue(option.defaultValue);

            category.addPreference(setting);
        }

        return root;
    }

    private enum OptionType {
        TEXT,
        NONNEGATIVE_INTEGER,
        LIST,
        CHECKBOX;
    }

    private class Option {
        private Option(final String name, final OptionType type, final Object defaultValue) {
            super();
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        String name;
        OptionType type;
        Object defaultValue;
    }

    private class ListOption extends Option {
        private ListOption(final String name, final OptionType type, final Object defaultValue, final String[] entries) {
            super(name, type, defaultValue);
            this.entries = entries;
        }

        String[] entries;
    }

    private static Option[] options = null;
}
