package de.cm.osm2po.snapp;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_ERR_POINT_FIND;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_ERR_ROUTE_CALC;
import static de.cm.osm2po.snapp.Marker.GPS_MARKER;
import static de.cm.osm2po.snapp.Marker.HOME_MARKER;
import static de.cm.osm2po.snapp.Marker.POS_MARKER;
import static de.cm.osm2po.snapp.Marker.SOURCE_MARKER;
import static de.cm.osm2po.snapp.Marker.TARGET_MARKER;
import static de.cm.osm2po.snapp.Marker.TOUCH_MARKER;

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
import de.cm.osm2po.sd.routing.SdPath;
import de.cm.osm2po.sd.routing.SdTouchPoint;

public class MainActivity extends MapActivity
implements MarkerEditListener, AppListener {

	private MainApplication app;
	private AppState appState;
	private MapView mapView;
	private RoutesLayer routesLayer;
	private MarkersLayer markersLayer;
	private ToggleButton tglCarOrBike;
	private ToggleButton tglNaviOrEdit;
	private ToggleButton tglPanOrHold;
	private ToggleButton tglToneOrQuiet;
	private TextView lblSpeed;
	private EditText txtAddress;
	private long nGpsCalls;
	private ProgressDialog progressDialog;
	private MarkerSelectDialog markerSelectDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		toast("Starting Activity");

		progressDialog = new ProgressDialog(this, R.style.StyledDialog);
		progressDialog.setMessage("Calculating Route...");

		markerSelectDialog = new MarkerSelectDialog();

		app = (MainApplication) this.getApplication();
		appState = app.getAppState();
		if (app.isRouterBusy()) progressDialog.show();

		tglCarOrBike = (ToggleButton) findViewById(R.id.tglCarOrBike);
		tglCarOrBike.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				appState.setBikeMode(!tglCarOrBike.isChecked());
				route();
			}
		});

		tglNaviOrEdit = (ToggleButton) findViewById(R.id.tglNaviOrEdit);
		tglNaviOrEdit.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				boolean navMode = tglNaviOrEdit.isChecked();
				appState.setNavMode(navMode);
				tglPanOrHold.setChecked(navMode);
				appState.setPanMode(navMode);
				txtAddress.setVisibility(navMode ? INVISIBLE : VISIBLE);
				app.startNavi();
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
				appState.setPanMode(tglPanOrHold.isChecked());
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

		markersLayer = new MarkersLayer(this, this);
		mapView.getOverlays().add(markersLayer);

		app.setAppListener(this);
		
		restoreViewState();
		
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String gp = (String) extras.get("snapp:geo");
			if (gp != null) {
				toast(gp);
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		saveViewState();
		app.setAppListener(null); // Important! Decouple from App!
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// Empty for portrait-mode
	}

	@Override
	public void onModeChanged() {
		tglCarOrBike.setChecked(!appState.isBikeMode());
		tglToneOrQuiet.setChecked(!appState.isQuietMode());
		tglNaviOrEdit.setChecked(appState.isNavMode());
		tglPanOrHold.setChecked(appState.isPanMode());
		
		txtAddress.setVisibility(appState.isNavMode() ? INVISIBLE : VISIBLE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		onModeChanged();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_nav_home:
			GeoPoint gp1 = appState.getLastPos();
			GeoPoint gp2 = markersLayer.getMarkerPosition(HOME_MARKER);
			if (gp1 != null && gp2 != null) {
				appState.setTarget(null);
				markersLayer.moveMarker(Marker.TOUCH_MARKER, gp1);
				onMarkerAction(SOURCE_MARKER); // fake
				markersLayer.moveMarker(Marker.TOUCH_MARKER, gp2);
				onMarkerAction(TARGET_MARKER); // fake
			}
			break;
		case R.id.menu_sms_pos:
			app.smsGeoPosition("+49 163 7600600");
		}
		
		return true;
	}

	@Override
	public void onGpsSignal(double lat, double lon, float bearing) {
		GeoPoint geoPoint = new GeoPoint(lat, lon);
		markersLayer.moveMarker(GPS_MARKER, geoPoint, bearing);
		if (appState.isPanMode()) {
			if (nGpsCalls == 0) mapView.setCenter(geoPoint);
			if (++nGpsCalls > 10) nGpsCalls = 0;
		}
	}

	@Override
	public void onPathPositionChanged(double lat, double lon, float bearing) {
		GeoPoint geoPoint = new GeoPoint(lat, lon);
		lblSpeed.setText(app.getKmh() + " km/h");
		markersLayer.moveMarker(POS_MARKER, geoPoint, bearing);
	}

	@Override
	public void onMarkerAction(Marker marker) {
		GeoPoint geoPoint = markersLayer.getLastTouchPosition();
		double lat = geoPoint.getLatitude();
		double lon = geoPoint.getLongitude();

		if (GPS_MARKER == marker) {
			markersLayer.moveMarker(GPS_MARKER, geoPoint);
			app.navigate(lat, lon); // Simulation
			return;
		}

		if (HOME_MARKER == marker) {
			markersLayer.moveMarker(marker, geoPoint);
			return;
		}

		SdTouchPoint tp = SdTouchPoint.create(app.getGraph(), (float)lat, (float)lon);

		if (marker == SOURCE_MARKER) {
			appState.setSource(tp);
		} else if (marker == TARGET_MARKER) {
			appState.setTarget(tp);
		}

		if (tp != null) {
			geoPoint = new GeoPoint(tp.getLat(), tp.getLon());
			markersLayer.moveMarker(marker, geoPoint);
		} else {
			app.speak(toast(MSG_ERR_POINT_FIND.getMessage()));
		}

		route();
	}

	private void route() {
		if (app.runRouteCalculation()) {
			progressDialog.show();
			lblSpeed.setText(null);
		}
	}

	@Override
	public void onRouteLost() {
		markersLayer.moveMarker(TOUCH_MARKER, appState.getLastPos()); // fake
		onMarkerAction(SOURCE_MARKER); // Fake
	}


	private String toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
		return msg;
	}

	@Override
	public void onRouteChanged() {
		SdPath path = appState.getPath();
		routesLayer.drawPath(app.getGraph(), path);
		if (null == path) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					app.speak(toast(MSG_ERR_ROUTE_CALC.getMessage()));
				}
			});
		}
		progressDialog.dismiss();
	}
	
	private void saveViewState() {
		GeoPoint gpMap = mapView.getMapPosition().getMapCenter();
		appState.setMapPos(gpMap);
		GeoPoint gpHome = markersLayer.getMarkerPosition(HOME_MARKER);
		appState.setHomePos(gpHome);
		appState.setMapZoom(mapView.getMapPosition().getZoomLevel());

		app.saveAppState();
	}
	
	private void restoreViewState() {

		int zoom = appState.getMapZoom();
		if (zoom > 0)  mapView.getController().setZoom(zoom);
		GeoPoint gpMap = appState.getMapPos();
		if (gpMap != null) mapView.setCenter(gpMap);
		GeoPoint gpHome = appState.getHomePos();
		if (gpHome != null) markersLayer.moveMarker(HOME_MARKER, gpHome);

		tglCarOrBike.setChecked(!appState.isBikeMode());
		tglToneOrQuiet.setChecked(!appState.isQuietMode());
		tglPanOrHold.setChecked(appState.isPanMode());
		tglNaviOrEdit.setChecked(appState.isNavMode());

		SdTouchPoint source = appState.getSource();
		if (source != null) {
			GeoPoint geoPoint = new GeoPoint(source.getLat(), source.getLon());
			markersLayer.moveMarker(SOURCE_MARKER, geoPoint);
		}

		SdTouchPoint target = appState.getTarget();
		if (target != null) {
			GeoPoint geoPoint = new GeoPoint(target.getLat(), target.getLon());
			markersLayer.moveMarker(TARGET_MARKER, geoPoint);
		}

		routesLayer.drawPath(app.getGraph(), appState.getPath());
	}


	private boolean findAddress (String address) {
		try {
			GeoPoint gpAddress = app.findAddress(address);
			if (gpAddress != null) {
				mapView.setCenter(gpAddress);
				mapView.getController().setZoom(14);
				markersLayer.moveMarker(TOUCH_MARKER, gpAddress);
				markerSelectDialog.show(getFragmentManager(), "dlg_marker");
				return true;
			} else {
				toast("Address not found");
			}
		} catch (Exception e) {
			toast(e.getMessage());
		}
		return false;
	}

	@Override
	public void onPositionRequest(GeoPoint geoPoint) {
		if (!appState.isNavMode()) {
			markerSelectDialog.show(getFragmentManager(), "dlg_marker");
		}
	}

}
