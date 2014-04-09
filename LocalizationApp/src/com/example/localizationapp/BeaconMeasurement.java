package com.example.localizationapp;

import com.estimote.sdk.Beacon;

public class BeaconMeasurement extends Measurement{
	private Beacon beacon;
	
	public BeaconMeasurement(Beacon beacon, String location) 
	{
		super(location);
		this.beacon = beacon;
		increaseTotalRSSI(beacon.getRssi());
	}
	
	public BeaconMeasurement(Beacon beacon) {
		super();
		this.beacon = beacon;
		increaseTotalRSSI(beacon.getRssi());
	}
	
	public Beacon getBeacon() {
		return beacon;
	}

	@Override
	public String getMac() {
		return beacon.getMacAddress();
	}
}
