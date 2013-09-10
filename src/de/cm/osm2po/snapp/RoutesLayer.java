package de.cm.osm2po.snapp;

import org.mapsforge.android.maps.overlay.ArrayWayOverlay;
import org.mapsforge.android.maps.overlay.OverlayWay;
import org.mapsforge.core.GeoPoint;

import android.graphics.Color;
import android.graphics.Paint;

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
//        fill.setAntiAlias(true);
        fill.setStrokeJoin(Paint.Join.ROUND);
//        fill.setPathEffect(new DashPathEffect(new float[] { 20, 20 }, 0));
 
		return fill;
	}
	
	public static Paint defaultOutlinePaint() {
		Paint outline = new Paint();

        outline.setStyle(Paint.Style.STROKE);
        outline.setColor(Color.BLACK);
        outline.setAlpha(31);
//        outline.setAntiAlias(true);
        outline.setStrokeWidth(11);
        outline.setStrokeJoin(Paint.Join.ROUND);

		return outline;
	}
	
	public void showRoute(GeoPoint[] geoPoints) {
		GeoPoint[][] geoWays = new GeoPoint[][]{geoPoints};
		if (overlayWay != null) removeWay(overlayWay);
		overlayWay = new OverlayWay(geoWays, defaultFillPaint(), defaultOutlinePaint());
		addWay(overlayWay);
		requestRedraw();
	}

}
