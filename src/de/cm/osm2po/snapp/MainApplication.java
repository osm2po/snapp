package de.cm.osm2po.snapp;

import static android.speech.tts.TextToSpeech.QUEUE_ADD;
import static de.cm.osm2po.sd.guide.SdMessageResource.MSG_INF_ROUTE_CALC;
import static de.cm.osm2po.sd.routing.SdGeoUtils.toCoord;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
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

public class MainApplication extends Application implements LocationListener, OnInitListener, OnCompletionListener, OnUtteranceCompletedListener {

	private LocationManager gps;
	private TextToSpeech tts;
	private Set<String> ttsTrackSet;
	private SdGraph graph;
	private SdGuide guide;
	private SdPath path;
	private File mapFile; // Mapsforge
	private AppListener appListener; // there is only one
	private Thread routingThread;
	private SdRouter router;
	private boolean bikeMode;
	private boolean gpsListening;
	private long[] jitters; // Coords, nano-long coded
	private int nJitters;
	private MediaPlayer mediaPlayer;
	private int mpNextStart;
	private boolean quiet;
	
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
    	tts.setOnUtteranceCompletedListener(this);
    	ttsTrackSet = registerTracks();
    	
    	mediaPlayer = MediaPlayer.create(this, R.raw.silence);
    	mediaPlayer.setOnCompletionListener(this);
    	
        graph = new SdGraph(new File(getSdDir(), "snapp.gpt"));
        mapFile = new File(getSdDir(), "snapp.map");
        
        jitters = new long[10];
    }
    
    private Set<String> registerTracks() {
    	Set<String> trackSet = new HashSet<String>();
    	File dir = new File(getSdDir(), "tracks");
    	for (String key : SdMessageResource.getKeySet()) {
    		String audioFileName = key.replace('.', '_') + ".mp3";
    		File audioFile = new File(dir, audioFileName);
    		if (audioFile.exists()) {
    			tts.addSpeech(key, audioFile.getAbsolutePath());
    			trackSet.add(key);
    		}
    	}
    	return trackSet;
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

		speak(MSG_INF_ROUTE_CALC.getMessage(), null);

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
		if (null == router || bikeMode != this.bikeMode) {
			this.bikeMode = bikeMode;
			router = new SdRouter(graph, cacheFile, 0, 1.1, !bikeMode, !bikeMode);
		}

		path = router.findPath(tpSource, tpTarget, dirHint);
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
    
    public GeoPoint getLastPosition() {
    	return new GeoPoint(lastLat, lastLon);
    }
    
    public int getKmh() {
    	return guide.getKmh();
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
					SdLocation loc = SdLocation.snap(path, lat, lon);
	                appListener.onLocate(loc);
	                if (loc.getJitter() < 50) {
	                	if (mediaPlayer.isPlaying()) {
	                		mediaPlayer.pause();
	                	}
	                	
	                	nJitters = 0;

	                    SdMessage[] msgs = guide.lookAhead(loc.getMeter());
	                    if (msgs != null) {
	                    	if (tts.isSpeaking()) tts.stop();

	                    	int nMsgs = msgs.length;
	                    	for (int i = 0; i < nMsgs; i++) {
	                    		String key = msgs[i].getKey();
	                    		String msg = msgs[i].getMessage();
	                    		String utteranceId = i == nMsgs-1 ? "[EOS]" : null;
	                    		if (ttsTrackSet.contains(key)) { 
	                    			speak(key, utteranceId);
	                    		} else {
	                    			speak(msg, utteranceId);
	                    		}
	                    	}
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
    		if (tts.isSpeaking()) tts.stop();
    		speak("Error " + t.getMessage(), null);
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

	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
		if (quiet) {
			if (tts.isSpeaking()) tts.stop();
			if (mediaPlayer.isPlaying()) mediaPlayer.stop();
		}
	}
	
	public boolean isQuiet() {
		return this.quiet;
	}
	
	public void speak(String msg, String utteranceId) {
		if (this.quiet) return;
		mpPause();
		
		HashMap<String, String> ttsAlarmMap = null;
		if (utteranceId != null) {
			ttsAlarmMap = new HashMap<String, String>();
			ttsAlarmMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
		}
		
		tts.speak(msg, QUEUE_ADD, ttsAlarmMap);
	}
	
	@Override
	public void onInit(int status) {
	}


	@Override
	public void onUtteranceCompleted(String utteranceId) {
		if ("[EOS]".equals(utteranceId)) {
        	guide.resetPrioTrace();
		}
	}
	
	/***************************** MediaPlayer *****************************/
	
	private void mpPause() {
		if (mediaPlayer.isPlaying()) {
			mediaPlayer.pause();
		}
	}
	
	private void mpPlay() {
		if (this.quiet) return;
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
