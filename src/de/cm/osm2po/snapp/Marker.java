package de.cm.osm2po.snapp;

public enum Marker {

	HOME_MARKER(R.drawable.ic_home48, true),
	TOUCH_MARKER(R.drawable.ic_touch16, false),
	GPS_MARKER(R.drawable.ic_gps32, false),
	POS_MARKER(R.drawable.ic_pos48, false),
	ALERT_MARKER(R.drawable.ic_alert48, true),
	SOURCE_MARKER(R.drawable.ic_source32, true),
	TARGET_MARKER(R.drawable.ic_target32, true);

	private int iconResourceId;
	private boolean bottomCenter;
	
	private Marker(int iconResourceId, boolean bottomCenter) {
		this.iconResourceId = iconResourceId;
		this.bottomCenter = bottomCenter;
	}
	
	public int getIconId() {
		return this.iconResourceId;
	}
	
	public boolean isBottomCenter() {
		return this.bottomCenter;
	}
}
