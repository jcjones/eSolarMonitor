/* Copyright 2010 J.C. Jones, All Rights Reserved */
package net.pugsplace.enlightenmonitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

public class EnlightenSolarMonitor {
	private static DecimalFormat formatter = new DecimalFormat("0.0");
	private static DateFormat dateFormat = SimpleDateFormat.getTimeInstance();
	
	private static final String PREFS_NAME = "net.pugsplace.enlightenmonitor.EnlightenSolarMonitor";
	private static final String PREF_PREFIX_KEY = "config_";
	
	static final String PREF_INSTALL_ID = "installId";
	static final String PREF_REFRESH_RATE = "refreshRateMs";
	static final String PREF_LAST_REFRESH = "lastRefresh";
	
	static final int MIN_REFRESH_TIME_MS = 5*60*1000;
	
	/**
	 * Used for logging messages
	 */
	private static final String TAG = "eSolarMonitor";

	/**
	 * User-agent string to use when making requests. Should be filled using
	 * {@link #prepareUserAgent(Context)} before making any other calls.
	 */
	private static String sUserAgent = null;

	/**
	 * {@link StatusLine} HTTP status code when no server error has occurred.
	 */
	private static final int HTTP_STATUS_OK = 200;
	
	public static final String REFRESH_RATE_DEFAULT = "30 minutes";

	/**
	 * Shared buffer used by {@link #getUrlContent(String)} when reading results
	 * from an API request.
	 */
	private static byte[] sBuffer = new byte[512];

	/**
	 * Prepare the internal User-Agent string for use. This requires a
	 * {@link Context} to pull the package name and version number for this
	 * application.
	 */
	public static void prepareUserAgent(Context context) {
		try {
			// Read package name and version number from manifest
			PackageManager manager = context.getPackageManager();
			PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
			sUserAgent = String.format(context.getString(R.string.template_user_agent), info.packageName,
					info.versionName);

		} catch (NameNotFoundException e) {
			Log.e(TAG, "Couldn't find package information in PackageManager", e);
		}
	}

	public static SolarPerformance getPerformanceData(Context context, String systemId) throws ApiException,
			ParseException {
		
		SolarPerformance perform = new SolarPerformance();

		String urlPattern = context.getString(R.string.template_performance_url);
		String content = getUrlContent(String.format(urlPattern, systemId));

		try {
			// Drill into the JSON response to find the content body
			JSONObject response = new JSONObject(content);
			JSONArray datasets = response.getJSONArray("datasets");

			JSONObject powerCurrent = datasets.getJSONObject(0);
			JSONObject energyToday = datasets.getJSONObject(1);
			JSONObject energyWeek = datasets.getJSONObject(2);
			JSONObject energyMonth = datasets.getJSONObject(3);
			JSONObject energyLifetime = datasets.getJSONObject(4);

			perform.setCurrentWatts(getWatts(powerCurrent));
			perform.setTodayWattHours(getWatts(energyToday));
			perform.setWeekWattHours(getWatts(energyWeek));
			perform.setMonthWattHours(getWatts(energyMonth));
			perform.setLifetimeWattHours(getWatts(energyLifetime));
			perform.setTimestamp(System.currentTimeMillis());
			
			
			savePreference(context, PREF_LAST_REFRESH, System.currentTimeMillis()+"");
			Log.d(TAG, perform.toString());
		} catch (JSONException e) {
			throw new ParseException("Problem parsing API response", e);
		}

		return perform;
	}

	private static double getWatts(JSONObject energyObject) throws JSONException {
		JSONObject primaryStat = energyObject.getJSONObject("primary_stat");

		String units = primaryStat.getString("units");
		if (units.startsWith("kW")) {
			return (double) (primaryStat.getDouble("value") * 1000);
		} else if (units.startsWith("W")) {
			return (double) (primaryStat.getDouble("value"));
		} else if (units.startsWith("MW")) {
			return (double) (primaryStat.getDouble("value") * 1000000);
		} else {
			return -1;
		}
	}

	/**
	 * Pull the raw text content of the given URL. This call blocks until the
	 * operation has completed, and is synchronized because it uses a shared
	 * buffer {@link #sBuffer}.
	 * 
	 * @param url
	 *            The exact URL to request.
	 * @return The raw content returned by the server.
	 * @throws ApiException
	 *             If any connection or server error occurs.
	 */
	protected static synchronized String getUrlContent(String url) throws ApiException {
		if (sUserAgent == null) {
			throw new ApiException("User-Agent string must be prepared");
		}

		// Create client and set our specific user-agent string
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet(url);
		request.setHeader("User-Agent", sUserAgent);

		try {
			HttpResponse response = client.execute(request);

			// Check if server response is valid
			StatusLine status = response.getStatusLine();
			if (status.getStatusCode() != HTTP_STATUS_OK) {
				throw new ApiException("Invalid response from server: " + status.toString());
			}

			// Pull content stream from response
			HttpEntity entity = response.getEntity();
			InputStream inputStream = entity.getContent();

			ByteArrayOutputStream content = new ByteArrayOutputStream();

			// Read response into a buffered stream
			int readBytes = 0;
			while ((readBytes = inputStream.read(sBuffer)) != -1) {
				content.write(sBuffer, 0, readBytes);
			}

			// Return result from buffered stream
			return new String(content.toByteArray());
		} catch (IOException e) {
			throw new ApiException("Problem communicating with API", e);
		}
	}
	
	private static String formatPerformance(double value, boolean energy, unitSize minUnit) {
		StringBuffer sb = new StringBuffer();

		if (value >= 1000000 || unitSize.megawatt.equals(minUnit)) {
			sb.append(formatter.format((double)value/1000000));
			sb.append(" MW");
		} else if (value > 9000|| unitSize.kilowatt.equals(minUnit)) {
			sb.append(formatter.format((double)value/1000));
			sb.append(" kW");
		} else {
			sb.append(formatter.format(value));
			sb.append(" W");
		}
		
		if (energy) {
			sb.append('h');
		}
		
		return sb.toString();
	}
	
	public enum unitSize {
		watt,kilowatt,megawatt;
	}

	public static class SolarPerformance {
		private double currentWatts;
		private double todayWattHours;
		private double weekWattHours;
		private double monthWattHours;
		private double lifetimeWattHours;
		private String timestamp;

		public String getCurrentWatts() {
			return formatPerformance(currentWatts, false, unitSize.watt);
		}

		public String getTodayWattHours() {
			return formatPerformance(todayWattHours, true, unitSize.kilowatt);
		}

		public String getWeekWattHours() {
			return formatPerformance(weekWattHours, true, unitSize.kilowatt);
		}

		public String getMonthWattHours() {
			return formatPerformance(monthWattHours, true, unitSize.kilowatt);
		}

		public String getLifetimeWattHours() {
			return formatPerformance(lifetimeWattHours, true, unitSize.kilowatt);
		}

		public void setCurrentWatts(double currentWatts) {
			this.currentWatts = currentWatts;
		}

		public void setTodayWattHours(double todayWattHours) {
			this.todayWattHours = todayWattHours;
		}

		public void setWeekWattHours(double weekWattHours) {
			this.weekWattHours = weekWattHours;
		}

		public void setMonthWattHours(double monthWattHours) {
			this.monthWattHours = monthWattHours;
		}

		public void setLifetimeWattHours(double lifetimeWattHours) {
			this.lifetimeWattHours = lifetimeWattHours;
		}
		
		public void setTimestamp(long l) {
			this.timestamp = dateFormat.format(new Date(l));
		}

		public String toString() {
			return "C: " + currentWatts + ", T: " + todayWattHours + ", W: " + weekWattHours + ", M: " + monthWattHours
					+ ", L: " + lifetimeWattHours;
		}

		public String getTimestamp() {
			return timestamp;
		}
	}

	protected static class ApiException extends Exception {
		public ApiException(String text, Throwable cause) {
			super(text, cause);
		}

		public ApiException(String text) {
			super(text);
		}
	}

	protected static class ParseException extends Exception {
		public ParseException(String text, Throwable cause) {
			super(text, cause);
		}

		public ParseException(String text) {
			super(text);
		}
	}
	
	// Write the prefix to the SharedPreferences object for this widget
	static void savePreference(Context context, String key, String text) {
		SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
		prefs.putString(PREF_PREFIX_KEY + key, text);
		prefs.commit();
	}
	
	static String getPreference(Context context, String key, String defaultString) {
		SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, 0);
		return preferences.getString(PREF_PREFIX_KEY+key, defaultString);
	}

	public static boolean isTimeForUpdate(Context context, long currentTimeMillis) {
		String rateString = getPreference(context, PREF_REFRESH_RATE, REFRESH_RATE_DEFAULT);
		long rateMs = parseRefreshString(rateString);
		
		long lastRefresh = Long.parseLong(getPreference(context, PREF_LAST_REFRESH, "0"));
		
		if (lastRefresh + rateMs < currentTimeMillis) {
			Log.d(TAG, "isTimeForUpdate true, " + lastRefresh + "+"+rateMs + "<" + currentTimeMillis);
			return true;
		}
		
		Log.d(TAG, "isTimeForUpdate FALSE, " + lastRefresh + "+"+rateMs + "<" + currentTimeMillis);
		return false;
	}

	public static long parseRefreshString(String refreshString) {
		long millsPerSecond = 1000-50; // Fudge factor
		long secondsPerMinute = 60;
		long minutesPerHour = 60;
		
		if (refreshString != null) {
			String parts[] = refreshString.split(" ");
			if (parts.length == 2) {
				int amt = Integer.parseInt(parts[0]);
				
				if ("minutes".equals(parts[1])) {
					return secondsPerMinute * millsPerSecond * amt;
				}
			
				if ("hours".equals(parts[1])) {
					return secondsPerMinute * millsPerSecond * minutesPerHour * amt;
				}
			}
		}
		
		Log.d(TAG, "Falling back to 30 minutes");
		return secondsPerMinute * millsPerSecond * 30;
	}

}