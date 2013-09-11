package de.cm.osm2po.snapp;

import static de.cm.osm2po.snapp.MainApplication.getSdDir;
import static de.cm.osm2po.snapp.MarkerType.GPS_MARKER;
import static de.cm.osm2po.snapp.MarkerType.SOURCE_MARKER;
import static de.cm.osm2po.snapp.MarkerType.TARGET_MARKER;
import static de.cm.osm2po.snapp.Utils.readLongs;
import static de.cm.osm2po.snapp.Utils.readString;
import static de.cm.osm2po.snapp.Utils.writeLongs;
import static de.cm.osm2po.snapp.Utils.writeString;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.mapsforge.android.maps.MapActivity;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.core.GeoPoint;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;
import android.widget.ToggleButton;
import de.cm.osm2po.sd.routing.SdTouchPoint;

public class MainActivity extends MapActivity
implements MarkerSelectListener, GpsListener {
	
	private RoutesLayer routesLayer;
	private MarkersLayer markersLayer;
	private SdTouchPoint tpSource, tpTarget;
	private long[] geometry;
	private ToggleButton tglBikeCar;
	private MainApplication app;
	private MapView mapView;

	private final static File STATE_FILE = new File(getSdDir(), "snapp.state");
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		app = (MainApplication) this.getApplication();
		app.setGpsListener(this);
		app.setTtsQuiet(false);
		
		tglBikeCar = (ToggleButton) findViewById(R.id.tglBikeCar);
		tglBikeCar.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {route();}
		});
		
		mapView = (MapView) findViewById(R.id.mapView);
		mapView.setClickable(true);
		mapView.setBuiltInZoomControls(true);
		mapView.setMapFile(app.getMapFile());
		mapView.getController().setZoom(15);
		
		routesLayer = new RoutesLayer();
		mapView.getOverlays().add(routesLayer);

		markersLayer = new MarkersLayer(this);
		mapView.getOverlays().add(markersLayer);

        tpSource = null;
        tpTarget = null;
        
        restoreInstanceState();
	}

	@Override
    protected void onDestroy() {
    	super.onDestroy();
    	app.setGpsListener(null); // UnChain
    	saveInstanceState();
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (item.getItemId() == R.id.menu_sleep) {
	    	app.setTtsQuiet(true);
			finish();
		}
		return true;
	}
	
	@Override
	public void onLocationChanged(double lat, double lon) {
		GeoPoint geoPoint = new GeoPoint(lat, lon);
		markersLayer.moveMarker(GPS_MARKER, geoPoint);
	}

	@Override
	public void onMarkerSelected(MarkerType markerType) {
		GeoPoint geoPoint = markersLayer.getLastTouchPosition();
		double lat = geoPoint.getLatitude();
		double lon = geoPoint.getLongitude();
		
		if (GPS_MARKER == markerType) {
			app.onGps(lat, lon); // Simulation
			return;
		}
		
		SdTouchPoint tp = SdTouchPoint.create(app.getGraph(), (float)lat, (float)lon);
		
		if (markerType == SOURCE_MARKER) {
			tpSource = tp;
		} else if (markerType == TARGET_MARKER) {
			tpTarget = tp;
		}

		if (tp != null) {
			geoPoint = new GeoPoint(tp.getLat(), tp.getLon());
			markersLayer.moveMarker(markerType, geoPoint);
		} else {
			toast("Point not found");
		}
		
		route();
	}
	
	private void route() {
		if (tpSource != null && tpTarget != null) {
			try {
				geometry = app.route(tpSource, tpTarget, tglBikeCar.isChecked()); 
				routesLayer.drawRoute(geometry);
			} catch (Throwable t) {
				toast("Routing error\n" + t.getMessage());
			}
		} else {
			toast("Please set Source and Target");
		}
	}
	
	private void toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	}

	
    /****************** SaveInstance secure via File ************************/
    
	private void saveInstanceState() {
		try {
			OutputStream os = new FileOutputStream(STATE_FILE);
			DataOutputStream dos = new DataOutputStream(os);
			dos.writeInt(mapView.getMapPosition().getZoomLevel());
			GeoPoint center = mapView.getMapPosition().getMapCenter(); 
			dos.writeDouble(center.getLatitude());
			dos.writeDouble(center.getLongitude());
			writeString(tpSource == null ? null : tpSource.getKey(), dos);
			writeString(tpTarget == null ? null : tpTarget.getKey(), dos);
			writeLongs(geometry, dos);

		} catch (Exception e) {
			toast(e.getMessage());
		}
	}
	
	private void restoreInstanceState() {
		try {
			if (!STATE_FILE.exists()) return;
			
			InputStream is = new FileInputStream(STATE_FILE);
			DataInputStream dis = new DataInputStream(is);
			
			int zoomLevel = dis.readInt();
			mapView.getController().setZoom(zoomLevel);
			
			double centerLat = dis.readDouble();
			double centerLon = dis.readDouble();
			GeoPoint center = new GeoPoint(centerLat, centerLon);
			mapView.setCenter(center);
			
	    	String tpSourceKey = readString(dis);
	    	if (tpSourceKey != null) {
	    		tpSource = SdTouchPoint.create(app.getGraph(), tpSourceKey);
				GeoPoint geoPoint = new GeoPoint(tpSource.getLat(), tpSource.getLon());
				markersLayer.moveMarker(SOURCE_MARKER, geoPoint);
	    	}
	    	
	    	String tpTargetKey = readString(dis);
	    	if (tpTargetKey != null) {
	    		tpTarget = SdTouchPoint.create(app.getGraph(), tpTargetKey);
				GeoPoint geoPoint = new GeoPoint(tpTarget.getLat(), tpTarget.getLon());
				markersLayer.moveMarker(TARGET_MARKER, geoPoint);
	    	}
	    	
	    	geometry = readLongs(dis);
	    	routesLayer.drawRoute(geometry); // null safe
			
			is.close();
			
		} catch (Exception e) {
			toast(e.getMessage());
		}
	}

}
