package de.cm.osm2po.snapp;



public enum MarkerType {
	// TODO NYI handle index as zindex
	HOME_MARKER(0, "Home", R.drawable.ic_home32, true),
	TOUCH_MARKER(1, "Touch", R.drawable.ic_marker16, false),
	SOURCE_MARKER(2, "Source", R.drawable.ic_source32, true),
	TARGET_MARKER(3, "Target", R.drawable.ic_target32, true),
	GPS_MARKER(4, "GPS", R.drawable.ic_north32, false);

	private int index;
	private String title;
	private int iconId;
	private boolean bottomCenter;
	
	private MarkerType(int index, String title, int iconId, boolean bottomCenter) {
		this.index = index;
		this.title = title;
		this.iconId = iconId;
		this.bottomCenter = bottomCenter;
	}
	
	public int getIndex() {
		return this.index;
	}
	
	public String getTitle() {
		return this.title;
	}
	
	public int getIconId() {
		return this.iconId;
	}
	
	public boolean isBottomCenter() {
		return this.bottomCenter;
	}
}
