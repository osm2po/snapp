package de.cm.osm2po.snapp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

					final Intent startIntent = new Intent(context, MainActivity.class);
					startIntent.putExtra("snapp:geo", message + ";" + senderNum);
					// a must in this context
					startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					// prevent multi instances of already running activity
					startIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					
					
					final NotificationManager notificationManager =	(NotificationManager)
							context.getSystemService(Context.NOTIFICATION_SERVICE);
					final Notification notification = new Notification(
							R.drawable.ic_helpme48, "Accident", System.currentTimeMillis());
					
					final PendingIntent pendingIntent = PendingIntent.getActivity(context, senderNum.hashCode(), startIntent, 0);
					notification.setLatestEventInfo(context, "Accident", message, pendingIntent);
					
					notificationManager.notify(senderNum.hashCode(), notification);
					
//					context.startActivity(startIntent);
				}
				
			}

		} catch (Exception e) {
			Log.e("SmsReceiver", "Exception smsReceiver" +e);

		}		
	}

}
