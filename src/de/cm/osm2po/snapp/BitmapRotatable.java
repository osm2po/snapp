package de.cm.osm2po.snapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;

public class BitmapRotatable extends BitmapDrawable {

	private Bitmap bmpTmp;
	private Bitmap bmpOrg;
	private float px, py;
	private float rotate;
	
	public BitmapRotatable(Bitmap bmp) {
		// WTF CTOR
		super(bmp = bmp.copy(bmp.getConfig(), true));
		this.bmpOrg = bmp.copy(bmp.getConfig(), false);
		this.bmpTmp = bmp;
		this.px = bmp.getWidth() / 2;
		this.py = bmp.getHeight() / 2;
	}
    
	public void setRotate(float rotate) {
		this.rotate = rotate;
	}
	
    @Override
    public void draw(Canvas canvas) {
    	Canvas c = new Canvas(this.bmpTmp);
    	c.drawColor(Color.TRANSPARENT, Mode.CLEAR);
    	Matrix mtx = new Matrix();
    	mtx.setRotate(this.rotate, this.px, this.py);
    	c.drawBitmap(this.bmpOrg, mtx, null);
    	super.draw(canvas);
    }

}
