/**
 * Copyright 2009 Spiros Papadimitriou <spapadim@cs.cmu.edu>
 * 
 * This file is part of Bitlicious.
 * 
 * Bitlicious is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Bitlicious is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Bitlicious.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bitquill.delicious;

import java.util.Date;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.format.DateUtils;

public class SettingsActivity extends PreferenceActivity 
	implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
		
    // Preference keys from delicious_settings.xml
    public static final String PREF_LOGIN = "delicious_login";
    public static final String PREF_USERNAME = "delicious_username";
    public static final String PREF_PASSWORD = "delicious_password";
    public static final String PREF_ENDPOINT = "delicious_endpoint";
    public static final String PREF_TAG_BG_SYNC = "tag_bg_sync";
    public static final String PREF_SYNC_ON_2G = "sync_on_2g";
    public static final String PREF_TAG_SYNC = "tag_sync";
    public static final String PREF_CLOUD_LOGARITHMIC = "cloud_logarithmic";
    public static final String PREF_ANDROID_TAG_ENABLE = "android_tag_enable";
    public static final String PREF_ANDROID_TAG_NAME = "android_tag_name";
    public static final String PREF_DELICIOUS_PRIVATE = "delicious_private";
    
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
				
		getPreferenceManager().setSharedPreferencesName(DeliciousApp.PREFS_NAME);
		
		// Load XML preferences file
		addPreferencesFromResource(R.xml.delicious_settings);
		
		Preference p = findPreference(PREF_LOGIN);
		p.setOnPreferenceClickListener(this);
		
		p = findPreference(PREF_ANDROID_TAG_NAME);
		p.setOnPreferenceChangeListener(this);
		p.setSummary(getPreferenceScreen().getSharedPreferences()
				.getString(PREF_ANDROID_TAG_NAME, 
						getText(R.string.pref_android_tag_name_default).toString()));
		p = findPreference(PREF_TAG_SYNC);
		p.setOnPreferenceClickListener(this);
        Date lastSyncDate = TagsCache.getLastSyncDate(this);
		if (lastSyncDate != null) {
		    p.setSummary(getText(R.string.pref_tag_sync_summary_prefix).toString() + " " +
		            DateUtils.getRelativeTimeSpanString(lastSyncDate.getTime()));
		}
		p = findPreference(PREF_TAG_BG_SYNC);
		p.setOnPreferenceClickListener(this);
	}
	
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (PREF_ANDROID_TAG_NAME.equals(preference.getKey())) {
			preference.setSummary((String)newValue);
			return true;
		} else if (PREF_TAG_BG_SYNC.equals(preference.getKey())) {
		    int intervalInHours = Integer.parseInt((String)newValue);
		    if (intervalInHours > 0) {
		        BookmarkService.syncSchedule(this, DateUtils.HOUR_IN_MILLIS * intervalInHours);
		    } else {
		        BookmarkService.syncCancel(this);
		    }
		    return true;
		}
		return false;
	}
	
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (PREF_LOGIN.equals(preference.getKey())) {
			Intent i = new Intent(this, LoginActivity.class);
			startActivity(i);
			return true;
		} else if (PREF_TAG_SYNC.equals(preference.getKey())) {
			BookmarkService.actionSyncTags(this, true);
		}
		return false;
	}
	
}
