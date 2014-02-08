package de.cm.osm2po.snapp;

import java.io.DataInput;
import java.io.DataOutput;

import org.mapsforge.core.GeoPoint;

import de.cm.osm2po.sd.routing.SdGraph;
import de.cm.osm2po.sd.routing.SdPath;
import de.cm.osm2po.sd.routing.SdTouchPoint;

public class InstanceState {
	private final static int FLAG_NULL = 0x0;
	private final static int FLAG_HAS_CENTER = 0x1;
	private final static int FLAG_HAS_HOME = 0x2;
	private final static int FLAG_HAS_SOURCE = 0x4;
	private final static int FLAG_HAS_TARGET = 0x8;
	private final static int FLAG_HAS_PATH = 0x10;
	
	private int zoomLevel;
	private boolean carMode;
	private boolean simuMode;
	private boolean quietMode;
	private GeoPoint gpMapCenter;
	private GeoPoint gpHome;
	private SdTouchPoint tpSource;
	private SdTouchPoint tpTarget;
	private SdPath path;
	
	public InstanceState(int zoomLevel, boolean carMode, boolean simuMode,
			boolean quietMode, GeoPoint gpMapCenter, GeoPoint gpHome,
			SdTouchPoint tpSource, SdTouchPoint tpTarget, SdPath path) {
		this.zoomLevel = zoomLevel;
		this.carMode = carMode;
		this.simuMode = simuMode;
		this.quietMode = quietMode;
		this.gpMapCenter = gpMapCenter;
		this.gpHome = gpHome;
		this.tpSource = tpSource;
		this.tpTarget = tpTarget;
		this.path = path;
	}

	public boolean save(DataOutput dout) {
		int flags = FLAG_NULL;
		if (gpMapCenter != null) flags |= FLAG_HAS_CENTER;
		if (gpHome != null) flags |= FLAG_HAS_HOME;
		if (tpSource != null) flags |= FLAG_HAS_SOURCE;
		if (tpTarget != null) flags |= FLAG_HAS_TARGET;
		if (path != null) flags |= FLAG_HAS_PATH;

		try {
			dout.writeInt(flags);
			
			dout.writeBoolean(this.carMode);
			dout.writeBoolean(this.quietMode);
			dout.writeBoolean(this.simuMode);
			dout.writeInt(this.zoomLevel);
			
			if ((flags & FLAG_HAS_CENTER) != 0) {
				dout.writeDouble(this.gpMapCenter.getLatitude());
				dout.writeDouble(this.gpMapCenter.getLongitude());
			}
			if ((flags & FLAG_HAS_HOME) != 0) {
				dout.writeDouble(this.gpHome.getLatitude());
				dout.writeDouble(this.gpHome.getLongitude());
			}
			if ((flags & FLAG_HAS_SOURCE) != 0) {
				this.tpSource.save(dout);
			}
			if ((flags & FLAG_HAS_TARGET) != 0) {
				this.tpTarget.save(dout);
			}
			if ((flags & FLAG_HAS_PATH) != 0) {
				this.path.save(dout);
			}
			
			return true;

		} catch (Exception e) {
			return false;
		}
	}
	
	public boolean load(DataInput din, SdGraph sdGraph) {
		try {
			int flags = din.readInt();

			this.carMode = din.readBoolean();
			this.quietMode = din.readBoolean();
			this.simuMode = din.readBoolean();
			this.zoomLevel = din.readInt();

			if ((flags & FLAG_HAS_CENTER) != 0) {
				double lat = din.readDouble();
				double lon = din.readDouble();
				this.gpMapCenter = new GeoPoint(lat, lon);
			}
			if ((flags & FLAG_HAS_HOME) != 0) {
				double lat = din.readDouble();
				double lon = din.readDouble();
				this.gpHome = new GeoPoint(lat, lon);
			}
			
			
			if ((flags & FLAG_HAS_SOURCE) != 0) {
				this.tpSource = SdTouchPoint.load(din, sdGraph);
			}
			if ((flags & FLAG_HAS_TARGET) != 0) {
				this.tpTarget = SdTouchPoint.load(din, sdGraph);
			}
			if ((flags & FLAG_HAS_PATH) != 0) {
				this.path = SdPath.load(din, sdGraph);
			}
			
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public int getZoomLevel() {
		return zoomLevel;
	}

	public boolean isCarMode() {
		return carMode;
	}

	public boolean isSimuMode() {
		return simuMode;
	}

	public boolean isQuietMode() {
		return quietMode;
	}

	public GeoPoint getGpMapCenter() {
		return gpMapCenter;
	}

	public GeoPoint getGpHome() {
		return gpHome;
	}

	public SdTouchPoint getTpSource() {
		return tpSource;
	}

	public SdTouchPoint getTpTarget() {
		return tpTarget;
	}

	public SdPath getPath() {
		return path;
	}
	
	
	
}
