package com.skyhookwireless.samples.source;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.skyhookwireless.wps.IPLocation;
import com.skyhookwireless.wps.IPLocationCallback;
import com.skyhookwireless.wps.Location;
import com.skyhookwireless.wps.WPSCertifiedLocationCallback;
import com.skyhookwireless.wps.WPSContinuation;
import com.skyhookwireless.wps.WPSLocation;
import com.skyhookwireless.wps.WPSLocationCallback;
import com.skyhookwireless.wps.WPSPeriodicLocationCallback;
import com.skyhookwireless.wps.WPSReturnCode;
import com.skyhookwireless.wps.WPSStreetAddressLookup;
import com.skyhookwireless.wps.XPS;

public class WpsApiTest
    extends Activity
    implements OnSharedPreferenceChangeListener
{
    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // create the XPS instance, passing in our Context
        _xps = new XPS(this);
        _stop = false;

        // listen for settings changes
        final SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        // read existing preferences
        onSharedPreferenceChanged(preferences, "Local File Path");
        onSharedPreferenceChanged(preferences, "Period");
        onSharedPreferenceChanged(preferences, "Iterations");
        onSharedPreferenceChanged(preferences, "Desired XPS Accuracy");
        onSharedPreferenceChanged(preferences, "Street Address Lookup");
        _tilingPath = preferences.getString("Tiling Path", "");
        _maxDataSizePerSession = Long.valueOf(preferences.getString("Max Data Per Session", "0"));
        _maxDataSizeTotal = Long.valueOf(preferences.getString("Max Data Total", "0"));
        final String serverUrl = preferences.getString(SERVER_URL_KEY, "");
        _xps.setTiling(_tilingPath,
                       _maxDataSizePerSession,
                       _maxDataSizeTotal,
                       null);

        if (serverUrl.length() > 0)
            XPS.setServerUrl(serverUrl);

        // set the UI layout
        _buttonLayout = new LinearLayout(this);
        _buttonLayout.setOrientation(LinearLayout.VERTICAL);
        setContentView(_buttonLayout);

        // initialize the Handler which will display location data
        // in the text view. we use a Handler because UI updates
        // must occur in the UI thread
        setUIHandler();

        // display the buttons.
        // _viewsToDisable is a list of views
        // which should be disabled when WPS is active
        _buttons = new LinearLayout(this);
        _buttons.setOrientation(LinearLayout.VERTICAL);

        _viewsToDisable.clear();
        _viewsToDisable.add(addSettingsButton(_buttons));
        _viewsToDisable.add(addIPLocationButton(_buttons));
        _viewsToDisable.add(addWPSLocationButton(_buttons));
        _viewsToDisable.add(addWPSPeriodicLocationButton(_buttons));
        _viewsToDisable.add(addXPSLocationButton(_buttons));
        _viewsToDisable.add(addWPSCertifiedLocationButton(_buttons));
        _stopButton = addStopButton(_buttons);
        addAbortButton(_buttons);
        deactivateStopButton();

        // Set the buttons within a ScrollView so that the user can scroll through
        // the buttons if there total length extends into the Location Display
        _scrollingButtons = new ScrollView(this);
        _scrollingButtons.addView(_buttons);
        _tv = new TextView(this);
        _tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        _scrollingText = new ScrollView(this);
        _scrollingText.addView(_tv);

        // Set up the overall layout so that the buttons take up 70 percent of the
        // available screen and the Location display takes up the remaining 30.

        _buttonLayout.addView(_scrollingButtons,
                              new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                                                            0,
                                                            0.8f ));

        // create the location layout
        _buttonLayout.addView(_scrollingText,
                              new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                                                            0,
                                                            0.2f));

        setKey(preferences.getString("Key", ""));

        notifyLocationSetting();
        notifyAlwaysAllowScanning();
    }

    @Override
    public void onPause()
    {
        super.onPause();

        // make sure WPS is stopped
        _xps.abort();
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
    }

    private LinearLayout _buttonLayout;
    private LinearLayout _buttons;
    private ScrollView _scrollingButtons;
    private ScrollView _scrollingText;
    private final ArrayList<View> _viewsToDisable = new ArrayList<View>();
    private Button _stopButton;
    private boolean _stop;

    // add the 'Settings' button which leads to all the
    // WPS settings.
    private Button addSettingsButton(final ViewGroup layout)
    {
        final Button settingsButton = new Button(this);
        settingsButton.setText("Settings");
        settingsButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                final Intent launchPreferencesIntent =
                    new Intent().setClass(WpsApiTest.this, Preferences.class);
                startActivity(launchPreferencesIntent);
            }
        });
        layout.addView(settingsButton);
        return settingsButton;
    }

    /**
     * A single callback class that will be used to handle
     * all location notifications sent by WPS to our app.
     */
    private class MyLocationCallback
        implements IPLocationCallback,
                   WPSLocationCallback,
                   WPSPeriodicLocationCallback,
                   WPSCertifiedLocationCallback
    {
        public void done()
        {
            // tell the UI thread to re-enable the buttons
            _handler.sendMessage(_handler.obtainMessage(DONE_MESSAGE));
        }

        public WPSContinuation handleError(final WPSReturnCode error)
        {
            // send a message to display the error
            _handler.sendMessage(_handler.obtainMessage(ERROR_MESSAGE,
                                                        error));
            // return WPS_STOP if the user pressed the Stop button
            if (! _stop)
                return WPSContinuation.WPS_CONTINUE;
            else
                return WPSContinuation.WPS_STOP;
        }

        public void handleIPLocation(final IPLocation location)
        {
            // send a message to display the location
            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
                                                        location));
        }

        public void handleWPSLocation(final WPSLocation location)
        {
            // send a message to display the location
            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
                                                        location));
        }

        public WPSContinuation handleWPSPeriodicLocation(final WPSLocation location)
        {
            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
                                                        location));
            // return WPS_STOP if the user pressed the Stop button
            if (! _stop)
                return WPSContinuation.WPS_CONTINUE;
            else
                return WPSContinuation.WPS_STOP;
        }

        public WPSContinuation handleWPSCertifiedLocation(final WPSLocation[] locations)
        {
            _handler.sendMessage(_handler.obtainMessage(LOCATION_LIST_MESSAGE,
                                                        locations));
            // return WPS_STOP if the user pressed the Stop button
            if (! _stop)
                return WPSContinuation.WPS_CONTINUE;
            else
                return WPSContinuation.WPS_STOP;
        }
    }

    private final MyLocationCallback _callback = new MyLocationCallback();

    private Button addIPLocationButton(final ViewGroup layout)
    {
        final Button ipLocationButton = new Button(this);
        ipLocationButton.setText("Get IP Location");

        ipLocationButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                activateStopButton();
                _tv.setText("");
                _xps.getIPLocation(null,
                                   _streetAddressLookup,
                                   _callback);
            }
        });
        layout.addView(ipLocationButton);
        return ipLocationButton;
    }

    private Button addWPSPeriodicLocationButton(final ViewGroup layout)
    {
        final Button wpsLocationButton = new Button(this);
        wpsLocationButton.setText("Get WPS Periodic Location");

        wpsLocationButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                activateStopButton();
                _tv.setText("");
                _xps.getPeriodicLocation(null,
                                         _streetAddressLookup,
                                         _period,
                                         _iterations,
                                         _callback);

            }
        });
        layout.addView(wpsLocationButton);
        return wpsLocationButton;
    }

    private Button addWPSLocationButton(final ViewGroup layout)
    {
        final Button wpsLocationButton = new Button(this);
        wpsLocationButton.setText("Get WPS Location");

        wpsLocationButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                activateStopButton();
                _tv.setText("");
                _xps.getLocation(null,
                                 _streetAddressLookup,
                                 _callback);
            }
        });
        layout.addView(wpsLocationButton);
        return wpsLocationButton;
    }

    private Button addXPSLocationButton(final ViewGroup layout)
    {
        final Button xpsLocationButton = new Button(this);
        xpsLocationButton.setText("Get XPS Location");
        xpsLocationButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                activateStopButton();
                _tv.setText("");
                _xps.getXPSLocation(null,
                                    // note we convert _period to seconds
                                    (int) (_period / 1000),
                                    _desiredXpsAccuracy,
                                    _callback);
            }
        });
        layout.addView(xpsLocationButton);
        return xpsLocationButton;
    }

    private Button addWPSCertifiedLocationButton(final ViewGroup layout)
    {
        final Button button = new Button(this);
        button.setText("Get XPS Certified Location");

        button.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                activateStopButton();
                _tv.setText("");
                _xps.getCertifiedLocation(null, _streetAddressLookup, _callback);
            }
        });
        layout.addView(button);
        return button;
    }

    /**
     * Starting with API version 18, Android supports Wi-Fi scanning when Wi-Fi is
     * disabled for connectivity purposes.
     *
     * To improve location accuracy we encourage users to enable this feature.
     * The code below provides a simple example where we use Android's built
     * in dialog to notify the user.
     */
    private void notifyAlwaysAllowScanning()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
        {
            final WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

            if (! wifi.isWifiEnabled() && ! wifi.isScanAlwaysAvailable())
            {
                final Intent intent =
                    new Intent(WifiManager.ACTION_REQUEST_SCAN_ALWAYS_AVAILABLE);
                startActivityForResult(intent, 1);
            }
        }
    }

    private void notifyLocationSetting()
    {
        final LocationManager locationManager =
            (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        final boolean networkEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        final boolean gpsEnabled =
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (! networkEnabled && ! gpsEnabled)
            displayLocationDialog("Location services disabled",
                                  "Location services have been disabled. Please enable location.");
        else if (! networkEnabled)
            displayLocationDialog("Location performance",
                                  "Location performance will be improved by enabling network based location.");
        else if (! gpsEnabled)
            displayLocationDialog("Location performance",
                                  "Location performance will be improved by enabling gps based location.");
    }

    private void displayLocationDialog(final String title, final String message)
    {
        final DialogInterface.OnClickListener launchSettings =
            new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    final Intent locationSettingIntent =
                        new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(locationSettingIntent);
                }
            };

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
               .setMessage(message)
               .setPositiveButton("Settings", launchSettings)
               .setNegativeButton("Cancel", null)
               .create()
               .show();
    }

    private Button addStopButton(final ViewGroup layout)
    {
        final Button stopButton = new Button(this);
        stopButton.setText("Stop");
        stopButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                _stop = true;
                stopButton.setEnabled(false);
            }
        });
        layout.addView(stopButton);
        return stopButton;
    }

    private Button addAbortButton(final ViewGroup layout)
    {
        final Button abortButton = new Button(this);
        abortButton.setText("Abort");
        abortButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                _xps.abort();
            }
        });
        layout.addView(abortButton);
        return abortButton;
    }

    private void activateStopButton()
    {
        for (final View view : _viewsToDisable)
        {
            view.setEnabled(false);
        }
        _stopButton.setEnabled(true);
    }

    private void deactivateStopButton()
    {
        for (final View view : _viewsToDisable)
        {
            view.setEnabled(true);
        }
        _stopButton.setEnabled(false);
    }

    // our Handler understands six messages
    private static final int LOCATION_MESSAGE = 1;
    private static final int ERROR_MESSAGE = 2;
    private static final int DONE_MESSAGE = 3;
    private static final int REGISTRATION_SUCCESS_MESSAGE = 4;
    private static final int REGISTRATION_ERROR_MESSAGE = 5;
    private static final int LOCATION_LIST_MESSAGE = 6;

    private void setUIHandler()
    {
        _handler = new Handler()
        {
            @Override
            public void handleMessage(final Message msg)
            {
                switch (msg.what)
                {
                case LOCATION_MESSAGE:
                    _tv.setText(((Location) msg.obj).toString());
                    return;
                case LOCATION_LIST_MESSAGE:
                    {
                        final StringBuilder sb = new StringBuilder();
                        for (final Location location : (Location[]) msg.obj)
                            sb.append(location+"\n\n");
                        _tv.setText(sb.toString());
                    }
                    return;
                case ERROR_MESSAGE:
                    _tv.setText(((WPSReturnCode) msg.obj).name());
                    return;
                case DONE_MESSAGE:
                    deactivateStopButton();
                    _stop = false;
                    return;
                case REGISTRATION_SUCCESS_MESSAGE:
                    _tv.setText("Registration succeeded");
                    return;
                case REGISTRATION_ERROR_MESSAGE:
                    _tv.setText("Registration failed ("+((WPSReturnCode) msg.obj).name()+")");
                    return;
                }
            }
        };
    }

    /**
     * Preferences management code
     */
    public static class Preferences
        extends PreferenceActivity
    {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);

            if (options == null)
            {
                options = new Option[]
                    {
                        new Option("Key"                      , OptionType.TEXT, null),
                        new Option(SERVER_URL_KEY             , OptionType.TEXT, null),
                        new Option("Local File Path"          , OptionType.TEXT, null),
                        new Option("Period"                   , OptionType.NONNEGATIVE_INTEGER, null),
                        new Option("Iterations"               , OptionType.NONNEGATIVE_INTEGER, null),
                        new Option("Desired XPS Accuracy"     , OptionType.NONNEGATIVE_INTEGER, null),
                        new Option("Tiling Path"              , OptionType.TEXT, getFilesDir().getAbsolutePath()),
                        new Option("Max Data Per Session"     , OptionType.NONNEGATIVE_INTEGER, null),
                        new Option("Max Data Total"           , OptionType.NONNEGATIVE_INTEGER, null),
                        new ListOption("Street Address Lookup", OptionType.LIST, null, new String[] {"None", "Limited", "Full"})
                    };
            }

            setPreferenceScreen(createRootPreferenceScreen());
        }

        private PreferenceScreen createRootPreferenceScreen()
        {
            final PreferenceScreen root =
                getPreferenceManager().createPreferenceScreen(this);

            final PreferenceCategory category = new PreferenceCategory(this);
            category.setTitle("WpsApiTest Settings");
            root.addPreference(category);

            for (final Option option : options)
            {
                Preference setting = null;
                switch (option.type)
                {
                case CHECKBOX:
                {
                    setting = new CheckBoxPreference(this);
                    break;
                }
                case LIST:
                {
                    if (option instanceof ListOption)
                    {
                        final ListPreference listSetting = new ListPreference(this);
                        final String[] entries = ((ListOption)option).entries;
                        listSetting.setEntries(entries);
                        listSetting.setEntryValues(entries);
                        setting = listSetting;
                        break;
                    }
                }
                default:
                {
                    final EditTextPreference textSetting = new EditTextPreference(this);
                    textSetting.getEditText().setSingleLine();
                    textSetting.getEditText().setFilters(new InputFilter[]
                    {
                        new InputFilter.LengthFilter(512)
                    });

                    if (option.type == OptionType.NONNEGATIVE_INTEGER)
                        textSetting.getEditText()
                                   .setKeyListener(new DigitsKeyListener(false,
                                                                         false));
                    setting = textSetting;
                }
                }

                if (setting != null)
                {
                    setting.setKey(option.name);
                    setting.setTitle(option.name);
                    if (option.defaultValue != null)
                        setting.setDefaultValue(option.defaultValue);

                    category.addPreference(setting);
                }
            }

            return root;
        }

        private enum OptionType
        {
            TEXT,
            NONNEGATIVE_INTEGER,
            LIST,
            CHECKBOX;
        }

        private class Option
        {
            private Option(final String name, final OptionType type, final Object defaultValue)
            {
                super();
                this.name = name;
                this.type = type;
                this.defaultValue = defaultValue;
            }

            String name;
            OptionType type;
            Object defaultValue;
        }

        private class ListOption extends Option
        {
            private ListOption(final String name, final OptionType type, final Object defaultValue, final String[] entries)
            {
                super(name, type, defaultValue);
                this.entries = entries;
            }

            String[] entries;
        }

        private static Option[] options = null;
    }

    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key)
    {
        if (sharedPreferences.getString(key, "default").equals(""))
        {
            // delete empty preferences so we get the default values below
            final Editor editor = sharedPreferences.edit();
            editor.remove(key);
            editor.commit();
        }

        boolean tilingChanged = false;

        if (key.equals("Key"))
        {
            setKey(sharedPreferences.getString(key, ""));
        }
        else if (key.equals("Local File Path"))
        {
            _localFilePath = sharedPreferences.getString(key, "");
            // TODO: clean this up?
            ArrayList<String> paths = null;
            if (! _localFilePath.equals(""))
            {
                paths = new ArrayList<String>(Arrays.asList(new String[]{_localFilePath}));
            }
            _xps.setLocalFilePaths(paths);
            return;
        }
        else if (key.equals("Period"))
        {
            _period = Long.valueOf(sharedPreferences.getString(key, "5000"));
            return;
        }
        else if (key.equals("Iterations"))
        {
            _iterations = Integer.valueOf(sharedPreferences.getString(key, "1"));
            return;
        }
        else if (key.equals("Desired XPS Accuracy"))
        {
            _desiredXpsAccuracy = Integer.valueOf(sharedPreferences.getString(key, "30"));
            return;
        }
        else if (key.equals("Tiling Path"))
        {
            _tilingPath = sharedPreferences.getString(key, "");
            tilingChanged = true;
        }
        else if (key.equals("Max Data Per Session"))
        {
            _maxDataSizePerSession = Long.valueOf(sharedPreferences.getString(key, "0"));
            tilingChanged = true;
        }
        else if (key.equals("Max Data Total"))
        {
            _maxDataSizeTotal = Long.valueOf(sharedPreferences.getString(key, "0"));
            tilingChanged = true;
        }
        else if (key.equals("Street Address Lookup"))
        {
            final String setting = sharedPreferences.getString(key, "None");
            if (setting.equals("None"))
            {
                _streetAddressLookup = WPSStreetAddressLookup.WPS_NO_STREET_ADDRESS_LOOKUP;
            }
            else if (setting.equals("Limited"))
            {
                _streetAddressLookup = WPSStreetAddressLookup.WPS_LIMITED_STREET_ADDRESS_LOOKUP;
            }
            else if (setting.equals("Full"))
            {
                _streetAddressLookup = WPSStreetAddressLookup.WPS_FULL_STREET_ADDRESS_LOOKUP;
            }
            return;
        }
        else if (key.equals(SERVER_URL_KEY))
        {
            final String serverUrl = sharedPreferences.getString(SERVER_URL_KEY, "");
            XPS.setServerUrl(serverUrl.length() > 0 ? serverUrl : null);
            return;
        }

        if (tilingChanged)
        {
            _xps.setTiling(_tilingPath,
                           _maxDataSizePerSession,
                           _maxDataSizeTotal,
                           null);
        }
    }

    private void setKey(String key)
    {
        if (key.equals(""))
            return;

        try
        {
            _xps.setKey(key);
            _tv.setText("");
        }
        catch (IllegalArgumentException e)
        {
            _tv.setText("The current API key is invalid. Please update it in settings.");
        }
    }

    private static final String SERVER_URL_KEY = "Server URL";

    private String _localFilePath = null;
    private long _period;
    private int _iterations;
    private int _desiredXpsAccuracy;
    private String _tilingPath = null;
    private long _maxDataSizePerSession;
    private long _maxDataSizeTotal;
    private WPSStreetAddressLookup _streetAddressLookup;
    private XPS _xps;
    private TextView _tv = null;
    private Handler _handler;
}
