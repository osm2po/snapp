package de.cm.osm2po.snapp;

import de.cm.osm2po.sd.guide.SdLocation;

public interface AppListener {
	void onLocationChanged(double lat, double lon, float bearing);
	void onLocate(SdLocation loc);
	void onRouteChanged(long[] geometry);
	void onRouteLost(long[] jitterCoords);
}
