package de.cm.osm2po.snapp;

import static de.cm.osm2po.snapp.MarkerType.TOUCH_MARKER;

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

	public MarkersLayer(Activity activity) {
		super(null);
		this.activity = activity;
		
    	MarkerType[] mtes = MarkerType.values();
    	int nMtes = mtes.length;
    	markers = new OverlayItem[nMtes];
    	for (int i = 0; i < nMtes; i++) {
    		MarkerType mte = mtes[i];
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

	public void moveMarker(MarkerType mte, GeoPoint geoPoint, float rotate) {
		OverlayItem overlayItem = markers[mte.getIndex()];
		Drawable drawable = overlayItem.getMarker();
		if (drawable instanceof RotatableBitmapDrawable) {
			((RotatableBitmapDrawable) drawable).rotate(rotate);
		}
		moveMarker(mte, geoPoint);
	}
	
	public void moveMarker(MarkerType mte, GeoPoint geoPoint) {
		markers[mte.getIndex()].setPoint(geoPoint);
		requestRedraw();
	}
	
	public GeoPoint getMarkerPosition(MarkerType mte) {
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
		MarkerSelectDialog dlg = new MarkerSelectDialog(this);
		dlg.show(activity.getFragmentManager(), "dlg_marker");
		return true;
	}

	@Override
	public void onMarkerSelected(MarkerType mte) {
		if (selectMarkerListener != null) {
			selectMarkerListener.onMarkerSelected(mte);
		} else if (activity instanceof MarkerSelectListener) {
			((MarkerSelectListener)activity).onMarkerSelected(mte);
		} else {
			Log.e("SNAPP", "Please provide a Listener");
		}
	}

}
