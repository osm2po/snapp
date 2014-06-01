package de.cm.osm2po.snapp;

public interface AppListener {
	void onGpsSignal(double lat, double lon, float bearing);
	void onPathPositionChanged(double lat, double lon, float bearing);
	void onRouteChanged();
	void onRouteLost();
}
