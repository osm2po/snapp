package de.cm.osm2po.snapp;

import static de.cm.osm2po.snapp.MainApplication.getAppDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.mapsforge.core.GeoPoint;

import android.util.Log;
import de.cm.osm2po.sd.routing.SdPath;
import de.cm.osm2po.sd.routing.SdTouchPoint;

/**
 * Persistable container which reflects the instance state of the app.
 * 
 * @author carsten
 */
public class AppState {

	private final static String STATE_FILE_NAME = "snapp.state";
	private static final int STATE_FILE_VERSION = 8;

	private final static int FLAG_NULL = 0x0;
	private final static int FLAG_HAS_MAPPOS = 0x1;
	private final static int FLAG_HAS_HOMEPOS = 0x2;
	private final static int FLAG_HAS_LASTPOS = 0x4;
	private final static int FLAG_HAS_SOURCE = 0x8;
	private final static int FLAG_HAS_TARGET = 0x10;
	private final static int FLAG_HAS_PATH = 0x20;
	
	private transient boolean restored;
	
	private boolean quietMode;
	private boolean bikeMode;
	private boolean panMode;
	private boolean navMode;

	private GeoPoint lastPos;
	private GeoPoint homePos;
	private GeoPoint mapPos;
	
	private int mapZoom;

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
	public int getMapZoom() {return mapZoom;}
	public void setMapZoom(int mapZoom) {this.mapZoom = mapZoom;}
	public SdPath getPath() {return path;}
	public void setPath(SdPath path) {this.path = path;}
	public SdTouchPoint getSource() {return source;}
	public void setSource(SdTouchPoint source) {this.source = source;}
	public SdTouchPoint getTarget() {return target;}
	public void setTarget(SdTouchPoint target) {this.target = target;}
	public GeoPoint getLastPos() {return lastPos;}
	public void setLastPos(GeoPoint lastPos) {this.lastPos = lastPos;}
	public GeoPoint getHomePos() {return homePos;}
	public void setHomePos(GeoPoint homePos) {this.homePos = homePos;}
	public GeoPoint getMapPos() {return mapPos;}
	public void setMapPos(GeoPoint mapPos) {this.mapPos = mapPos;}
	
	public boolean isRestored() {return this.restored;}

	public AppState restoreAppState(int graphId) {
		try {
			restored = false;
			
			File stateFile = new File(getAppDir(), STATE_FILE_NAME); 
			if (!stateFile.exists()) return this;

			InputStream is = new FileInputStream(stateFile);
			DataInputStream dis = new DataInputStream(is);

			// Read compatibility infos
			int stateFileVersion = dis.readInt();
			int readGraphId = dis.readInt();
			
			if (stateFileVersion != STATE_FILE_VERSION || graphId != readGraphId) {
				dis.close(); // FIXME WTF why does android throw an exception here?
				return this;
			}
			
			int flags = dis.readInt();

			navMode = dis.readBoolean();
			panMode = dis.readBoolean();
			bikeMode = dis.readBoolean();
			quietMode = dis.readBoolean();

			mapZoom = dis.readInt();

			lastPos = null;
			if ((flags & FLAG_HAS_LASTPOS) != 0) {
				double lat = dis.readDouble();
				double lon = dis.readDouble();
				lastPos = new GeoPoint(lat, lon);
			}
			mapPos = null;
			if ((flags & FLAG_HAS_MAPPOS) != 0) {
				double lat = dis.readDouble();
				double lon = dis.readDouble();
				mapPos = new GeoPoint(lat, lon);
			}
			homePos = null;
			if ((flags & FLAG_HAS_HOMEPOS) != 0) {
				double lat = dis.readDouble();
				double lon = dis.readDouble();
				homePos = new GeoPoint(lat, lon);
			}

			source = null;
			if ((flags & FLAG_HAS_SOURCE) != 0) {
				source = SdTouchPoint.load(dis);
			}
			target = null;
			if ((flags & FLAG_HAS_TARGET) != 0) {
				target = SdTouchPoint.load(dis);
			}
			
			path = null;
			if ((flags & FLAG_HAS_PATH) != 0) {
				path = SdPath.load(dis);
			}
			
			restored = true;

			dis.close(); // FIXME WTF why does android throw an exception here?

		} catch (Throwable t) {
			Log.e(getClass().getName(), t.toString());
		}

		return this;
	}

	public boolean saveAppState(int graphId) {
		boolean saved = false;
		int flags = FLAG_NULL;

		if (lastPos != null) flags |= FLAG_HAS_LASTPOS;
		if (mapPos != null) flags |= FLAG_HAS_MAPPOS;
		if (homePos != null) flags |= FLAG_HAS_HOMEPOS;
		if (source != null) flags |= FLAG_HAS_SOURCE;
		if (target != null) flags |= FLAG_HAS_TARGET;
		if (path != null) flags |= FLAG_HAS_PATH;

		try {
			File stateFile = new File(getAppDir(), STATE_FILE_NAME); 
			
			OutputStream os = new FileOutputStream(stateFile);
			DataOutputStream dos = new DataOutputStream(os);
			dos.writeInt(STATE_FILE_VERSION);
			dos.writeInt(graphId);
			
			dos.writeInt(flags);
			
			dos.writeBoolean(navMode);
			dos.writeBoolean(panMode);
			dos.writeBoolean(bikeMode);
			dos.writeBoolean(quietMode);
			
			dos.writeInt(mapZoom);

			if ((flags & FLAG_HAS_LASTPOS) != 0) {
				dos.writeDouble(lastPos.getLatitude());
				dos.writeDouble(lastPos.getLongitude());
			}
			if ((flags & FLAG_HAS_MAPPOS) != 0) {
				dos.writeDouble(mapPos.getLatitude());
				dos.writeDouble(mapPos.getLongitude());
			}
			if ((flags & FLAG_HAS_HOMEPOS) != 0) {
				dos.writeDouble(homePos.getLatitude());
				dos.writeDouble(homePos.getLongitude());
			}
			if ((flags & FLAG_HAS_SOURCE) != 0) {
				source.save(dos);
			}
			if ((flags & FLAG_HAS_TARGET) != 0) {
				target.save(dos);
			}
			if ((flags & FLAG_HAS_PATH) != 0) {
				path.save(dos);
			}
			
			saved = true;
			
			dos.close(); // FIXME WTF why does android throw an exception here?
			
		} catch (Throwable t) {
			Log.e(getClass().getName(), t.toString());
		}

		return saved;
	}

}
