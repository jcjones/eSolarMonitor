/* Copyright 2010 J.C. Jones, All Rights Reserved */
package net.pugsplace.enlightenmonitor;

import net.pugsplace.enlightenmonitor.EnlightenSolarMonitor.ApiException;
import net.pugsplace.enlightenmonitor.EnlightenSolarMonitor.ParseException;
import net.pugsplace.enlightenmonitor.EnlightenSolarMonitor.SolarPerformance;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;

public class MonitorWidget extends AppWidgetProvider {
	private static int currentState = 0;
	private static final int STATE_WEEK = 0;
	private static final int STATE_MONTH = 1;
	private static final int STATE_LIFETIME = 2;

	private static SolarPerformance performanceData;
	private static final Object semaphore = new Object();

	/** Intent name for the state change */
	private static final String ACTION_WIDGET_STATECHANGE = "net.pugsplace.enlightenmonitor.StateChange";
	/** Intent name for opening the config */
	private static final String ACTION_WIDGET_CONFIG = "net.pugsplace.enlightenmonitor.DoConfigure";
	/**
	 * Used for logging messages
	 */
	private static final String TAG = "eSolarWidget";

	
	/** Used by the service */
	private static int[] appWidgetIds;

	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		MonitorWidget.appWidgetIds = appWidgetIds;
		
		if (EnlightenSolarMonitor.isTimeForUpdate(context, System.currentTimeMillis())) {
			// To prevent any ANR timeouts, we perform the update in a service
			context.startService(new Intent(context, UpdateService.class));
		}
	}

	public static void drawScreen(Context context) {
		if (MonitorWidget.appWidgetIds == null) {
			return;
		}
		
		for (int appWidgetId : MonitorWidget.appWidgetIds) {
			Log.i(TAG, "Updating widget " + appWidgetId);
			
			// Build the widget update for today
			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.enlighten_appwidget);
			
			// Set button actions
			Intent stateChangeIntent = new Intent(context, MonitorWidget.class).setAction(ACTION_WIDGET_STATECHANGE);
			PendingIntent stateChangePendingIntent = PendingIntent.getBroadcast(context, 0, stateChangeIntent, 0);
			views.setOnClickPendingIntent(R.id.PanelIcon, stateChangePendingIntent);
			
			Intent configIntent = new Intent(context, MonitorConfiguration.class);
			configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
			configIntent.setData(Uri.parse("eSolarMonitor://appWidgetId/"+appWidgetId));

			PendingIntent configPendingIntent = PendingIntent.getActivity(context, 0, configIntent, 0);
			views.setOnClickPendingIntent(R.id.ConfigIcon, configPendingIntent);
			
			// Draw
			if (performanceData != null) {
				synchronized (semaphore) {
					// Build an update that holds the updated widget contents
					views.setTextViewText(R.id.Watts, performanceData.getCurrentWatts());
					views.setTextViewText(R.id.TodayWH, performanceData.getTodayWattHours());
					views.setTextViewText(R.id.LastUpdate, performanceData.getTimestamp());
	
					switch (currentState) {
					case STATE_WEEK:
						views.setTextViewText(R.id.StatValue, performanceData.getWeekWattHours());
						views.setTextViewText(R.id.StatLabel, context.getString(R.string.widget_week));
						break;
					case STATE_MONTH:
						views.setTextViewText(R.id.StatValue, performanceData.getMonthWattHours());
						views.setTextViewText(R.id.StatLabel, context.getString(R.string.widget_month));
						break;
					case STATE_LIFETIME:
						views.setTextViewText(R.id.StatValue, performanceData.getLifetimeWattHours());
						views.setTextViewText(R.id.StatLabel, context.getString(R.string.widget_lifetime));
						break;
					}
				}
			} else {
				Log.w(TAG, "Performance Data was null");
				views.setTextViewText(R.id.Watts, context.getString(R.string.widget_error));
				views.setTextViewText(R.id.TodayWH, "");
				views.setTextViewText(R.id.StatValue, "");
				views.setTextViewText(R.id.StatLabel, "");
			}
	
			// Push update for this widget to the home screen
			AppWidgetManager manager = AppWidgetManager.getInstance(context);
			manager.updateAppWidget(appWidgetId, views);
		}
	}

	public static class UpdateService extends Service {

		public IBinder onBind(Intent arg0) {
			return null;
		}

		public void onStart(Intent intent, int startId) {
			try {
				EnlightenSolarMonitor.prepareUserAgent(this);
				synchronized (semaphore) {
					String installId = EnlightenSolarMonitor.getPreference(this, EnlightenSolarMonitor.PREF_INSTALL_ID, "");
					Log.d(TAG, "Install ID: [" + installId + "]");
					
					if (installId != null && installId.length()>0) {
						
						// Throttle to 5 minutes
						long lastRefresh = Long.parseLong(EnlightenSolarMonitor.getPreference(this, EnlightenSolarMonitor.PREF_LAST_REFRESH, "0"));
						if (performanceData != null && System.currentTimeMillis() < lastRefresh+EnlightenSolarMonitor.MIN_REFRESH_TIME_MS) {
							Log.i(TAG, "Throttled performance update");

						} else {
							SolarPerformance result = EnlightenSolarMonitor.getPerformanceData(this, installId);
							
							if (result != null) {								
								Log.d(TAG, "Got Performance Data");
								performanceData = result;
							}
						}
					}
				}				
			} catch (ParseException parseException) {
				Log.e(TAG, "Couldn't parse JSON response", parseException);
			} catch (ApiException apiException) {
				Log.e(TAG, "Couldn't connect to Enlighten service", apiException);
			}
			
			drawScreen(this);
		}
	}

	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive Intent: " + intent);
		if (ACTION_WIDGET_STATECHANGE.equals(intent.getAction())) {
			currentState = (++currentState) % 3;
			drawScreen(context);
		} else {
			super.onReceive(context, intent);
		}
	}
}
