package de.cm.osm2po.snapp;

import de.cm.osm2po.sd.guide.SdSampleGuide.Locator;


public interface AppListener {
	void onLocationChanged(double lat, double lon);
	void onLocate(Locator loc);
	void onRouteChanged(long[] geometry);
	void onRouteLost(long[] jitterCoords);
}
