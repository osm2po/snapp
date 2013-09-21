package de.cm.osm2po.snapp;

import java.io.File;
import java.util.Arrays;

import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import de.cm.osm2po.sd.guide.SdAdvicePoint;
import de.cm.osm2po.sd.guide.SdGuide;
import de.cm.osm2po.sd.guide.SdGuide.Locator;
import de.cm.osm2po.sd.routing.SdGraph;
import de.cm.osm2po.sd.routing.SdPath;
import de.cm.osm2po.sd.routing.SdRouter;
import de.cm.osm2po.sd.routing.SdTouchPoint;

public class MainApplication extends Application implements LocationListener, OnInitListener {

	private LocationManager gps;
	private TextToSpeech tts;
	private SdGraph graph;
	private SdGuide guide;
	private File mapFile; // Mapsforge
	private AppListener appListener; // there is only one
	private boolean ttsQuiet;
	private Thread routingThread;
	
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
    	gps.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 5, this);

    	tts = new TextToSpeech(this, this);
    	ttsQuiet = false;
    	
        graph = new SdGraph(new File(getSdDir(), "snapp.gpt"));
        mapFile = new File(getSdDir(), "snapp.map");
    }
    
    public File getMapFile() {return mapFile;}
    public SdGraph getGraph() {return graph;}
    
    @Override
    public void onTerminate() {
    	super.onTerminate();
    	graph.close();
    }
    
    public void setAppListener(AppListener appListener) {
    	this.appListener = appListener;
    };
    
    public void setTtsQuiet(boolean ttsQuiet) {
    	this.ttsQuiet = ttsQuiet;
    }
    
    /******************************** SD **********************************/
    
    public boolean isBusy() {
    	return routingThread != null && routingThread.isAlive();
    }
    
    public void route(final SdTouchPoint tpSource, final SdTouchPoint tpTarget,
    		final boolean bikeMode) throws IllegalStateException {
    	if (isBusy()) throw new IllegalStateException("Routing in progress");

    	speak("Routing");
    	routingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					long[] geometry = routeAsync(tpSource, tpTarget, bikeMode);
					Thread.sleep(1000);
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
    
    private long[] routeAsync(SdTouchPoint tpSource, SdTouchPoint tpTarget, boolean bikeMode) {
		File cacheFile = new File(getCacheDir(), "osm2po" + System.currentTimeMillis() + ".sd");
		SdRouter sdRouter = new SdRouter();
		
		guide = null;
		SdPath path = sdRouter.findPath(graph,
				cacheFile, tpSource, tpTarget, bikeMode, 1000, 1.1);
		
		if (path != null) {
			guide = new SdGuide(path);
			return createGeometry(path);
		}
		return null;
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

    public void onGps(double lat, double lon) {
		if (appListener != null) {
			appListener.onLocationChanged(lat, lon);
			if (guide != null) {
                Locator locator = this.guide.locate(lat, lon);
                if (locator.getJitterMeters() < 50) {
                	for (SdAdvicePoint ap : guide.lookAhead(locator.getMeterStone(), 15)) { 
                		speak(ap.toString());
                	}
                }
			}
		}
    }
    
	@Override
	public void onLocationChanged(Location location) {
		double lat = location.getLatitude();
		double lon = location.getLongitude();
		onGps(lat, lon);
	}

	@Override
	public void onProviderDisabled(String provider) {
		speak("G P S on");
	}

	@Override
	public void onProviderEnabled(String provider) {
		speak("G P S off");
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		speak("G P S Status " + status);
	}

    
	/******************************** TTS *********************************/

	public void speak(String msg) {
		if (!ttsQuiet) tts.speak(msg, TextToSpeech.QUEUE_ADD, null);
	}
	
	@Override
	public void onInit(int status) {
	}

}
