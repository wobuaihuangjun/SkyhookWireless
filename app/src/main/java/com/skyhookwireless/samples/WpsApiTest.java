package com.skyhookwireless.samples;

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
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
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

import java.util.ArrayList;
import java.util.Arrays;

import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_HYBRID;
import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_NONE;
import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_NORMAL;
import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_SATELLITE;
import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_TERRAIN;

public class WpsApiTest extends AppCompatActivity implements
        OnSharedPreferenceChangeListener,
        OnClickListener,
        OnMapReadyCallback,
        LocationListener,
        AdapterView.OnItemSelectedListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "WpsApiTest";

    public static final String SERVER_URL_KEY = "Server URL";

    private String localFilePath = null;
    private String _tilingPath = null;
    private long _maxDataSizePerSession;
    private long _maxDataSizeTotal;
    private WPSStreetAddressLookup _streetAddressLookup;
    private XPS _xps;
    private Handler _handler;

    private GoogleMap mMap;
    private Spinner mSpinner;

    SharedPreferences preferences;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // create the XPS instance, passing in our Context
        _xps = new XPS(this);

        // listen for settings changes
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
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

        setKey(preferences.getString("Key", ""));

        // initialize the Handler which will display location data
        // in the text view. we use a Handler because UI updates
        // must occur in the UI thread
        setUIHandler();

        mSpinner = (Spinner) findViewById(R.id.layers_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.layers_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(this);

        Button wpsLocation = (Button) findViewById(R.id.wps_location);
        Button googleLocation = (Button) findViewById(R.id.google_location);

        _viewsToDisable.clear();
        _viewsToDisable.add(wpsLocation);

        wpsLocation.setOnClickListener(this);
        googleLocation.setOnClickListener(this);
        deactivateStopButton();

        // Set the buttons within a ScrollView so that the user can scroll through
        // the buttons if there total length extends into the Location Display

        notifyLocationSetting();
        notifyAlwaysAllowScanning();
    }

    @Override
    public void onPause() {
        super.onPause();

        // make sure WPS is stopped
        _xps.abort();
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private final ArrayList<View> _viewsToDisable = new ArrayList<>();

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.wps_location:
                activateStopButton();
                _xps.getLocation(null,
                        _streetAddressLookup,
                        _callback);
                break;
            case R.id.google_location:
                LocationServices.FusedLocationApi.requestLocationUpdates(
                        mGoogleApiClient,
                        mLocationRequest,
                        this);
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        updateMapType();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    /**
     * A single callback class that will be used to handle
     * all location notifications sent by WPS to our app.
     */
    private class MyLocationCallback implements IPLocationCallback, WPSLocationCallback,
            WPSPeriodicLocationCallback, WPSCertifiedLocationCallback {
        public void done() {
            // tell the UI thread to re-enable the buttons
            _handler.sendMessage(_handler.obtainMessage(DONE_MESSAGE));
        }

        public WPSContinuation handleError(final WPSReturnCode error) {
            // send a message to display the error
            _handler.sendMessage(_handler.obtainMessage(ERROR_MESSAGE,
                    error));
            // return WPS_STOP if the user pressed the Stop button
            return WPSContinuation.WPS_CONTINUE;
        }

        public void handleIPLocation(final IPLocation location) {
            // send a message to display the location
            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
                    location));
        }

        public void handleWPSLocation(final WPSLocation location) {
            // send a message to display the location
            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
                    location));
        }

        public WPSContinuation handleWPSPeriodicLocation(final WPSLocation location) {
            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
                    location));
            // return WPS_STOP if the user pressed the Stop button
            return WPSContinuation.WPS_CONTINUE;
        }

        public WPSContinuation handleWPSCertifiedLocation(final WPSLocation[] locations) {
            _handler.sendMessage(_handler.obtainMessage(LOCATION_LIST_MESSAGE,
                    locations));
            // return WPS_STOP if the user pressed the Stop button
            return WPSContinuation.WPS_CONTINUE;
        }
    }

    private final MyLocationCallback _callback = new MyLocationCallback();

    /**
     * Starting with API version 18, Android supports Wi-Fi scanning when Wi-Fi is
     * disabled for connectivity purposes.
     * <p>
     * To improve location accuracy we encourage users to enable this feature.
     * The code below provides a simple example where we use Android's built
     * in dialog to notify the user.
     */
    private void notifyAlwaysAllowScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            final WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

            if (!wifi.isWifiEnabled() && !wifi.isScanAlwaysAvailable()) {
                final Intent intent =
                        new Intent(WifiManager.ACTION_REQUEST_SCAN_ALWAYS_AVAILABLE);
                startActivityForResult(intent, 1);
            }
        }
    }

    private void notifyLocationSetting() {
        final LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        final boolean networkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        final boolean gpsEnabled =
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!networkEnabled && !gpsEnabled)
            displayLocationDialog("Location services disabled",
                    "Location services have been disabled. Please enable location.");
        else if (!networkEnabled)
            displayLocationDialog("Location performance",
                    "Location performance will be improved by enabling network based location.");
        else if (!gpsEnabled)
            displayLocationDialog("Location performance",
                    "Location performance will be improved by enabling gps based location.");
    }

    private void displayLocationDialog(final String title, final String message) {
        final DialogInterface.OnClickListener launchSettings =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
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

    private void activateStopButton() {
        for (final View view : _viewsToDisable) {
            view.setEnabled(false);
        }
    }

    private void deactivateStopButton() {
        for (final View view : _viewsToDisable) {
            view.setEnabled(true);
        }
    }

    // our Handler understands six messages
    private static final int LOCATION_MESSAGE = 1;
    private static final int ERROR_MESSAGE = 2;
    private static final int DONE_MESSAGE = 3;
    private static final int REGISTRATION_SUCCESS_MESSAGE = 4;
    private static final int REGISTRATION_ERROR_MESSAGE = 5;
    private static final int LOCATION_LIST_MESSAGE = 6;

    private void setUIHandler() {
        _handler = new Handler() {
            @Override
            public void handleMessage(final Message msg) {
                switch (msg.what) {
                    case LOCATION_MESSAGE:
                        Location location = ((Location) msg.obj);

                        Log.w(TAG, location.toString());
                        addWpsMarker(location);
                        break;
                    case LOCATION_LIST_MESSAGE:
                        Location[] locations = ((Location[]) msg.obj);
                        Log.w(TAG, locations.toString());
                        break;
                    case ERROR_MESSAGE:
                        Toast.makeText(WpsApiTest.this, ((WPSReturnCode) msg.obj).name(), Toast.LENGTH_SHORT).show();
                        Log.w(TAG, ((WPSReturnCode) msg.obj).name());
                        break;
                    case DONE_MESSAGE:
                        deactivateStopButton();
                        Toast.makeText(WpsApiTest.this, "Location complete", Toast.LENGTH_SHORT).show();
                        break;
                    case REGISTRATION_SUCCESS_MESSAGE:
                        Log.w(TAG, "Registration succeeded");
                        Toast.makeText(WpsApiTest.this, "Registration succeeded", Toast.LENGTH_SHORT).show();
                        break;
                    case REGISTRATION_ERROR_MESSAGE:
                        Log.w(TAG, "Registration failed (" + ((WPSReturnCode) msg.obj).name() + ")");
                        Toast.makeText(WpsApiTest.this, "Registration failed (" + ((WPSReturnCode) msg.obj).name() + ")", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };
    }

    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (sharedPreferences.getString(key, "default").equals("")) {
            // delete empty preferences so we get the default values below
            final Editor editor = sharedPreferences.edit();
            editor.remove(key);
            editor.commit();
        }

        boolean tilingChanged = false;

        if (key.equals("Key")) {
            setKey(sharedPreferences.getString(key, ""));
        } else if (key.equals("Local File Path")) {
            localFilePath = sharedPreferences.getString(key, "");
            // TODO: clean this up?
            ArrayList<String> paths = null;
            if (!localFilePath.equals("")) {
                paths = new ArrayList<String>(Arrays.asList(new String[]{localFilePath}));
            }
            _xps.setLocalFilePaths(paths);
            return;
        } else if (key.equals("Tiling Path")) {
            _tilingPath = sharedPreferences.getString(key, "");
            tilingChanged = true;
        } else if (key.equals("Max Data Per Session")) {
            _maxDataSizePerSession = Long.valueOf(sharedPreferences.getString(key, "0"));
            tilingChanged = true;
        } else if (key.equals("Max Data Total")) {
            _maxDataSizeTotal = Long.valueOf(sharedPreferences.getString(key, "0"));
            tilingChanged = true;
        } else if (key.equals("Street Address Lookup")) {
            final String setting = sharedPreferences.getString(key, "None");
            if (setting.equals("None")) {
                _streetAddressLookup = WPSStreetAddressLookup.WPS_NO_STREET_ADDRESS_LOOKUP;
            } else if (setting.equals("Limited")) {
                _streetAddressLookup = WPSStreetAddressLookup.WPS_LIMITED_STREET_ADDRESS_LOOKUP;
            } else if (setting.equals("Full")) {
                _streetAddressLookup = WPSStreetAddressLookup.WPS_FULL_STREET_ADDRESS_LOOKUP;
            }
            return;
        } else if (key.equals(SERVER_URL_KEY)) {
            final String serverUrl = sharedPreferences.getString(SERVER_URL_KEY, "");
            XPS.setServerUrl(serverUrl.length() > 0 ? serverUrl : null);
            return;
        }

        if (tilingChanged) {
            _xps.setTiling(_tilingPath,
                    _maxDataSizePerSession,
                    _maxDataSizeTotal,
                    null);
        }
    }

    private void setKey(String key) {
        if (key.equals("")) {
            _xps.setKey("eJwVwcENACAIBLA3w5CA5FCfIrqUcXdjq6TyoanQWXCYQ3mnJ3vtwWZFeMnIaJhZd78PD04LLQ");
            return;
        }

        try {
            _xps.setKey(key);
        } catch (IllegalArgumentException e) {
            Toast.makeText(WpsApiTest.this, "The current API key is invalid. Please update it in settings.", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // We will provide our own zoom controls.
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.setMyLocationEnabled(false);

        initAMapClient();
    }

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    private void initAMapClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mLocationRequest = LocationRequest.create()
                .setInterval(5000)         // 5 seconds
                .setFastestInterval(16)    // 16ms = 60fps
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        mGoogleApiClient.connect();
    }

    Marker wpsMarker;
    Marker googleMarker;

    private void addWpsMarker(Location location) {
        LatLng center = new LatLng(location.getLatitude(), location.getLongitude());

        if (wpsMarker != null) {
            wpsMarker.remove();
        }
        wpsMarker = mMap.addMarker(new MarkerOptions()
                .position(center).title("WPS")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        CameraPosition position =
                new CameraPosition.Builder().target(center)
                        .zoom(15.5f)
                        .bearing(0)
                        .tilt(0)
                        .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000, null);
    }

    private void addGoogleMarker(double latitude, double longitude) {
        LatLng center = new LatLng(latitude, longitude);

        if (googleMarker != null) {
            googleMarker.remove();
        }
        googleMarker = mMap.addMarker(new MarkerOptions()
                .position(center)
                .title("Google")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        CameraPosition position =
                new CameraPosition.Builder().target(center)
                        .zoom(15.5f)
                        .bearing(0)
                        .tilt(0)
                        .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000, null);
    }

    private void updateMapType() {
        // No toast because this can also be called by the Android framework in onResume() at which
        // point mMap may not be ready yet.
        if (mMap == null) {
            return;
        }

        String layerName = ((String) mSpinner.getSelectedItem());
        if (layerName.equals(getString(R.string.normal))) {
            mMap.setMapType(MAP_TYPE_NORMAL);
        } else if (layerName.equals(getString(R.string.hybrid))) {
            mMap.setMapType(MAP_TYPE_HYBRID);


        } else if (layerName.equals(getString(R.string.satellite))) {
            mMap.setMapType(MAP_TYPE_SATELLITE);
        } else if (layerName.equals(getString(R.string.terrain))) {
            mMap.setMapType(MAP_TYPE_TERRAIN);
        } else if (layerName.equals(getString(R.string.none_map))) {
            mMap.setMapType(MAP_TYPE_NONE);
        } else {
            Log.i(TAG, "Error setting layer with name " + layerName);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient,
                mLocationRequest,
                this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
        Toast.makeText(this, "Connection Suspended", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
        Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLocationChanged(android.location.Location location) {
        Log.d(TAG, "onLocationChanged");
        if (location == null) {
            return;
        }
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
        addGoogleMarker(location.getLatitude(), location.getLongitude());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() == R.id.menu_set) {
            Intent launchPreferencesIntent =
                    new Intent().setClass(WpsApiTest.this, Preferences.class);
            startActivity(launchPreferencesIntent);
            return true;
        } else if (item.getItemId() == R.id.menu_reset) {
            reset();
        }
        return super.onOptionsItemSelected(item);
    }

    private void reset() {
        preferences.edit().clear().apply();
    }

}
