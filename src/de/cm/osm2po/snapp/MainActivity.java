package de.cm.osm2po.snapp;

import static de.cm.osm2po.sd.guide.SdMessage.ERR_POINT_FIND;
import static de.cm.osm2po.sd.guide.SdMessage.ERR_ROUTE_CALC;
import static de.cm.osm2po.sd.guide.SdMessage.ERR_ROUTE_LOST;
import static de.cm.osm2po.sd.routing.SdGeoUtils.toLat;
import static de.cm.osm2po.sd.routing.SdGeoUtils.toLon;
import static de.cm.osm2po.snapp.MainApplication.getSdDir;
import static de.cm.osm2po.snapp.MarkerType.GPS_MARKER;
import static de.cm.osm2po.snapp.MarkerType.HOME_MARKER;
import static de.cm.osm2po.snapp.MarkerType.SOURCE_MARKER;
import static de.cm.osm2po.snapp.MarkerType.TARGET_MARKER;
import static de.cm.osm2po.snapp.MarkerType.TOUCH_MARKER;
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

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import de.cm.osm2po.sd.guide.SdHumbleGuide.Locator;
import de.cm.osm2po.sd.routing.SdTouchPoint;

public class MainActivity extends MapActivity
implements MarkerSelectListener, AppListener {
	
	private RoutesLayer routesLayer;
	private MarkersLayer markersLayer;
	private SdTouchPoint tpSource, tpTarget;
	private long[] geometry;
	private ToggleButton tglBikeCar;
	private ToggleButton tglGps;
	private TextView lblSpeed;
	private MainApplication app;
	private MapView mapView;
	private long nGpsCalls;
	ProgressDialog progressDialog;
	
	private final static File STATE_FILE = new File(getSdDir(), "snapp.state");
	private static final int STATE_FILE_VERSION = 2;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		app = (MainApplication) this.getApplication();
		if (app.isCalculatingRoute()) progressDialog =  ProgressDialog.show(
				this, null, "Please wait", true, false);

		
		tglBikeCar = (ToggleButton) findViewById(R.id.tglBikeCar);
		tglBikeCar.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {route(0);}
		});
		
		tglGps = (ToggleButton) findViewById(R.id.tglGps);
		tglGps.setChecked(app.isGpsListening());
		tglGps.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				app.setGpsListening(tglGps.isChecked());
			}
		});
		
		lblSpeed = (TextView) findViewById(R.id.lblSpeed);
		
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

        app.setAppListener(this);
        
        if (!app.isGuiding()) route(0);
	}

	@Override
    protected void onDestroy() {
    	super.onDestroy();
    	// FIXME handle running calculation
    	app.setAppListener(null); // UnChain
    	saveInstanceState();
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (item.getItemId() == R.id.menu_nav_home) {
			GeoPoint gp1 = app.getLastPosition();
			GeoPoint gp2 = markersLayer.getMarkerPosition(HOME_MARKER);
			if (gp1 != null && gp2 != null) {
				tpSource = null;
				tpTarget = null;
				markersLayer.moveMarker(MarkerType.TOUCH_MARKER, gp1);
				onMarkerSelected(SOURCE_MARKER);
				markersLayer.moveMarker(MarkerType.TOUCH_MARKER, gp2);
				onMarkerSelected(TARGET_MARKER);
			}
			
		}
		return true;
	}
	
	@Override
	public void onLocationChanged(double lat, double lon) {
		GeoPoint geoPoint = new GeoPoint(lat, lon);
		markersLayer.moveMarker(GPS_MARKER, geoPoint);
		if (nGpsCalls == 0) mapView.setCenter(geoPoint);
		if (++nGpsCalls > 10) nGpsCalls = 0;
	}
	
	@Override
	public void onLocate(Locator loc) {
		int kmh = loc.getKmh();
		if (kmh > 200) {
			lblSpeed.setText("too fast");
		} else if (kmh < 1) {
			lblSpeed.setText("too slow");
		} else {
			lblSpeed.setText(kmh + " km/h");
		}
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
		
		if (HOME_MARKER == markerType) {
			markersLayer.moveMarker(markerType, geoPoint);
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
			app.speak(toast(ERR_POINT_FIND.getMessageText()), false);
		}
		
		route(0);
	}

	@Override
	public void onRouteChanged(long[] geometry) {
		this.geometry = geometry;
		routesLayer.drawRoute(geometry);
		if (null == geometry) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					app.speak(toast(ERR_ROUTE_CALC.getMessageText()), false);
				}
			});
		}
		if (progressDialog != null) progressDialog.dismiss();
	}

	private void route(long dirHint) {
		if (app.isCalculatingRoute()) return;
		if (tpSource != null && tpTarget != null) {
			try {
				progressDialog =  ProgressDialog.show(
						this, "Calculating route...", "Please wait", true, false);
				app.route(tpSource, tpTarget, tglBikeCar.isChecked(), dirHint);
			} catch (Throwable t) {
				toast("Error\n" + t.getMessage());
			}
		} else {
//			toast(ERR_POINT_SET.getMessage());
		}
	}

	@Override
	public void onRouteLost(long[] jitterCoords) {
		app.speak(ERR_ROUTE_LOST.getMessageText(), false);
		int n = jitterCoords.length;
		long c =  jitterCoords[n-1]; // last jitter is new Source-TouchPoint
		tpSource = SdTouchPoint.create(app.getGraph(), (float)toLat(c), (float)toLon(c));
		if (tpSource != null) {
			markersLayer.moveMarker(TOUCH_MARKER, new GeoPoint(toLat(c), toLon(c)));
			markersLayer.moveMarker(SOURCE_MARKER, new GeoPoint(tpSource.getLat(), tpSource.getLon()));
		}
		route(jitterCoords[n-2]); // last but one jitter as direction hint);
	}

	
	private String toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
		return msg;
	}

	
    /****************** SaveInstance secure via File ************************/
    
	
	private void saveInstanceState() {
		try {
			OutputStream os = new FileOutputStream(STATE_FILE);
			DataOutputStream dos = new DataOutputStream(os);
			dos.writeInt(STATE_FILE_VERSION);
			dos.writeBoolean(tglBikeCar.isChecked());
			
			dos.writeInt(mapView.getMapPosition().getZoomLevel());
			GeoPoint center = mapView.getMapPosition().getMapCenter(); 
			dos.writeDouble(center.getLatitude());
			dos.writeDouble(center.getLongitude());

			GeoPoint geoPointHome = markersLayer.getMarkerPosition(HOME_MARKER);
			boolean hasHome = null != geoPointHome;
			dos.writeBoolean(hasHome);
			if (hasHome) {
				dos.writeDouble(geoPointHome.getLatitude());
				dos.writeDouble(geoPointHome.getLongitude());
			}
			
			writeString(tpSource == null ? null : tpSource.getKey(), dos);
			writeString(tpTarget == null ? null : tpTarget.getKey(), dos);
			writeLongs(geometry, dos);
			

		} catch (Exception e) {
			toast("Error: " + e.getMessage());
		}
	}
	
	private void restoreInstanceState() {
		try {
			if (!STATE_FILE.exists()) return;
			
			InputStream is = new FileInputStream(STATE_FILE);
			DataInputStream dis = new DataInputStream(is);
			
			int saveInstanceVersion = dis.readInt();
			if (saveInstanceVersion != STATE_FILE_VERSION) {
				toast("Wrong SaveInstance-Version");
				return;
			}
			
			tglBikeCar.setChecked(dis.readBoolean());
			
			int zoomLevel = dis.readInt();
			mapView.getController().setZoom(zoomLevel);
			
			double centerLat = dis.readDouble();
			double centerLon = dis.readDouble();
			GeoPoint center = new GeoPoint(centerLat, centerLon);
			mapView.setCenter(center);

			boolean hasHome = dis.readBoolean();
			if (hasHome) {
				double homeLat = dis.readDouble();
				double homeLon = dis.readDouble();
				GeoPoint geoPointHome = new GeoPoint(homeLat, homeLon);
    			markersLayer.moveMarker(HOME_MARKER, geoPointHome);
			}
			
	    	String tpSourceKey = readString(dis);
	    	if (tpSourceKey != null) {
	    		tpSource = SdTouchPoint.create(app.getGraph(), tpSourceKey);
	    		if (null == tpSource) {
	    			toast("Could not restore source");
	    		} else {
	    			GeoPoint geoPoint = new GeoPoint(tpSource.getLat(), tpSource.getLon());
	    			markersLayer.moveMarker(SOURCE_MARKER, geoPoint);
	    		}
	    	}
	    	
	    	String tpTargetKey = readString(dis);
	    	if (tpTargetKey != null) {
	    		tpTarget = SdTouchPoint.create(app.getGraph(), tpTargetKey);
	    		if (null == tpTarget) {
	    			toast("Could not restore target");
	    		} else {
					GeoPoint geoPoint = new GeoPoint(tpTarget.getLat(), tpTarget.getLon());
					markersLayer.moveMarker(TARGET_MARKER, geoPoint);
	    		}
	    	}
	    	
	    	geometry = readLongs(dis);
	    	
	    	if (app.isGuiding()) 
	    		routesLayer.drawRoute(geometry); // null safe
			
			is.close();
			
		} catch (Exception e) {
			toast("Error: " + e.getMessage());
		}
	}

}
