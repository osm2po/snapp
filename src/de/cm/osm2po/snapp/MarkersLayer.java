package de.cm.osm2po.snapp;

import static de.cm.osm2po.snapp.Marker.TOUCH_MARKER;

import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.overlay.ArrayItemizedOverlay;
import org.mapsforge.android.maps.overlay.OverlayItem;
import org.mapsforge.core.GeoPoint;

import android.app.Activity;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class MarkersLayer extends ArrayItemizedOverlay {

	private MarkerEditListener listener;
	private OverlayItem[] markers; // Erlaubt den Zugriff via MarkerTypeEnum

	public MarkersLayer(MarkerEditListener listener, Activity activity) {
		super(null);
		this.listener = listener;
		
    	Marker[] mtes = Marker.values();
    	int nMtes = mtes.length;
    	markers = new OverlayItem[nMtes];
    	for (int i = 0; i < nMtes; i++) {
    		Marker mte = mtes[i];
    		Drawable drawable = activity.getResources().getDrawable(mte.getIconId());
    		if (mte.isBottomCenter()) {
    			ArrayItemizedOverlay.boundCenterBottom(drawable);
    		} else {
    			if (drawable instanceof BitmapDrawable) {
    				drawable = new RotatableBitmapDrawable(((BitmapDrawable) drawable).getBitmap());
    			}
    			ArrayItemizedOverlay.boundCenter(drawable);
    		}
    		markers[i] = new OverlayItem(null, null, null, drawable);
    		addItem(markers[i]);
    	}
	}

	public void moveMarker(Marker mte, GeoPoint geoPoint, float rotate) {
		OverlayItem overlayItem = markers[mte.ordinal()];
		Drawable drawable = overlayItem.getMarker();
		if (drawable instanceof RotatableBitmapDrawable) {
			((RotatableBitmapDrawable) drawable).rotate(rotate);
		}
		moveMarker(mte, geoPoint);
	}
	
	public void moveMarker(Marker mte, GeoPoint geoPoint) {
		markers[mte.ordinal()].setPoint(geoPoint);
		requestRedraw();
	}
	
	public GeoPoint getMarkerPosition(Marker mte) {
		return markers[mte.ordinal()].getPoint();
	}

	public GeoPoint getLastTouchPosition() {
		return markers[TOUCH_MARKER.ordinal()].getPoint();
	}
	
	@Override
	public boolean onLongPress(GeoPoint geoPoint, MapView mapView) {
		moveMarker(TOUCH_MARKER, geoPoint);
		listener.onPositionRequest(geoPoint);
		return true;
	}

}
