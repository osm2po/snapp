package de.cm.osm2po.snapp;

import static de.cm.osm2po.snapp.Marker.TOUCH_MARKER;

import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.overlay.ArrayItemizedOverlay;
import org.mapsforge.android.maps.overlay.OverlayItem;
import org.mapsforge.core.GeoPoint;

import android.app.Activity;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

public class MarkersLayer extends ArrayItemizedOverlay implements MarkerSelectListener {

	private Activity activity;
	private OverlayItem[] markers; // Erlaubt den Zugriff via MarkerTypeEnum
	private MarkerSelectListener selectMarkerListener;
	private MarkerSelectDialog markerSelectDialog;

	public MarkersLayer(Activity activity) {
		super(null);
		this.activity = activity;
		markerSelectDialog = new MarkerSelectDialog();
		
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
    		markers[i] = new OverlayItem(null, mte.getTitle(), null, drawable);
    		addItem(markers[i]);
    	}
	}

	public void moveMarker(Marker mte, GeoPoint geoPoint, float rotate) {
		OverlayItem overlayItem = markers[mte.getIndex()];
		Drawable drawable = overlayItem.getMarker();
		if (drawable instanceof RotatableBitmapDrawable) {
			((RotatableBitmapDrawable) drawable).rotate(rotate);
		}
		moveMarker(mte, geoPoint);
	}
	
	public void moveMarker(Marker mte, GeoPoint geoPoint) {
		markers[mte.getIndex()].setPoint(geoPoint);
		requestRedraw();
	}
	
	public GeoPoint getMarkerPosition(Marker mte) {
		return markers[mte.getIndex()].getPoint();
	}

	public GeoPoint getLastTouchPosition() {
		return markers[TOUCH_MARKER.getIndex()].getPoint();
	}
	
	public MarkersLayer setListener(MarkerSelectListener listener) {
		this.selectMarkerListener = listener;
		return this;
	}

	@Override
	public boolean onLongPress(GeoPoint geoPoint, MapView mapView) {
		moveMarker(TOUCH_MARKER, geoPoint);
		showMarkerSelectDialog();
		return true;
	}
	
	public void showMarkerSelectDialog() {
		markerSelectDialog.show(activity.getFragmentManager(), "dlg_marker");
	}

	@Override
	public void onMarkerSelected(Marker mte) {
		if (selectMarkerListener != null) {
			selectMarkerListener.onMarkerSelected(mte);
		} else if (activity instanceof MarkerSelectListener) {
			((MarkerSelectListener)activity).onMarkerSelected(mte);
		} else {
			Log.e("SNAPP", "Please provide a Listener");
		}
	}

}
