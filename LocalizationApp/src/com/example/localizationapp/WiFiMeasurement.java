package com.example.localizationapp;

import android.net.wifi.ScanResult;

public class WiFiMeasurement extends Measurement{

	private ScanResult accesspoint;
	
	public WiFiMeasurement(ScanResult accesspoint, String location) {
		super(location);
		this.accesspoint = accesspoint;
		increaseTotalRSSI(accesspoint.level);
	}
	
	public WiFiMeasurement(ScanResult accesspoint) {
		super();
		this.accesspoint = accesspoint;
		increaseTotalRSSI(accesspoint.level);
	}
	
	public ScanResult getAccesspoint() {
		return accesspoint;
	}

	@Override
	public String getMac() {
		return accesspoint.BSSID;
	}
}
