package de.cm.osm2po.snapp;

import static de.cm.osm2po.snapp.MarkerType.GPS_MARKER;
import static de.cm.osm2po.snapp.MarkerType.HOME_MARKER;
import static de.cm.osm2po.snapp.MarkerType.SOURCE_MARKER;
import static de.cm.osm2po.snapp.MarkerType.TARGET_MARKER;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;

public class MarkerSelectDialog extends DialogFragment implements OnClickListener {

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
	
	@Override
	public void onClick(View v) {
		dismiss();
		Activity activity = this.getActivity();
		if (activity instanceof MarkerSelectListener) {
			MarkerSelectListener msl = (MarkerSelectListener) activity;
			switch (v.getId()) {
			case R.id.btn_source: msl.onMarkerSelected(SOURCE_MARKER); break;
			case R.id.btn_target: msl.onMarkerSelected(TARGET_MARKER); break;
			case R.id.btn_gps_simu: msl.onMarkerSelected(GPS_MARKER); break;
			case R.id.btn_home: msl.onMarkerSelected(HOME_MARKER); break;
			}
		}
	}

}
