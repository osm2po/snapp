package de.cm.osm2po.snapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * A rudimentary {@link BitmapDrawable} that can be rotated
 * around its center.<br/>
 * <b>Pre:</b> The Bitmap should be a square
 * @author (c) Carsten Moeller, Pinneberg - info@osm2po.de
 */
public class RotatableBitmapDrawable extends Drawable {
	
	private Bitmap bmpOrg;
	private Bitmap bmp;
	private Canvas canvas;
	private int w, h;
	private float degs;

	public RotatableBitmapDrawable(Bitmap bmp) {
		this.bmpOrg = bmp;
		this.bmp = bmp.copy(bmp.getConfig(), true);
		this.h = bmp.getHeight();
		this.w = bmp.getWidth();
		this.canvas = new Canvas(this.bmp);
	}

	@Override
	public void draw(Canvas canvas) {
		canvas.drawBitmap(this.bmp, null, this.getBounds(), null);
	}
	
	/**
	 * Set the current rotation angle before next draw.
	 * @param degs float - clockwise angle 0-360.
	 */
	public void rotate(float degs) {
		if (degs != this.degs) {
			this.canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
			Matrix mtx = new Matrix();
			mtx.setRotate(degs, this.w / 2, this.h / 2);
			this.canvas.drawBitmap(this.bmpOrg, mtx, null);
			this.degs = degs;
		}
	}
	
	@Override public int getIntrinsicWidth() {return this.w;}
	@Override public int getIntrinsicHeight() {return this.h;}
	@Override public int getMinimumWidth() {return this.w;}
	@Override public int getMinimumHeight() {return this.h;}

	// Not used
	@Override public int getOpacity() {return 0;}
	@Override public void setAlpha(int alpha) {}
	@Override public void setColorFilter(ColorFilter cf) {}

}
