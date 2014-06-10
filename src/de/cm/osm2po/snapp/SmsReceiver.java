package de.cm.osm2po.snapp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

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
					double latitude = 53.5;
					double longitude = 10.1;
					
					// parse location
					// FIXME Handle multiline message
					Pattern pattern = Pattern.compile("(.*)(geo:)([\\d\\.\\-]+),([\\d\\.\\-]+)", Pattern.MULTILINE);
					Matcher matcher = pattern.matcher(message);
					if (!matcher.matches()) return;
					try { 
						latitude = Double.parseDouble(matcher.group(3));
						longitude = Double.parseDouble(matcher.group(4));
						
					} catch (Exception e) {
						Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
						return;
					}

					// Create Intent to start activivity
					final Intent startIntent = new Intent(context, MainActivity.class);
					startIntent.putExtra("sms_msg", message);
					startIntent.putExtra("sms_num", senderNum);
					startIntent.putExtra("sms_lat", latitude);
					startIntent.putExtra("sms_lon", longitude);
					
					// a must in this context
					startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					// prevent multi instances of already running activity
					startIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					
					// Wrap Intent in a notification with PendingIntent
					final NotificationManager notificationManager =	(NotificationManager)
							context.getSystemService(Context.NOTIFICATION_SERVICE);
					final Notification notification = new Notification(
							R.drawable.ic_alert48, "Location Alert", System.currentTimeMillis());
					final PendingIntent pendingIntent = PendingIntent.getActivity(context, message.hashCode(), startIntent, 0);
					notification.setLatestEventInfo(context, "Alert", message, pendingIntent);
					notificationManager.notify(message.hashCode(), notification);
					
//					context.startActivity(startIntent);
				}
				
			}

		} catch (Exception e) {
			Log.e("SmsReceiver", "Exception smsReceiver" +e);
		}		
	}

}
