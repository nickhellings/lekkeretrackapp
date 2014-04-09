package com.example.localizationapp;

import java.util.ArrayList;
import java.util.List;

public abstract class Measurement {
	private int averageRSSI;
	private int totalRSSI;
	private int pollingAmount;
	private String location;
	private List<Integer> RSSIS;
	
	public Measurement(String location) {
		this.averageRSSI = 0;
		this.totalRSSI = 0;
		this.pollingAmount = 0;
		this.location = location;
		this.RSSIS = new ArrayList<Integer>();
	}
	
	public Measurement() {
		this("");
	}

	/**
	 * Compares the give measurement to this measurement. Comparison is done based on the Mac Addresses of the measurements.
	 * 
	 * @param measurement the measurement to compare to this measurement.
	 * @return true if the given measurement matches this measurement, false if it doesnt.
	 */
	public boolean compareTo(Measurement measurement) {
		if (measurement instanceof BeaconMeasurement && this instanceof BeaconMeasurement){
			BeaconMeasurement measurementA = (BeaconMeasurement) measurement;
			BeaconMeasurement measurementB = (BeaconMeasurement) this;
			
			if (measurementA.getBeacon().getMacAddress().equals(measurementB.getBeacon().getMacAddress())){
				return true;
			}
		}
		else if (measurement instanceof WiFiMeasurement && this instanceof WiFiMeasurement){
			WiFiMeasurement measurementA = (WiFiMeasurement) measurement;
			WiFiMeasurement measurementB = (WiFiMeasurement) this;
			
			if (measurementA.getAccesspoint().BSSID.equals(measurementB.getAccesspoint().BSSID)){
				return true;
			}
		}
		return false;
	}
	
	public abstract String getMac();
	
	public int getAverageRSSI() {
		return averageRSSI;
	}
	
	public int getTotalRSSI() {
		return totalRSSI;
	}
	
	public int getPollingAmount() {
		return pollingAmount;
	}
	
	public String getLocation() {
		return location;
	}
	
	public List<Integer> getRSSIS() {
		return RSSIS;
	}
	
	public void addRSSI(int rssi) {
		RSSIS.add(rssi);
	}
	
	public void setRSSIS(List<Integer> RSSIS) {
		this.RSSIS = RSSIS;
	}
	
	public void setAverageRSSI(int averageRSSI) {
		this.averageRSSI = averageRSSI;  
	}
	
	public void setTotalRSSI(int totalRSSI) {
		this.totalRSSI = totalRSSI;
	}
	
	public void setLocation(String location) {
		this.location = location;
	}
	
	/**
	 * Method to increase the totalRSSI of the measurement. 
	 * Will also increase the pollingAmount by 1 and calculate the averageRSSI.
	 * 
	 * @param increaseValue the RSSI to increase the total RSSI with.
	 */
	public void increaseTotalRSSI(int increaseValue) {
		this.totalRSSI += increaseValue;
		this.pollingAmount += 1;
		this.averageRSSI = totalRSSI / pollingAmount;
	}
	
	public void setPollingAmount(int pollingAmount) {
		this.pollingAmount = pollingAmount;
	}
}
