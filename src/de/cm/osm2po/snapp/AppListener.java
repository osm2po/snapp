package de.cm.osm2po.snapp;

public interface AppListener {
	void onGpsChanged(double lat, double lon, float bearing);
	void onPositionChanged(double lat, double lon, float bearing);
	void onRouteChanged(long[] geometry);
	void onRouteLost();
}
