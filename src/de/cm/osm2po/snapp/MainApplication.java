package de.cm.osm2po.snapp;

import static android.speech.tts.TextToSpeech.QUEUE_ADD;
import static android.speech.tts.TextToSpeech.QUEUE_FLUSH;
import static de.cm.osm2po.sd.guide.SdMessage.INF_ROUTE_CALC;
import static de.cm.osm2po.sd.routing.SdGeoUtils.toCoord;

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
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import de.cm.osm2po.sd.guide.SdAdvice;
import de.cm.osm2po.sd.guide.SdHumbleGuide;
import de.cm.osm2po.sd.guide.SdHumbleGuide.Locator;
import de.cm.osm2po.sd.routing.SdGraph;
import de.cm.osm2po.sd.routing.SdPath;
import de.cm.osm2po.sd.routing.SdRouter;
import de.cm.osm2po.sd.routing.SdTouchPoint;

public class MainApplication extends Application implements LocationListener, OnInitListener, OnCompletionListener {

	private LocationManager gps;
	private TextToSpeech tts;
	private SdGraph graph;
	private SdHumbleGuide guide;
	private File mapFile; // Mapsforge
	private AppListener appListener; // there is only one
	private Thread routingThread;
	private SdRouter sdRouter;
	private boolean bikeMode;
	private boolean gpsListening;
	private long[] jitters; // Coords, nano-long coded
	private int nJitters;
	private MediaPlayer mediaPlayer;
	private int mpNextStart;
	
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
    	tts.addSpeech("[signal]", getClass().getPackage().getName(), R.raw.signal);
    	
    	mediaPlayer = MediaPlayer.create(this, R.raw.silence);
    	mediaPlayer.setOnCompletionListener(this);
    	
        graph = new SdGraph(new File(getSdDir(), "snapp.gpt"));
        mapFile = new File(getSdDir(), "snapp.map");
        
        jitters = new long[10];
    }
    
    public File getMapFile() {return mapFile;}
    public SdGraph getGraph() {return graph;}
    
    @Override
    public void onTerminate() {
    	super.onTerminate();
    	tts.shutdown();
    	mediaPlayer.release();
    	graph.close();
    }
    
    public void setAppListener(AppListener appListener) {
    	this.appListener = appListener;
    };
    
    public void setGpsListening(boolean gpsListening) {
    	this.gpsListening = gpsListening;
    	if (!isGpsAvailable()) {
    		Intent gpsSettings = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    		gpsSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		startActivity(gpsSettings);    		
//			Intent intent = new Intent("android.location.GPS_ENABLED_CHANGE");
//			intent.putExtra("enabled", true);
//			sendBroadcast(intent);
    	}
    }
    
    public boolean isGpsListening() {
    	return gpsListening;
    }
    
    public boolean isGpsAvailable() {
    	return gps.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
    
    /******************************** SD **********************************/
    
    public boolean isCalculatingRoute() {
    	return routingThread != null && routingThread.isAlive();
    }
    
    public void route(final SdTouchPoint tpSource, final SdTouchPoint tpTarget,
    		final boolean bikeMode, final long dirHint) throws IllegalStateException {
    	if (isCalculatingRoute()) throw new IllegalStateException("Routing in progress");

		speak(INF_ROUTE_CALC.getMessageText(), false);

    	routingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					long[] geometry = routeAsync(tpSource, tpTarget, bikeMode, dirHint);
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
    
    private long[] routeAsync(SdTouchPoint tpSource, SdTouchPoint tpTarget,
    		boolean bikeMode, long dirHint) {
		File cacheFile = new File(getCacheDir(), "osm2po.sd");
		if (null == sdRouter || bikeMode != this.bikeMode) {
			this.bikeMode = bikeMode;
			sdRouter = new SdRouter(graph, cacheFile, 0, 1.1, !bikeMode, !bikeMode);
		}

		SdPath path = sdRouter.findPath(tpSource, tpTarget, dirHint);
		guide = null == path ? null : new SdHumbleGuide(path);
		
		return createGeometry(path);
    }
    
    public boolean isGuiding() {
    	return guide != null;
    }
    
    private long[] createGeometry(SdPath path) {
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
    
    public GeoPoint getLastPosition() {
    	return new GeoPoint(lastLat, lastLon);
    }

    private boolean isOnGpsBusy;
    public void onGps(double lat, double lon, float bearing) {
    	lastLat = lat;
    	lastLon = lon;
    	
    	if (isOnGpsBusy || isCalculatingRoute()) return;
    	isOnGpsBusy = true;
    	
    	try {
			if (appListener != null) {
				appListener.onLocationChanged(lat, lon, bearing);
				if (guide != null) {
	                Locator locator = this.guide.locate(lat, lon);
	                appListener.onLocate(locator);
	                if (locator.getJitterMeters() < 50) {
	                	if (mediaPlayer.isPlaying()) {
	                		mediaPlayer.pause();
	                	}
	                	
	                	nJitters = 0;
	                    // alarm range
	                    int ms = locator.getMeterStone();
	                    int kmh = locator.getKmh();
	                    SdAdvice adv = guide.lookAhead(ms, kmh);
	                    if (adv != null) {
	                    	String msg = adv.getMessageText();
	                    	if (msg.contains("Ziel") || msg.contains("Target")) {
	                    		speak("[signal]", false);
	                    		speak(msg, false);
	                    	} else {
	                    		speak(adv.getMessageText(), true);
	                    	}
	                    } else if (guide.getSilence() > 30) {
	                    	mpPlay(); 
	                    }
	                    
	                } else {
	                	if (nJitters == jitters.length) {
	                		nJitters = 0;
	                		this.appListener.onRouteLost(jitters);
	                	} else {
	                		jitters[nJitters++] = toCoord(lat, lon);
	                	}
	                }
				}
			}

    	} catch (Throwable t) {
    		speak("Error " + t.getMessage(), true);
		} finally {
			isOnGpsBusy = false;
		}
    }
    
	@Override
	public void onLocationChanged(Location location) {
		double lat = location.getLatitude();
		double lon = location.getLongitude();
		if (gpsListening) onGps(lat, lon, location.getBearing());
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

	public void speak(String msg, boolean now) {
		int queueMode = now ? QUEUE_FLUSH : QUEUE_ADD;
		mpPause();
		tts.speak(msg, queueMode, null);
	}
	
	@Override
	public void onInit(int status) {
	}

	/***************************** MediaPlayer *****************************/
	
	private void mpPause() {
		if (mediaPlayer.isPlaying()) {
			mediaPlayer.pause();
		}
	}
	
	private void mpPlay() {
		int ts = (int) (System.currentTimeMillis() / 1000);
		if (mpNextStart < ts && !mediaPlayer.isPlaying() && !tts.isSpeaking()) {
			mediaPlayer.start();
		}
	}
	
	@Override
	public void onCompletion(MediaPlayer mp) {
		mpNextStart = (int) (System.currentTimeMillis() / 1000) + 300; // in 5 Min.
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
