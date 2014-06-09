package de.cm.osm2po.snapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle bundle = intent.getExtras();
		try {
			if (bundle != null) {

				final Object[] pdusObj = (Object[]) bundle.get("pdus");

				if (pdusObj.length > 0) { // Take first, no multi SMS
					SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) pdusObj[0]);
					String phoneNumber = currentMessage.getDisplayOriginatingAddress();

					String senderNum = phoneNumber;
					String message = currentMessage.getDisplayMessageBody();

					final Intent activity = new Intent(context, MainActivity.class);
					activity.putExtra("snapp:geo", message + ";" + senderNum);
					// a must in this context
					activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					// prevent multi instances of already running activity
					activity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					context.startActivity(activity);
				}
				
			}

		} catch (Exception e) {
			Log.e("SmsReceiver", "Exception smsReceiver" +e);

		}		
	}

}
