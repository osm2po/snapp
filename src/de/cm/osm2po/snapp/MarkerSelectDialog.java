package de.cm.osm2po.snapp;

import static de.cm.osm2po.snapp.MarkerType.GPS_MARKER;
import static de.cm.osm2po.snapp.MarkerType.SOURCE_MARKER;
import static de.cm.osm2po.snapp.MarkerType.TARGET_MARKER;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;

public class MarkerSelectDialog extends DialogFragment implements OnClickListener {

	private MarkerSelectListener selectMarkerListener;
	
	public MarkerSelectDialog(MarkerSelectListener selectMarkerListener) {
		this.selectMarkerListener = selectMarkerListener;
	}
	
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
		
        return view;
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.btn_source) {
			dismiss();
			selectMarkerListener.onMarkerSelected(SOURCE_MARKER);
		} else if (v.getId() == R.id.btn_target) {
			dismiss();
			selectMarkerListener.onMarkerSelected(TARGET_MARKER);
		} else if (v.getId() == R.id.btn_gps_simu) {
			dismiss();
			selectMarkerListener.onMarkerSelected(GPS_MARKER);
		}
	}

}
