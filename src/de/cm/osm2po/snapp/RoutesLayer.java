package de.cm.osm2po.snapp;

import java.util.Arrays;

import org.mapsforge.android.maps.overlay.ArrayWayOverlay;
import org.mapsforge.android.maps.overlay.OverlayWay;
import org.mapsforge.core.GeoPoint;

import android.graphics.Color;
import android.graphics.Paint;
import de.cm.osm2po.sd.routing.SdGeoUtils;
import de.cm.osm2po.sd.routing.SdGraph;
import de.cm.osm2po.sd.routing.SdPath;

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
	
	public void drawPath(SdGraph graph, SdPath path) {
		
		if (overlayWay != null) removeWay(overlayWay); // remove old routes

		if (path != null) {
			GeoPoint[] geoPoints = new GeoPoint[0x400 /*1k*/];
			int n = 0; 
			
			int nEdges = path.getNumberOfEdges();

			for (int i = 0; i < nEdges; i++) {
				long[] coords = path.fetchGeometry(graph, i);
				int nCoords = coords.length;
				
				int z = 0 == i ? 0 : 1; 
				for (int j = z; j < nCoords; j++) {
					double lat = SdGeoUtils.toLat(coords[j]);
					double lon = SdGeoUtils.toLon(coords[j]);
		        	geoPoints[n++] = new GeoPoint(lat, lon);
		        	if (geoPoints.length == n) { // ArrayOverflowCheck
		        		geoPoints = Arrays.copyOf(geoPoints, n * 2);
		        	}
				}
			}
			
			geoPoints = Arrays.copyOf(geoPoints, n);
			GeoPoint[][] geoWays = new GeoPoint[][]{geoPoints};
			overlayWay = new OverlayWay(geoWays, defaultFillPaint(), defaultOutlinePaint());
			addWay(overlayWay);
			requestRedraw();
		}
	}

}
