package de.cm.osm2po.snapp;

import static de.cm.osm2po.snapp.MainApplication.getAppDir;
import static de.cm.osm2po.snapp.Utils.readString;
import static de.cm.osm2po.snapp.Utils.writeString;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;
import de.cm.osm2po.sd.routing.SdGraph;
import de.cm.osm2po.sd.routing.SdPath;
import de.cm.osm2po.sd.routing.SdTouchPoint;

/**
 * Persistable container which reflects the instance state of the app.
 * 
 * @author carsten
 */
public class AppState {

	private final static String STATE_FILE_NAME = "snapp.state";
	private static final int STATE_FILE_VERSION = 4;

	private transient boolean restored;
	
	private boolean quietMode;
	private boolean bikeMode;
	private boolean panMode;
	private boolean navMode;

	private double lastLat;
	private double lastLon;
	private double homeLat;
	private double homeLon;
	
	private int mapZoom;
	private double mapLat;
	private double mapLon;

	private SdPath path;
	private SdTouchPoint source;
	private SdTouchPoint target;

	public boolean isQuietMode() {return quietMode;}
	public void setQuietMode(boolean quietMode) {this.quietMode = quietMode;}
	public boolean isBikeMode() {return bikeMode;}
	public void setBikeMode(boolean bikeMode) {this.bikeMode = bikeMode;}
	public boolean isPanMode() {return panMode;}
	public void setPanMode(boolean panMode) {this.panMode = panMode;}
	public boolean isNavMode() {return navMode;}
	public void setNavMode(boolean navMode) {this.navMode = navMode;}
	public double getLastLat() {return lastLat;}
	public void setLastLat(double lastLat) {this.lastLat = lastLat;}
	public double getLastLon() {return lastLon;}
	public void setLastLon(double lastLon) {this.lastLon = lastLon;}
	public double getHomeLat() {return homeLat;}
	public void setHomeLat(double homeLat) {this.homeLat = homeLat;}
	public double getHomeLon() {return homeLon;}
	public void setHomeLon(double homeLon) {this.homeLon = homeLon;}
	public int getMapZoom() {return mapZoom;}
	public void setMapZoom(int mapZoom) {this.mapZoom = mapZoom;}
	public double getMapLat() {return mapLat;}
	public void setMapLat(double mapLat) {this.mapLat = mapLat;}
	public double getMapLon() {return mapLon;}
	public void setMapLon(double mapLon) {this.mapLon = mapLon;}
	public SdPath getPath() {return path;}
	public void setPath(SdPath path) {this.path = path;}
	public SdTouchPoint getSource() {return source;}
	public void setSource(SdTouchPoint source) {this.source = source;}
	public SdTouchPoint getTarget() {return target;}
	public void setTarget(SdTouchPoint target) {this.target = target;}
	
	public boolean isRestored() {return this.restored;}

	public AppState restoreAppState(SdGraph graph) {
		try {
			restored = false;
			
			File stateFile = new File(getAppDir(), STATE_FILE_NAME); 
			if (!stateFile.exists()) return this;

			InputStream is = new FileInputStream(stateFile);
			DataInputStream dis = new DataInputStream(is);

			int saveInstanceVersion = dis.readInt();
			if (saveInstanceVersion != STATE_FILE_VERSION) {
				dis.close(); // FIXME WTF android throws an exception here?
				return this;
			}

			navMode = dis.readBoolean();
			panMode = dis.readBoolean();
			bikeMode = dis.readBoolean();
			quietMode = dis.readBoolean();

			lastLat = dis.readDouble();
			lastLon = dis.readDouble();
			homeLat = dis.readDouble();
			homeLon = dis.readDouble();
			mapLat = dis.readDouble();
			mapLon = dis.readDouble();
			
			mapZoom = dis.readInt();

			source = SdTouchPoint.create(graph, readString(dis));
			target = SdTouchPoint.create(graph, readString(dis));

			if (dis.readBoolean()) path = SdPath.load(dis, graph);
			
			restored = true;

			dis.close(); // FIXME WTF android throws an exception here?

		} catch (Throwable t) {
			Log.e(getClass().getName(), t.toString());
		}

		return this;
	}

	public boolean saveAppState (SdGraph graph) {
		boolean saved = false;
		try {
			File stateFile = new File(getAppDir(), STATE_FILE_NAME); 
			
			OutputStream os = new FileOutputStream(stateFile);
			DataOutputStream dos = new DataOutputStream(os);
			dos.writeInt(STATE_FILE_VERSION);
			
			dos.writeBoolean(navMode);
			dos.writeBoolean(panMode);
			dos.writeBoolean(bikeMode);
			dos.writeBoolean(quietMode);
			
			dos.writeDouble(lastLat);
			dos.writeDouble(lastLon);

			dos.writeDouble(homeLat);
			dos.writeDouble(homeLon);
			dos.writeDouble(mapLat);
			dos.writeDouble(mapLon);
			
			dos.writeInt(mapZoom);

			writeString(source != null ? source.getKey() : null, dos);
			writeString(target != null ? target.getKey() : null, dos);
			
			dos.writeBoolean(path != null);
			if (path != null) path.save(dos);
			
			saved = true;
			
			dos.close(); // FIXME WTF android throws an exception here?
			
			return true;

		} catch (Throwable t) {
			Log.e(getClass().getName(), t.toString());
		}

		return saved;
	}

}
