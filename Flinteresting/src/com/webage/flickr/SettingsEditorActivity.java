package com.webage.flickr;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import com.webage.flickrcast.R;

public class SettingsEditorActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.prefs);
		PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
	}

}
