package net.pugsplace.enlightenmonitor;

import net.pugsplace.enlightenmonitor.MonitorWidget.UpdateService;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RemoteViews;
import android.widget.Spinner;

public class MonitorConfiguration extends Activity {

	private int mAppWidgetId;
	private EditText installationId;
	private Spinner refreshRate;

	/**
	 * Used for logging messages
	 */
	private static final String TAG = "eSolarConfiguration";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "Created");

		// Set the result to CANCELED. This will cause the widget host to cancel
		// out of the widget placement if they press the back button.
		setResult(RESULT_CANCELED);

		// Show the view
		setContentView(R.layout.enlighten_config);

		// Find the EditText
		installationId = (EditText) findViewById(R.id.config_installationId);
		refreshRate = (Spinner) findViewById(R.id.config_refresh_spinner);
		
		// Insert the Spinner text entries
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.refresh_times_array,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		refreshRate.setAdapter(adapter);

		// Bind the action for the save button.
		findViewById(R.id.config_save).setOnClickListener(mSaveListener);
		findViewById(R.id.config_about).setOnClickListener(mAboutListener);

		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		Log.i(TAG, "Extras: " + extras + intent + intent.getDataString());
		if (extras != null) {
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
			Log.i(TAG, "ID: " + mAppWidgetId);
		}

		// If they gave us an intent without the widget id, just bail.
		if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			Log.i(TAG, "Not opening config");
			finish();
		}

		String instDefault = EnlightenSolarMonitor.getPreference(MonitorConfiguration.this,
				EnlightenSolarMonitor.PREF_INSTALL_ID, "");
		String refreshDefault = EnlightenSolarMonitor.getPreference(MonitorConfiguration.this,
				EnlightenSolarMonitor.PREF_REFRESH_RATE, EnlightenSolarMonitor.REFRESH_RATE_DEFAULT);

		installationId.setText(instDefault);

		for (int pos = 0; pos < adapter.getCount(); pos++) {
			if (adapter.getItem(pos).equals(refreshDefault)) {
				refreshRate.setSelection(pos);
				break;
			}
		}
	}

	View.OnClickListener mAboutListener = new View.OnClickListener() {
		public void onClick(View v) {
			Context context = v.getContext();
			new AlertDialog.Builder(context).setTitle(context.getString(R.string.about_title))
					.setMessage(context.getString(R.string.about_message)).show();
		}
	};

	View.OnClickListener mSaveListener = new View.OnClickListener() {
		public void onClick(View v) {
			final Context context = MonitorConfiguration.this;

			// When the button is clicked, save the string in our prefs and
			// return that they
			// clicked OK.
			String installId = installationId.getText().toString().trim();
			String refreshString = (String) refreshRate.getSelectedItem();

			String oldInstallId = EnlightenSolarMonitor.getPreference(context,
					EnlightenSolarMonitor.PREF_INSTALL_ID, "");

			if (false == oldInstallId.equals(installId)) {
				EnlightenSolarMonitor.savePreference(context, EnlightenSolarMonitor.PREF_LAST_REFRESH, "0");
			}

			// save
			EnlightenSolarMonitor.savePreference(MonitorConfiguration.this, EnlightenSolarMonitor.PREF_INSTALL_ID,
					installId);
			EnlightenSolarMonitor.savePreference(MonitorConfiguration.this, EnlightenSolarMonitor.PREF_REFRESH_RATE,
					refreshString);

			// Make the update
			context.startService(new Intent(context, UpdateService.class));

			// Make sure we pass back the original appWidgetId
			Intent resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
			setResult(RESULT_OK, resultValue);
			finish();
		}
	};

}
