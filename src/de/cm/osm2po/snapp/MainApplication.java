package de.cm.osm2po.snapp;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.speech.tts.TextToSpeech.QUEUE_ADD;
import static android.telephony.TelephonyManager.CALL_STATE_IDLE;
import static android.telephony.TelephonyManager.CALL_STATE_OFFHOOK;
import static android.telephony.TelephonyManager.CALL_STATE_RINGING;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_ERR_ROUTE_LOST;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_INF_ROUTE_CALC;

import java.io.File;
import java.util.List;

import org.mapsforge.core.GeoPoint;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import de.cm.osm2po.sd.guide.SdEvent;
import de.cm.osm2po.sd.guide.SdForecast;
import de.cm.osm2po.sd.guide.SdGuide;
import de.cm.osm2po.sd.guide.SdLocation;
import de.cm.osm2po.sd.guide.SdMessage;
import de.cm.osm2po.sd.guide.SdMessageResource;
import de.cm.osm2po.sd.routing.SdGraph;
import de.cm.osm2po.sd.routing.SdPath;
import de.cm.osm2po.sd.routing.SdRouter;

public class MainApplication extends Application implements LocationListener, OnInitListener {
	
	private final SmsManager smsMan = SmsManager.getDefault();

	private LocationManager locman;
	private TextToSpeech tts;
	private SdGraph graph;
	private SdGuide guide;
	private File mapFile; // Mapsforge
	private AppListener appListener; // there is only one
	private Thread routingThread;
	private SdRouter router;
	private int nJitters;
	private boolean gpsProviderActive;

	private AppState appState;
	
	public final static File getAppDir() {
        File sdcard = Environment.getExternalStorageDirectory();
        File sdDir = new File(sdcard, "Snapp");
        if (!sdDir.exists()) sdDir.mkdir();
        return sdDir;
	}
	
    @Override
    public void onCreate() {
    	super.onCreate();

    	toast("Starting Application");
    	
    	File pathCacheFile = new File(getCacheDir(), "snapp.tmp");

    	graph = new SdGraph(new File(getAppDir(), "snapp.gpt"));
    	mapFile = new File(getAppDir(), "snapp.map");
    	appState = new AppState().restoreAppState(graph.getId());
    	router = new SdRouter(graph, pathCacheFile);

    	SdPath path = appState.getPath();
    	boolean bikeMode = appState.isBikeMode();
		guide = (null == path) ? null : new SdGuide(
				SdForecast.create(SdEvent.create(graph, path, !bikeMode, !bikeMode)));
    	
    	locman = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

    	// Important precheck. Emulator does not provide NETWORK, eg.
    	if (locman.getProvider(LocationManager.GPS_PROVIDER) != null)
    		locman.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    	if (locman.getProvider(LocationManager.NETWORK_PROVIDER) != null)
    		locman.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
    	
    	// String locmans = locman.getProviders(false).toString();
    	// toast("LocationProviders:\n" + locmans);

    	tts = new TextToSpeech(this, this);
    	registerTracks();

    	TelephonyManager tm = (TelephonyManager) getApplicationContext()
    			.getSystemService(Context.TELEPHONY_SERVICE);
        int events = PhoneStateListener.LISTEN_CALL_STATE;
        tm.listen(new PhoneListener(), events);
        
    	startNavi();
    }
    
    @Override
    public void onTerminate() {
    	// CAUTION! onTerminate only fires on emulators!
    	super.onTerminate();
    	tts.shutdown();
    	graph.close();
    }
    
    protected boolean saveAppState() {
    	return appState.saveAppState(graph.getId());
    }

    private void registerTracks() {
    	File dir = new File(getAppDir(), "tracks");
    	for (SdMessageResource msg : SdMessageResource.values()) {
    		String key = msg.getKey();
    		if (null == key) continue;
    		
    		String audioFileName = key.replace('.', '_') + ".mp3";
    		File audioFile = new File(dir, audioFileName);
    		if (audioFile.exists()) {
    			tts.addSpeech(msg.getMessage(), audioFile.getAbsolutePath());
    		}
    	}
    }
    
    public File getMapFile() {return mapFile;}
    public SdGraph getGraph() {return graph;}
    
    public void setAppListener(AppListener appListener) {
    	this.appListener = appListener;
    };
    
    public void activateGps() {
    	if (!isGpsOn()) {
    		Intent gpsSettings = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    		gpsSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		startActivity(gpsSettings);    		
//			Intent intent = new Intent("android.location.GPS_ENABLED_CHANGE");
//			intent.putExtra("enabled", true);
//			sendBroadcast(intent);
    	}
    }
    
    public boolean isGpsOn() {
    	return locman.isProviderEnabled(GPS_PROVIDER);
    }
    
    public AppState getAppState() {
    	return appState;
    }
    
    public void startNavi() {
    	if (!appState.isNavMode()) return;
    	activateGps();
    	if (guide != null) guide.reset();
    }
    
    /******************************** SD **********************************/
    
    public boolean isRouterBusy() {
    	return routingThread != null && routingThread.isAlive();
    }
    
    public boolean runRouteCalculation() {
    	
    	if (isRouterBusy() || null == appState.getSource() || null == appState.getTarget()) {
    		return false;
    	}
    	
		speak(MSG_INF_ROUTE_CALC.getMessage());

    	routingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {calculateRoute();} catch (Exception e) {}
			}
		});
    	routingThread.setPriority(Thread.MAX_PRIORITY);
    	routingThread.start();
    	return true;
    }

    private void calculateRoute() {
    	boolean bikeMode = appState.isBikeMode();
		SdPath path = router.findPath(
				appState.getSource(), appState.getTarget(), !bikeMode);
		appState.setPath(path);
		guide = (null == path) ? null : new SdGuide(
				SdForecast.create(SdEvent.create(graph, path, !bikeMode, !bikeMode)));
		if (appListener != null) appListener.onRouteChanged();
    }
    
    public void cancelRouteCalculation() {
    	router.cancel();
    }
    
    /******************************** GPS *********************************/
    
    public int getKmh() {
    	return guide.getKmh();
    }
    
    private boolean isNavigateBusy;
    /**
     * This method will be called by real GPS and Simulation.
     * @param lat double Latitude
     * @param lon double Longitude
     * @param bearing float bearing
     */
    public void navigate(double lat, double lon) {
    	try {
    		appState.setGpsPos(new GeoPoint(lat, lon));
    		
			if (appListener != null) {
				
				if (isNavigateBusy || isRouterBusy()) return;
				isNavigateBusy = true;

				if (guide != null) {
					SdLocation loc = SdLocation.snap(graph, appState.getPath(), lat, lon);
	                if (loc.getJitter() < 50) {
	                	nJitters = 0;

	                	appListener.onPathPositionChanged(loc.getLat(), loc.getLon(), loc.getBearing());

	                    SdMessage[] msgs = guide.lookAhead(loc.getMeter(), tts.isSpeaking());
	                    if (msgs != null) {
	                    	if (appState.isQuietMode()) {
	                    		String s = "";
	                    		for (SdMessage msg : msgs) s += msg.getMessage() + " ";
	                    		toast(s);
	                    	} else {
	                    		for (SdMessage msg : msgs) speak(msg.getMessage());
	                    	}
	                    }
	                    
	                } else {
                		if (++nJitters > 10) {
                			speak(MSG_ERR_ROUTE_LOST.getMessage());
                			this.appListener.onRouteLost();
                			nJitters = 0;
                		}
	                }
				}
			}

    	} catch (Throwable t) {
    		if (tts.isSpeaking()) tts.stop();
    		speak("Error " + t.getMessage());
    		
		} finally {
			isNavigateBusy = false;
		}
    }
    
	@Override
	public void onLocationChanged(Location location) { // from GPS
		// WTF Workaround - onStatusChanged() does not fire on my phone
		String callingProvider = location.getProvider();
		if (GPS_PROVIDER.equals(callingProvider)) {
			if (!gpsProviderActive) speak("G P S fixed");
			gpsProviderActive = true;
		} else if (gpsProviderActive) {
			return; // Prefer GPS- to NETWORK-Provider
		}
		
		double lat = location.getLatitude();
		double lon = location.getLongitude();
		float bearing = location.getBearing();
		if (appListener != null) 
			appListener.onGpsSignal(lat, lon, bearing);
		if (appState.isNavMode()) navigate(lat, lon);
	}

	@Override
	public void onProviderDisabled(String provider) {
		// toast("Provider " + provider + " disabled");
		if (GPS_PROVIDER.equals(provider)) {
			gpsProviderActive = false;
		}
	}

	@Override
	public void onProviderEnabled(String provider) {
		// toast("Provider " + provider + " enabled");
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// WTF This Event does not fire on my Mobile
	}

	/******************************** TTS *********************************/

	public void setQuietMode(boolean quietMode) {
		appState.setQuietMode(quietMode);
		if (quietMode) {
			if (tts.isSpeaking()) tts.stop();
		}
	}
	
	public boolean isQuietMode() {
		return appState.isQuietMode();
	}
	
	public void speak(String msg) {
		if (appState.isQuietMode()) return;
		if (null == msg) return;
		
		tts.speak(msg, QUEUE_ADD, null);
	}
	
	@Override
	public void onInit(int status) {
	}


	/************************** Address-Finder  ***************************/
	
	public GeoPoint findAddress(String address) throws Exception {
		Geocoder coder = new Geocoder(this);
		List<Address> addresses = coder.getFromLocationName(address,5);
		if (addresses != null && addresses.size() > 0) {
			Address adr = addresses.get(0);
			return new GeoPoint(adr.getLatitude(), adr.getLongitude());
		}
		return null;
	}

	/************************** Toast *************************************/
	
	private String toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
		return msg;
	}
	
	/*********************** Phone observer *******************************/
	
	private class PhoneListener extends PhoneStateListener {
		private boolean restoreTone;
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			switch (state) {
			case CALL_STATE_RINGING:
				// speak("Telefon " + incomingNumber);
				break;
			case CALL_STATE_OFFHOOK:
				appState.saveAppState(graph.getId());
				restoreTone = !isQuietMode();
				setQuietMode(true);
				break;
			case CALL_STATE_IDLE:
				if (restoreTone) setQuietMode(false);
				restoreTone = false;
				break;
			} 
		}
		
	}
	
	/*********************** SMS-GeoPosition-Sender *****************************/

	public void smsGeoPosition(String mobileNumber) {
		GeoPoint gp = appState.getGpsPos();
		
		if (gp != null) {
			String smsMsg = "snapp:geo:" + gp.getLatitude() + "," + gp.getLongitude();
			smsMan.sendTextMessage(mobileNumber, null, smsMsg, null, null);
		} else {
			toast("No current Position to send via SMS");
		}
	}
	

}
