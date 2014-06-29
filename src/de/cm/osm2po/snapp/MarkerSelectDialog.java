package de.cm.osm2po.snapp;

import static de.cm.osm2po.snapp.Marker.GPS_MARKER;
import static de.cm.osm2po.snapp.Marker.HOME_MARKER;
import static de.cm.osm2po.snapp.Marker.SOURCE_MARKER;
import static de.cm.osm2po.snapp.Marker.TARGET_MARKER;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;

public class MarkerSelectDialog extends DialogFragment implements OnClickListener {
	
	private int action;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.dialog_select_marker, container);

		ImageButton btnSource = (ImageButton) view.findViewById(R.id.btn_source);
		btnSource.setOnClickListener(this);
		ImageButton btnTarget = (ImageButton) view.findViewById(R.id.btn_target);
		btnTarget.setOnClickListener(this);
		ImageButton btnGpsSimu = (ImageButton) view.findViewById(R.id.btn_gps_simu);
		btnGpsSimu.setOnClickListener(this);
		ImageButton btnHome = (ImageButton) view.findViewById(R.id.btn_home);
		btnHome.setOnClickListener(this);
		
        return view;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		return  dialog;
	}
	
	public void show(int action, FragmentManager fm) {
		this.action = action;
		super.show(fm, "dlg_marker");
	}
	
	@Override
	public void onClick(View v) {
		dismiss();
		Activity activity = this.getActivity();
		if (activity instanceof MarkerEditListener) {
			MarkerEditListener msl = (MarkerEditListener) activity;
			switch (v.getId()) {
			case R.id.btn_source: msl.onMarkerAction(SOURCE_MARKER, action); break;
			case R.id.btn_target: msl.onMarkerAction(TARGET_MARKER, action); break;
			case R.id.btn_gps_simu: msl.onMarkerAction(GPS_MARKER, action); break;
			case R.id.btn_home: msl.onMarkerAction(HOME_MARKER, action); break;
			}
		}
	}

}
