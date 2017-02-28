package com.ecemoca.zhoub.mapscanner.settings;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.ecemoca.zhoub.mapscanner.MapScanner;
import com.ecemoca.zhoub.mapscanner.R;

/**
 * Created by zhoub on 11/16/2016.
 */
// Preferences
public class settingsActivity extends PreferenceActivity {
    protected void onCreate(Bundle icicle){
        super.onCreate(icicle);
//        getSharedPreferences("mapscannersettings",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
//        getFragmentManager().beginTransaction().replace(android.R.id.content,new MapScanner.settingsFragment()).commit();

        getPreferenceManager().setSharedPreferencesName(MapScanner.SHARED_PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences);

    }
}


