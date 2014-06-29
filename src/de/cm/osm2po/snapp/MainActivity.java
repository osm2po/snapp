package de.cm.osm2po.snapp;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_ERR_POINT_FIND;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_ERR_ROUTE_CALC;
import static de.cm.osm2po.snapp.Marker.ALERT_MARKER;
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
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
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

	private final static int CONTACT_SELECTED = 4711;
	private final static int ACTION_MOVE = 1;
	private final static int ACTION_GOTO = 2;
	
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

		progressDialog = new ProgressDialog(this, R.style.StyledDialog) {
			@Override
			public void onBackPressed() {
				app.cancelRouteCalculation();
				progressDialog.dismiss();
				toast("Calculation cancelled");
				toast(app.getStatistic());
			}
		};
		progressDialog.setMessage("Calculating Route...");
		progressDialog.setCancelable(false);

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
		
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String msg = (String) extras.get("sms_msg");
			String num = (String) extras.get("sms_num");
			Double lat = (Double) extras.get("sms_lat");
			Double lon = (Double) extras.get("sms_lon");
			if (msg != null && num != null && lat != null && lon != null) {
				toast("Position received from " + num + ": " + lat + "," + lon);
				markersLayer.moveMarker(TOUCH_MARKER, new GeoPoint(lat, lon));
				markersLayer.moveMarker(ALERT_MARKER, new GeoPoint(lat, lon));
				appState.setMapZoom(15);
				appState.setPanMode(false);
				appState.setMapPos(new GeoPoint(lat, lon));
				markerSelectDialog.show(ACTION_MOVE, getFragmentManager());
			}
		}

		restoreViewState();
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
			GeoPoint gp1 = appState.getGpsPos();
			GeoPoint gp2 = markersLayer.getMarkerPosition(HOME_MARKER);
			if (gp1 != null && gp2 != null) {
				appState.setTarget(null);
				markersLayer.moveMarker(Marker.TOUCH_MARKER, gp1);
				onMarkerAction(SOURCE_MARKER, ACTION_MOVE); // fake
				markersLayer.moveMarker(Marker.TOUCH_MARKER, gp2);
				onMarkerAction(TARGET_MARKER, ACTION_MOVE); // fake
			}
			break;
			
		case R.id.menu_goto_marker: 
			markerSelectDialog.show(ACTION_GOTO, getFragmentManager());
			break;
			
		case R.id.menu_sms_pos:
// TODO this code is not optimal
//			this syntax has been taken from my book but doesnt work in callback
//		    Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
//		    startActivityForResult(intent, CONTACT_SELECTED);

//			this syntax is from the net
		    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		    intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
		    startActivityForResult(intent, CONTACT_SELECTED);
		    break;
		}
		
		return true;
	}
	

	@Override
	public void onGpsSignal(double lat, double lon, float bearing) {
		GeoPoint geoPoint = new GeoPoint(lat, lon);
		markersLayer.moveMarker(GPS_MARKER, geoPoint, bearing);
		appState.setGpsPos(geoPoint);
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
	public void onPositionRequest(GeoPoint geoPoint) {
		if (!appState.isNavMode()) {
			markerSelectDialog.show(ACTION_MOVE, getFragmentManager());
		}
	}

	@Override
	public void onMarkerAction(Marker marker, int action) {
		switch (action) {
		case ACTION_MOVE:
			moveMarker(marker);
			break;
		default:
			gotoMarker(marker);
			break;
		}
	}
	
	public void gotoMarker(Marker marker) {
		GeoPoint geoPoint = markersLayer.getMarkerPosition(marker);
		if (geoPoint != null) {
			mapView.setCenter(geoPoint);
		} else {
			toast("Marker not yet set");
		}
	}

	public void moveMarker(Marker marker) {
		GeoPoint geoPoint = markersLayer.getLastTouchPosition();
		double lat = geoPoint.getLatitude();
		double lon = geoPoint.getLongitude();

		if (GPS_MARKER == marker) {
			markersLayer.moveMarker(GPS_MARKER, geoPoint);
			appState.setGpsPos(geoPoint);
			app.navigate(lat, lon); // Simulation
			return;
		}

		if (HOME_MARKER == marker) {
			markersLayer.moveMarker(marker, geoPoint);
			return;
		}

		SdTouchPoint tp = SdTouchPoint.create(
				app.getGraph(), (float)lat, (float)lon, !appState.isBikeMode());

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
		markersLayer.moveMarker(TOUCH_MARKER, appState.getGpsPos()); // fake
		onMarkerAction(SOURCE_MARKER, ACTION_MOVE); // Fake
	}


	private String toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
		return msg;
	}

	@Override
	public void onRouteChanged() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				progressDialog.dismiss();
				SdPath path = appState.getPath();
				if (null == path) {
					app.speak(toast(MSG_ERR_ROUTE_CALC.getMessage()));
				}
				toast(app.getStatistic());
				routesLayer.drawPath(app.getGraph(), path);
			}
		});
	}
	
	private void saveViewState() {
		GeoPoint gpMap = mapView.getMapPosition().getMapCenter();
		appState.setMapPos(gpMap);
		GeoPoint gpHome = markersLayer.getMarkerPosition(HOME_MARKER);
		appState.setHomePos(gpHome);
		GeoPoint gpGps = markersLayer.getMarkerPosition(GPS_MARKER);
		appState.setGpsPos(gpGps);
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
		GeoPoint gpGps = appState.getGpsPos();
		if (gpGps != null) markersLayer.moveMarker(GPS_MARKER, gpGps);

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
				markerSelectDialog.show(ACTION_MOVE, getFragmentManager());
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO this code is not optimal
		if (CONTACT_SELECTED == requestCode && resultCode != 0) {
		    if (data != null) {
		        Uri uri = data.getData();

		        if (uri != null) {
		            Cursor c = null;
		            try {
		                c = getContentResolver().query(uri, new String[]{ 
		                            ContactsContract.CommonDataKinds.Phone.NUMBER,  
		                            ContactsContract.CommonDataKinds.Phone.TYPE },
		                        null, null, null);

		                if (c != null && c.moveToFirst()) {
		                    String number = c.getString(0);
		                    //int type = c.getInt(1);
		                    toast("Position sent to " + number);
		                    app.smsGeoPosition(number);
		                }
		            } finally {
		                if (c != null) {
		                    c.close();
		                }
		            }
		        }
		    }
		}
	}


}
