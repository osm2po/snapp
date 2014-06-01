package de.cm.osm2po.snapp;

import org.mapsforge.android.maps.overlay.ArrayWayOverlay;
import org.mapsforge.android.maps.overlay.OverlayWay;
import org.mapsforge.core.GeoPoint;

import android.graphics.Color;
import android.graphics.Paint;
import de.cm.osm2po.sd.routing.SdGeoUtils;

public class RoutesLayer extends ArrayWayOverlay {

	OverlayWay overlayWay;
	
	public RoutesLayer() {
		super(defaultFillPaint(), defaultOutlinePaint());
	}
	
	public static Paint defaultFillPaint() {
		Paint fill = new Paint();

        fill.setStyle(Paint.Style.STROKE);
        fill.setColor(Color.BLUE);
        fill.setAlpha(127);
        fill.setStrokeWidth(7);
        fill.setAntiAlias(true);
        fill.setStrokeJoin(Paint.Join.ROUND);
 
		return fill;
	}
	
	public static Paint defaultOutlinePaint() {
		Paint outline = new Paint();

        outline.setStyle(Paint.Style.STROKE);
        outline.setColor(Color.BLACK);
        outline.setAlpha(31);
        outline.setAntiAlias(true);
        outline.setStrokeWidth(11);
        outline.setStrokeJoin(Paint.Join.ROUND);

		return outline;
	}
	
	public void drawRoute(long[] geometry) {
		if (overlayWay != null) removeWay(overlayWay);
		if (null == geometry) return;
		
    	int n = geometry.length;
        GeoPoint[] geoPoints = new GeoPoint[n];
        for (int i = 0; i < n; i++) {
        	double lat = SdGeoUtils.toLat(geometry[i]);
        	double lon = SdGeoUtils.toLon(geometry[i]);
        	geoPoints[i] = new GeoPoint(lat, lon);
        }
		
		GeoPoint[][] geoWays = new GeoPoint[][]{geoPoints};
		overlayWay = new OverlayWay(geoWays, defaultFillPaint(), defaultOutlinePaint());
		addWay(overlayWay);
		requestRedraw();
	}

}
