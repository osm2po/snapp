package de.cm.osm2po.snapp;

import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_ERR_POINT_FIND;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_ERR_ROUTE_CALC;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_ERR_ROUTE_LOST;
import static de.cm.osm2po.snapp.MainApplication.getSdDir;
import static de.cm.osm2po.snapp.Marker.GPS_MARKER;
import static de.cm.osm2po.snapp.Marker.HOME_MARKER;
import static de.cm.osm2po.snapp.Marker.POS_MARKER;
import static de.cm.osm2po.snapp.Marker.SOURCE_MARKER;
import static de.cm.osm2po.snapp.Marker.TARGET_MARKER;
import static de.cm.osm2po.snapp.Marker.TOUCH_MARKER;
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
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.widget.ToggleButton;
import de.cm.osm2po.sd.routing.SdTouchPoint;

public class MainActivity extends MapActivity
implements MarkerSelectListener, AppListener {
	
	private RoutesLayer routesLayer;
	private MarkersLayer markersLayer;
	private SdTouchPoint tpSource, tpTarget;
	private long[] geometry;
	private ToggleButton tglCarOrBike;
	private ToggleButton tglNaviOrEdit;
	private ToggleButton tglPanOrHold;
	private ToggleButton tglToneOrQuiet;
	private TextView lblSpeed;
	private EditText txtAddress;
	private MainApplication app;
	private MapView mapView;
	private long nGpsCalls;
	private ProgressDialog progressDialog;
	
	private final static File STATE_FILE = new File(getSdDir(), "snapp.state");
	private static final int STATE_FILE_VERSION = 3;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		progressDialog = new ProgressDialog(this, R.style.StyledDialog);
		progressDialog.setMessage("Calculating Route...");

		app = (MainApplication) this.getApplication();
		if (app.isRouterBusy()) progressDialog.show();
		
		tglCarOrBike = (ToggleButton) findViewById(R.id.tglCarOrBike);
		tglCarOrBike.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {app.setBikeMode(!tglCarOrBike.isChecked()); route();}
		});
		
		tglNaviOrEdit = (ToggleButton) findViewById(R.id.tglNaviOrEdit);
		tglNaviOrEdit.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				app.setNaviMode(tglNaviOrEdit.isChecked());
				tglPanOrHold.setChecked(tglNaviOrEdit.isChecked());
				app.setAutoPanningMode(tglPanOrHold.isChecked());
			}
		});

		tglToneOrQuiet = (ToggleButton) findViewById(R.id.tglToneOrQuiet);
		tglToneOrQuiet.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				app.setQuietMode(!tglToneOrQuiet.isChecked());
			}
		});

		tglPanOrHold = (ToggleButton) findViewById(R.id.tglPanOrHold);
		tglPanOrHold.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				app.setAutoPanningMode(tglPanOrHold.isChecked());
			}
		});
		
		txtAddress = (EditText) findViewById(R.id.txtAddress);
		txtAddress.setImeActionLabel("Find", EditorInfo.IME_ACTION_SEARCH);
		txtAddress.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH) {
					findAddress(txtAddress.getText().toString());
				}
				return false;
			}
		});
		
		
		mapView = (MapView) findViewById(R.id.mapView);
		mapView.setClickable(true);
		mapView.setBuiltInZoomControls(true);
		mapView.setMapFile(app.getMapFile());
		mapView.getController().setZoom(15);
		
		lblSpeed = (TextView) findViewById(R.id.lblSpeed);

		routesLayer = new RoutesLayer();
		mapView.getOverlays().add(routesLayer);

		markersLayer = new MarkersLayer(this);
		mapView.getOverlays().add(markersLayer);

        tpSource = null;
        tpTarget = null;
        
        restoreInstanceState();

        app.setAppListener(this);
        
        if (!app.isGuiding()) route();
	}

	@Override
    protected void onDestroy() {
    	super.onDestroy();
    	app.setAppListener(null); // UnChain
    	saveInstanceState();
    }
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// Empty for portrait-mode
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		tglCarOrBike.setChecked(!app.isBikeMode());
		tglToneOrQuiet.setChecked(!app.isQuietMode());
		tglNaviOrEdit.setChecked(app.isNaviMode());
		tglPanOrHold.setChecked(app.isAutoPanningMode());
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (item.getItemId() == R.id.menu_nav_home) {
			GeoPoint gp1 = app.getLastGpsPosition();
			GeoPoint gp2 = markersLayer.getMarkerPosition(HOME_MARKER);
			if (gp1 != null && gp2 != null) {
				tpSource = null;
				tpTarget = null;
				markersLayer.moveMarker(Marker.TOUCH_MARKER, gp1);
				onMarkerSelected(SOURCE_MARKER);
				markersLayer.moveMarker(Marker.TOUCH_MARKER, gp2);
				onMarkerSelected(TARGET_MARKER);
			}
			
		}
		return true;
	}
	
	@Override
	public void onGpsChanged(double lat, double lon, float bearing) {
		GeoPoint geoPoint = new GeoPoint(lat, lon);
		markersLayer.moveMarker(GPS_MARKER, geoPoint, bearing);
		if (app.isAutoPanningMode()) {
			if (nGpsCalls == 0) mapView.setCenter(geoPoint);
			if (++nGpsCalls > 10) nGpsCalls = 0;
		}
	}
	
	@Override
	public void onPositionChanged(double lat, double lon, float bearing) {
		GeoPoint geoPoint = new GeoPoint(lat, lon);
		lblSpeed.setText(app.getKmh() + " km/h");
		markersLayer.moveMarker(POS_MARKER, geoPoint, bearing);
	}

	@Override
	public void onMarkerSelected(Marker markerType) {
		GeoPoint geoPoint = markersLayer.getLastTouchPosition();
		double lat = geoPoint.getLatitude();
		double lon = geoPoint.getLongitude();
		
		if (GPS_MARKER == markerType) {
			app.navigate(lat, lon, 0); // Simulation
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
			app.speak(toast(MSG_ERR_POINT_FIND.getMessage()));
		}
		
		route();
	}

	@Override
	public void onRouteChanged(long[] geometry) {
		this.geometry = geometry;
		routesLayer.drawRoute(geometry);
		if (null == geometry) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					app.speak(toast(MSG_ERR_ROUTE_CALC.getMessage()));
				}
			});
		}
		progressDialog.dismiss();
	}
	
	private void route() {
		if (app.isRouterBusy()) return;
		if (tpSource != null && tpTarget != null) {
			try {
				progressDialog.show();
				lblSpeed.setText(null);
				app.route(tpSource, tpTarget);
			} catch (Throwable t) {
				toast("Error\n" + t.getMessage());
			}
		} else {
//			toast(ERR_POINT_SET.getMessage());
		}
	}

	@Override
	public void onRouteLost() {
		app.speak(MSG_ERR_ROUTE_LOST.getMessage());
		markersLayer.moveMarker(TOUCH_MARKER, app.getLastGpsPosition());
		onMarkerSelected(SOURCE_MARKER);
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
			
			dos.writeBoolean(tglCarOrBike.isChecked());
			dos.writeBoolean(tglNaviOrEdit.isChecked());
			dos.writeBoolean(tglPanOrHold.isChecked());
			dos.writeBoolean(tglToneOrQuiet.isChecked());
			
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
				is.close();
				return;
			}
			
			tglCarOrBike.setChecked(dis.readBoolean());
			tglNaviOrEdit.setChecked(dis.readBoolean());
			tglPanOrHold.setChecked(dis.readBoolean());
			tglToneOrQuiet.setChecked(dis.readBoolean());

			app.setBikeMode(!tglCarOrBike.isChecked());
			app.setNaviMode(tglNaviOrEdit.isChecked());
			app.setAutoPanningMode(tglPanOrHold.isChecked());
			app.setQuietMode(!tglToneOrQuiet.isChecked());
			
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
	    	
	    	if (app.isGuiding()) routesLayer.drawRoute(geometry); // null safe
			
			is.close();
			
		} catch (Exception e) {
			toast("Error: " + e.getMessage());
		}
	}
	
	private boolean findAddress (String address) {
		try {
			GeoPoint gpAddress = app.findAddress(address);
			if (gpAddress != null) {
				mapView.setCenter(gpAddress);
				mapView.getController().setZoom(14);
				markersLayer.moveMarker(TOUCH_MARKER, gpAddress);
				markersLayer.showMarkerSelectDialog();
				return true;
			} else {
				toast("Address not found");
			}
		} catch (Exception e) {
			toast(e.getMessage());
		}
		return false;
	}


}
