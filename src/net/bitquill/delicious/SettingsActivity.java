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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.format.DateUtils;

public class SettingsActivity extends PreferenceActivity 
	implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
		
	private boolean mNeverSynced = true;
	
	@Override
	public void onDestroy () {
	    // XXX do first tag sync here?
	    super.onDestroy();
	}
	
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
				
		getPreferenceManager().setSharedPreferencesName(DeliciousApp.PREFS_NAME);
		
		Date lastSyncDate = TagsCache.getLastSyncDate(this);
		mNeverSynced = (lastSyncDate == null);

		// Load XML preferences file
		addPreferencesFromResource(R.xml.delicious_settings);
		
		Preference p = findPreference("delicious_login");
		p.setOnPreferenceClickListener(this);
		
		p = findPreference("android_tag_name");
		p.setOnPreferenceChangeListener(this);
		p.setSummary(getPreferenceScreen().getSharedPreferences()
				.getString("android_tag_name", 
						getText(R.string.pref_android_tag_name_default).toString()));
		p = findPreference("suggest_enable");
		p.setOnPreferenceChangeListener(this);
		p = findPreference("suggest_sync");
		p.setOnPreferenceClickListener(this);
		if (lastSyncDate != null) {
		    p.setSummary(getText(R.string.pref_suggest_sync_summary_prefix).toString() + 
		            DateUtils.getRelativeTimeSpanString(lastSyncDate.getTime()));
		}
		p = findPreference("suggest_bg_sync");
		p.setOnPreferenceClickListener(this);
	}
	
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if ("android_tag_name".equals(preference.getKey())) {
			preference.setSummary((String)newValue);
			return true;
		} else if ("suggest_enable".equals(preference.getKey())) {
			boolean enabled = ((Boolean)newValue).booleanValue();
			if (enabled && mNeverSynced) {
			    mNeverSynced = false; // FIXME if on EDGE, sync won't happen
				SimpleSyncService.actionSyncTags(this);
			}
			return true;
		} else if ("suggest_bg_sync".equals(preference.getKey())) {
		    boolean enabled = ((Boolean)newValue).booleanValue();
		    if (enabled) {
		        SimpleSyncService.syncSchedule(this, DateUtils.MINUTE_IN_MILLIS * 60); // TODO - make interval a setting
		    } else {
		        SimpleSyncService.syncCancel(this);
		    }
		    return true;
		}
		return false;
	}
	
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if ("delicious_login".equals(preference.getKey())) {
			Intent i = new Intent(this, LoginActivity.class);
			startActivity(i);
			return true;
		} else if ("suggest_sync".equals(preference.getKey())) {
			SimpleSyncService.actionSyncTags(this);
		}
		return false;
	}
	
}
