package de.cm.osm2po.snapp;



public enum Marker {
	// TODO NYI handle index as zindex
	HOME_MARKER(0, "Home", R.drawable.ic_home48, true),
	TOUCH_MARKER(1, "Touch", R.drawable.ic_touch16, false),
	GPS_MARKER(2, "Gps", R.drawable.ic_gps32, false),
	POS_MARKER(3, "Position", R.drawable.ic_pos48, false),
	SOURCE_MARKER(4, "Source", R.drawable.ic_source32, true),
	TARGET_MARKER(5, "Target", R.drawable.ic_target32, true);

	private int index;
	private String title;
	private int iconResourceId;
	private boolean bottomCenter;
	
	private Marker(int index, String title, int iconResourceId, boolean bottomCenter) {
		this.index = index;
		this.title = title;
		this.iconResourceId = iconResourceId;
		this.bottomCenter = bottomCenter;
	}
	
	public int getIndex() {
		return this.index;
	}
	
	public String getTitle() {
		return this.title;
	}
	
	public int getIconId() {
		return this.iconResourceId;
	}
	
	public boolean isBottomCenter() {
		return this.bottomCenter;
	}
}
