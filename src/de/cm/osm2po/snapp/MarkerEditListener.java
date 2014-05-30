package de.cm.osm2po.snapp;

import org.mapsforge.core.GeoPoint;

public interface MarkerEditListener {
	void onPositionRequest(GeoPoint geoPoint);
	void onMarkerSpecified(Marker marker);
}
