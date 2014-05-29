package de.cm.osm2po.snapp;

import static android.speech.tts.TextToSpeech.QUEUE_ADD;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_INF_ROUTE_CALC;

import java.io.File;
import java.util.Arrays;
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
import de.cm.osm2po.sd.guide.SdEvent;
import de.cm.osm2po.sd.guide.SdForecast;
import de.cm.osm2po.sd.guide.SdGuide;
import de.cm.osm2po.sd.guide.SdLocation;
import de.cm.osm2po.sd.guide.SdMessage;
import de.cm.osm2po.sd.guide.SdMessageResource;
import de.cm.osm2po.sd.routing.SdGraph;
import de.cm.osm2po.sd.routing.SdPath;
import de.cm.osm2po.sd.routing.SdRouter;
import de.cm.osm2po.sd.routing.SdTouchPoint;

public class MainApplication extends Application implements LocationListener, OnInitListener {

	private LocationManager gps;
	private TextToSpeech tts;
	private SdGraph graph;
	private SdGuide guide;
	private SdPath path;
	private File mapFile; // Mapsforge
	private AppListener appListener; // there is only one
	private Thread routingThread;
	private SdRouter router;
	private int nJitters;

	private boolean quietMode;
	private boolean bikeMode;
	private boolean autoPanningMode;
	private boolean naviMode;
	
	private double lastLat;
	private double lastLon;
	
	public final static File getSdDir() {
        File sdcard = Environment.getExternalStorageDirectory();
        File sdDir = new File(sdcard, "Snapp");
        sdDir.mkdir();
        return sdDir;
	}
	
    @Override
    public void onCreate() {
    	super.onCreate();

    	gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    	gps.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, this);
    	Location loc = gps.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    	if (loc != null) {
	    	this.lastLat = loc.getLatitude();
	    	this.lastLon = loc.getLongitude();
    	}

    	tts = new TextToSpeech(this, this);
    	registerTracks();
    	
        graph = new SdGraph(new File(getSdDir(), "snapp.gpt"));
        mapFile = new File(getSdDir(), "snapp.map");
    }
    
    private void registerTracks() {
    	File dir = new File(getSdDir(), "tracks");
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
    
    @Override
    public void onTerminate() {
    	super.onTerminate();
    	tts.shutdown();
    	graph.close();
    }
    
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
    	return gps.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
    
    public boolean isAutoPanningMode() {
    	return autoPanningMode;
    }
    
    public void setAutoPanningMode(boolean autoPanningMode) {
    	this.autoPanningMode = autoPanningMode;
    }
    
    public boolean isNaviMode() {
    	return naviMode;
    }
    
    public void setNaviMode(boolean naviMode) {
    	if (naviMode) activateGps();
    	if (guide != null && naviMode) guide.reset();
    	this.naviMode = naviMode;
    }
    
    public boolean isBikeMode() {
    	return bikeMode;
    }
    
    public void setBikeMode(boolean bikeMode) {
    	this.bikeMode = bikeMode;
    }
    
    /******************************** SD **********************************/
    
    public boolean isRouterBusy() {
    	return routingThread != null && routingThread.isAlive();
    }
    
    public void route(final SdTouchPoint tpSource, final SdTouchPoint tpTarget)
    		throws IllegalStateException {
    	if (isRouterBusy()) throw new IllegalStateException("Routing in progress");

		speak(MSG_INF_ROUTE_CALC.getMessage());

    	routingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					long[] geometry = routeAsync(tpSource, tpTarget);
					if (appListener != null) {
						appListener.onRouteChanged(geometry);
					}
				} catch (Exception e) {
					if (appListener != null) {
						appListener.onRouteChanged(null);
					}
				}
			}
		});
    	routingThread.start();
    }
    
    private long[] routeAsync(SdTouchPoint tpSource, SdTouchPoint tpTarget) {
		File cacheFile = new File(getCacheDir(), "osm2po.sd");
		if (null == router)	router = new SdRouter(graph, cacheFile);
		path = router.findPath(tpSource, tpTarget, 0, 1.1, !bikeMode, !bikeMode);
		guide = (null == path) ? null : new SdGuide(SdForecast.create(SdEvent.create(path)));
		
		return createGeometry();
    }
    
    public boolean isGuiding() {
    	return guide != null;
    }
    
    private long[] createGeometry() {
        if (null == path) return null;
        int nEdges = path.getNumberOfEdges();
        
        long[] points = new long[1000];
        int nPoints = 0;
        // TODO eliminate redundant points at connections
        for (int e = 0; e < nEdges; e++) {
        	long[] coords = path.fetchGeometry(e);
            
            for (int j = 0; j < coords.length; j++) {
            	if (nPoints == points.length) // ArrayOverflow
            		points = Arrays.copyOf(points, nPoints * 2);
            	
            	points[nPoints++] = coords[j];
            }
        }
        return Arrays.copyOf(points, nPoints);
    }

    
    /******************************** GPS *********************************/
    
    public GeoPoint getLastGpsPosition() {
    	return new GeoPoint(lastLat, lastLon);
    }
    
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
    		lastLat = lat;
    		lastLon = lon;
    		
			if (appListener != null) {
				
				if (isNavigateBusy || isRouterBusy()) return;
				isNavigateBusy = true;

				if (guide != null) {
					SdLocation loc = SdLocation.snap(path, lat, lon);
	                if (loc.getJitter() < 50) {
	                	nJitters = 0;

	                	appListener.onPositionChanged(loc.getLat(), loc.getLon(), loc.getBearing());

	                    SdMessage[] msgs = guide.lookAhead(loc.getMeter(), tts.isSpeaking());
	                    if (msgs != null) {
	                    	for (SdMessage msg : msgs) speak(msg.getMessage());
	                    }
	                    
	                } else {
                		if (++nJitters > 10) {
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
	public void onLocationChanged(Location location) {
		double lat = location.getLatitude();
		double lon = location.getLongitude();
		float bearing = location.getBearing();
		if (appListener != null) 
			appListener.onGpsChanged(lat, lon, bearing);
		if (naviMode) navigate(lat, lon);
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	/******************************** TTS *********************************/

	public void setQuietMode(boolean quietMode) {
		this.quietMode = quietMode;
		if (quietMode) {
			if (tts.isSpeaking()) tts.stop();
		}
	}
	
	public boolean isQuietMode() {
		return this.quietMode;
	}
	
	public void speak(String msg) {
		if (this.quietMode) return;
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

}
