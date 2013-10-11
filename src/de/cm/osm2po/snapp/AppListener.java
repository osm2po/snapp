package de.cm.osm2po.snapp;


public interface AppListener {
	void onLocationChanged(double lat, double lon);
	void onRouteChanged(long[] geometry);
	void onRouteLost(double lat, double lon);
}
