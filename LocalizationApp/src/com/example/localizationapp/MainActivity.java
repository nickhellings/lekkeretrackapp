package com.example.localizationapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * @author Nick
 *
 */
public class MainActivity extends Activity {

	//Static variables
	private static final Region ALL_BEACONS_EVER = new Region("allBeaconsID",
			null, null, null);
	private static final String TAG = "RSSIMeasurement";
	private static final int MEASURE_TIME = 30000;
	private static final int MEASURE_TIME_CURRENT_LOC = 10000;
	private static final String SSID_LISTEN_TO = "GHG-Wifi";
	private static final int LISTVIEW_COLOR = Color.DKGRAY; 
	//Managers, Adapters, Listeners
	private BeaconManager beaconManager;
	private BluetoothAdapter btAdapter;
	private ArrayAdapter adapter;
	private IntentFilter scanResultsFilter;
	//Timer variables
	private Timer timer;
	private TimerTask timertask;
	//Lists
	private HashMap<String, List<Measurement>> measurements;
	private List<Measurement> currentLocation;
	private List<String> listViewItems;
	private Queue<Measurement> measurementsToPrint;
	//GUI
	private ListView listviewBeacons;
	private ProgressBar progressBar;
	//Status variables
	private String locationName = "";
	private boolean isScanningForCurrentLocation = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//--------BLUETOOTH-----------
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		btAdapter.enable();
		beaconManager = new BeaconManager(this);

		//--------INITIALIZE LISTS----
		measurementsToPrint = new LinkedList<Measurement>();
		measurements = new HashMap<String, List<Measurement>>();
		currentLocation = new ArrayList<Measurement>();
		listViewItems = new ArrayList<String>();

		//--------LISTVIEW----------
		listviewBeacons = (ListView) findViewById(R.id.listViewBeacons);
		listviewBeacons.setBackgroundColor(LISTVIEW_COLOR);
		adapter = new ArrayAdapter(getApplicationContext(), android.R.layout.simple_list_item_1, listViewItems);
		listviewBeacons.setAdapter(adapter);
		
		//--------SPINNER---------
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		progressBar.setVisibility(ProgressBar.INVISIBLE);

		//-------TIMER-----------
		timer = new Timer();

		//--------LISTENERS-------
		setRangingListener();
		setScanButtonListener();
		setSaveButtonListener();
		setLocationButtonListener();
		setClearButtonListener();
		scanResultsFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		
		//-------LOAD----------
		loadMeasurements();
	}

	//--------LISTENERS & RECEIVERS---------
	/**
	 * The broadcastreceiver that will receive all the results of wifi scans. 
	 * Will send the received results to the wifiResultHandler and start a new scan.
	 */
	private BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			WifiManager w = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			wifiResultHandler(w.getScanResults());
			w.startScan();
		}
	};	
	private void setSaveButtonListener() {
		Button buttonSave = (Button) findViewById(R.id.buttonSave);
		buttonSave.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				saveMeasurements();
			}
		});
	}
	private void setClearButtonListener() {
		Button buttonClear = (Button) findViewById(R.id.buttonClear);
		buttonClear.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				clearMemory();
			}
		});
	}
	private void setLocationButtonListener() {
		Button buttonLocation = (Button) findViewById(R.id.ButtonLocation);
		buttonLocation.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				runScan(true);
			}
		});
	}
	private void setScanButtonListener() {
		Button buttonScan = (Button) findViewById(R.id.buttonScan);
		buttonScan.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				runScan(false);
			}
		});
	}
	/**
	 * Sets the listener that will receive the incoming beacon signals from the beaconmanager.
	 */
	private void setRangingListener() {
		beaconManager.setRangingListener(new BeaconManager.RangingListener() {

			@Override
			public void onBeaconsDiscovered(Region region, List<Beacon> beacons) {
				handleBeacons(beacons);
			}
		});
	}
	
	//----------METHODS-----------
	/**
	 * Handles the received list of ScanResults, 
	 * either adds them to the currentLocation list if they are not in there yet, 
	 * else runs the increaseTotalRSSI method of the measurement.
	 * 
	 * @param scanResults the accesspoints to handle.
	 */
	private void wifiResultHandler(List<ScanResult> scanResults) {
		for (ScanResult scanResult : scanResults) {
			if (scanResult.SSID.equals(SSID_LISTEN_TO)) {
				
				Measurement matchingMeasurement = getMatchingMeasurement(scanResult);

				if (matchingMeasurement != null){
					matchingMeasurement.increaseTotalRSSI(scanResult.level);
				}
				else{
					currentLocation.add(new WiFiMeasurement(scanResult, locationName));
				}
			}
		}
	}
	/**
	 * Run a scan to add a new location footprint of all found signals, or get the current location.
	 *
	 * @param currentLoc true to scan to get current location, false to make a new footprint.
	 */
	private void runScan(boolean currentLoc) {
		progressBar.setVisibility(ProgressBar.VISIBLE);
		
		int timerDuration;
		
		//Two different possible scans, true for retrieving current location
		//false for making a new footprint. Different timerDurations allows for more flexibility.
		if (currentLoc) {
			isScanningForCurrentLocation = true;
			timerDuration = MEASURE_TIME_CURRENT_LOC;
		}
		else {
			timerDuration = MEASURE_TIME;
			
			//Save the name of the footprint to add in the locationName variable.
			EditText locationText = (EditText) findViewById(R.id.editTextLocation);
			locationName = locationText.getText().toString();
		}
		
		//Register the receiver for the wifi scanresults and start the scan.
		registerReceiver(wifiScanReceiver, scanResultsFilter);
		WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		wm.startScan();
		
		//Connect and start listening to low energy beacons.
		beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
			@Override
			public void onServiceReady() {
				try {
					beaconManager.startRanging(ALL_BEACONS_EVER);
				} catch (RemoteException e) {
				}
			}
		});

		timertask = new TimerTask() {
			@Override
			public void run() {

				Log.i(TAG, "Timer event");
				
				//Stop scanning for low energy and wifi accesspoints
				beaconManager.disconnect();
				unregisterReceiver(wifiScanReceiver);

				//If we are just scanning for the current location, put the result into the TextView.
				//else we are making a new footprint, so copy the result to the measurements list and print them to the ListView.
				if (isScanningForCurrentLocation){
					
					isScanningForCurrentLocation = false;
					
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							
							TextView locationView = (TextView) findViewById(R.id.textViewLocation);
							locationView.setText(getCurrentLocation());
							
							currentLocation.clear();
							
							progressBar.setVisibility(ProgressBar.INVISIBLE);
						}
					});
				}
				else {
					List<Measurement> measurementListTemp = new ArrayList<Measurement>();
					
					for (Measurement measurementToCopy : currentLocation) {
						measurementListTemp.add(measurementToCopy);
					}
					measurements.put(locationName, measurementListTemp);
					
					locationName = "";
					
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							printMeasurements(currentLocation);
							currentLocation.clear();
							
							progressBar.setVisibility(ProgressBar.INVISIBLE);
						}
					});
				}
			}
		};
		
		timer.schedule(timertask, timerDuration);
	}

	/**
	 * Method to clear all temporary and permanent scan data.
	 */
	private void clearMemory() {
		SharedPreferences settings = getApplicationContext()
				.getSharedPreferences("LocalizationApp", 0);
		SharedPreferences.Editor editor = settings.edit();
		
		editor.clear().commit();
		
		measurements.clear();
		listViewItems.clear();
		adapter.notifyDataSetChanged();
	}

	/**
	 * Looks for a measurement that is related to the same beacon, AP or other type of sender.
	 * The list of measurements used is the one of the last current location scan.
	 * 
	 * @param measurement the measurement to be compared.
	 * @return a Measurement if one was found in the currentLocation list equal to the supplied measurement, else null.
	 */
	private Measurement getMatchingMeasurement(Measurement measurement) {
		for (Measurement currentLocMeasurement : currentLocation) {
			if (measurement.compareTo(currentLocMeasurement)) {
				return currentLocMeasurement;
			}
		}
		return null;
	}	
	private Measurement getMatchingMeasurement(Beacon beacon) {
		return getMatchingMeasurement(new BeaconMeasurement(beacon));
	}
	private Measurement getMatchingMeasurement(ScanResult scanresult) {
		return getMatchingMeasurement(new WiFiMeasurement(scanresult));
	}

	/**
	 * Method to return the closest matching location to the current position.
	 * Each matching current location measurement (i.e. same beacon or router as in the list we just scanned)
	 * will be subtracted by the average RSSI measured for a saved footprint.
	 * The result of that will be squared and added up to other results of different beacons, APs etc.
	 * The end result will be the square root of all added up results and thus the closest match to one of the saved footprints.
	 * 
	 * @return the name of the location most fitting to the current position.
	 */
	private String getCurrentLocation() {

		Iterator it = measurements.entrySet().iterator();
		//Make a TreeMap for the results so it's easy to get the best match.
		TreeMap<Double, String> results = new TreeMap<Double, String>();

		//Loop through all the saved footprints.
		while (it.hasNext()) {
			
			Map.Entry pairs = (Map.Entry) it.next();
			
			String location = (String) pairs.getKey();
			List<Measurement> measurements = (List<Measurement>) pairs.getValue();
			
			//The calculated index of how well the footprint 
			//compares to the location of the user will be put in this variable.
			int currentLocationResult = 0;

			Log.i(TAG, "Measuring " + location);
			
			//Loop through each of the measurements for the current footprint
			for (Measurement measurement : measurements) {
				
				//Match the current measurement with a measurement of the location of the user.
				Measurement currentLocMeasurement = getMatchingMeasurement(measurement);

				//If there is a matching measurement, calculate the index.
				//if there is not a matching measurement, do nothing for now. 
				//Not having a measurement match doesnt mean anything, 
				//could be that the AP was moved or disabled.
				if (currentLocMeasurement != null) {

					int rssiCurrentPos = currentLocMeasurement.getAverageRSSI();
					int rssiAverage = measurement.getAverageRSSI();
					
					Log.i(TAG, "Result is " 
						+ String.valueOf(rssiCurrentPos) 
						+ " - "
						+ String.valueOf(rssiAverage) 
						+ " = " 
						+ String.valueOf(rssiCurrentPos - rssiAverage)
						+ " = "
						+ Math.pow(rssiCurrentPos - rssiAverage, 2));

					currentLocationResult += Math.pow(rssiCurrentPos - rssiAverage, 2);

					Log.i(TAG, "Result now " + String.valueOf(currentLocationResult));
				}
			}
			//This is the endresult, the index of how well this 
			//footprint compared to the measurements taken at the location of the user.
			Log.i(TAG, "Endresult " + String.valueOf(Math.pow(currentLocationResult, 0.5)));
			results.put(Math.pow(currentLocationResult, 0.5), location);
		}
		
		//Return the location that belongs to the lowest index.
		if (results.size() > 0) {
			return (String) results.get(results.firstKey());
		}
		return "";
	}
	private void printMeasurement(Measurement measurement) {
		String macAndRSSI = measurement.getLocation() 
				+ " " 
				+ measurement.getMac() 
				+ ": " 
				+ String.valueOf(measurement.getAverageRSSI());
		
		Log.i(TAG, "Printing measurement " + measurement.getMac());

		listViewItems.add(macAndRSSI);
		adapter.notifyDataSetChanged();
	}

	private void printMeasurements(List<Measurement> measurements) {
		for (Measurement measurement : measurements) {
			printMeasurement(measurement);
		}
	}

	/**
	 * Loads all the saved footprints and puts them into the measurements list. 
	 * After this the printMeasurement method will be ran to print the measurement to the ListView.
	 */
	private void loadMeasurements() {
		SharedPreferences settings = getApplicationContext()
				.getSharedPreferences("LocalizationApp", 0);
		Gson gson = new Gson();
		Map<String, ?> allSettings = settings.getAll();
		Iterator it = allSettings.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry) it.next();
			Log.i(TAG, "Loading: " + pairs.getKey());
			Log.i(TAG, "JSON String: " + pairs.getValue());
			List<Measurement> measurementsTemp = (List<Measurement>) gson.fromJson(
					(String) pairs.getValue(),
					new TypeToken<List<BeaconMeasurement>>() {
					}.getType());
			measurements.put((String) pairs.getKey(), measurementsTemp);
			printMeasurements(measurementsTemp);
		}
	}
	
	/**
	 * This method will loop through the list of given beacons and see if there is a measurement in the currentLocation list
	 * matching the beacon. Then it will either add a new measurement to the currentLocation list
	 * if there was no match or run the increaseTotalRSSI method of the matching measurement.
	 * 
	 * @param beacons a list of beacons that have to be handled.
	 */
	private void handleBeacons(List<Beacon> beacons){
		
		for (Beacon beacon : beacons) {
			Measurement matchingMeasurement = getMatchingMeasurement(beacon);

			if (matchingMeasurement != null){
				Log.i(TAG, "matching measurement not null: " + beacon.getRssi());
				matchingMeasurement.increaseTotalRSSI(beacon.getRssi());
			}
			else{
				Log.i(TAG, "new beaconmeasurement " + beacon.getMacAddress());
				currentLocation.add(new BeaconMeasurement(beacon, locationName));
			}
		}
	}
	/**
	 * Saves all footprints in the measurements list, replacing the measurements that were already saved or adds
	 * new entries if the footprint was not yet saved.
	 */
	private void saveMeasurements() {
		SharedPreferences settings = getApplicationContext()
				.getSharedPreferences("LocalizationApp", 0);
		SharedPreferences.Editor editor = settings.edit();
		Gson gson = new Gson();
		Iterator it = measurements.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry) it.next();
			Log.i(TAG, "Saving: " + pairs.getKey());
			String jsonString = gson.toJson(pairs.getValue());
			Log.i(TAG, "JSON String: " + jsonString);
			editor.putString((String) pairs.getKey(), jsonString);
		}

		editor.commit();
	}
	
	//---------ACTIVITY METHODS----------
	@Override
	public void onStart() {
		super.onStart();

	}

	@Override
	public void onStop() {
		try {
			beaconManager.stopRanging(ALL_BEACONS_EVER);
		} catch (RemoteException e) {
		}
		super.onStop();
	}

	@Override
	public void onDestroy() {
		beaconManager.disconnect();

		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
}
