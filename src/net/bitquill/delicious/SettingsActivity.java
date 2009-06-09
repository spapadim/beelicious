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

public class SettingsActivity extends PreferenceActivity 
	implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
		
	private boolean mNeverSynced = true;
	
	@Override
	public void onDestroy () {
		sHandler.setActivity(null);
		super.onDestroy();
	}
	
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		sHandler.setActivity(this);
		
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
		// TODO - add last update date (when android.text.format.DateUtils is released)
	}
	
	private void fetchTags () {
		Thread tagsThread = new Thread() {
			@Override
			public void run () {
				TagsCache.syncTags(DeliciousApp.getInstance()); // XXX - check!
				sHandler.sendEmptyMessage(MSG_SYNC_COMPLETE);
			}
		};
		showDialog(ID_PROGRESS_DIALOG);
		tagsThread.start();		
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if ("android_tag_name".equals(preference.getKey())) {
			preference.setSummary((String)newValue);
			return true;
		} else if ("suggest_enable".equals(preference.getKey())) {
			boolean enabled = ((Boolean)newValue).booleanValue();
			if (enabled && mNeverSynced) {
				showDialog(ID_SYNC_CONFIRM_DIALOG);
			}
			return true;
		}
		return false;
	}
	
	private static final int ID_PROGRESS_DIALOG = 1;
	private static final int ID_SYNC_CONFIRM_DIALOG = 2;
	
	@Override
	protected Dialog onCreateDialog (int id) {
		switch(id) {
		case ID_PROGRESS_DIALOG:
			ProgressDialog progressDlg = new ProgressDialog(this);
			progressDlg.setMessage(getText(R.string.fetching_tags_message));
			return progressDlg;
		case ID_SYNC_CONFIRM_DIALOG:
			AlertDialog confirmDlg = new AlertDialog.Builder(this)
				.setTitle(R.string.tags_sync_confirm_title)
				.setMessage(R.string.tags_sync_confirm_message)
				.setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
					@Override
					public void onClick (DialogInterface dialog, int which) {
						fetchTags();
					}
				})
				.setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {
					@Override
					public void onClick (DialogInterface dialog, int which) {
						if (mNeverSynced) {
							CheckBoxPreference p = (CheckBoxPreference)findPreference("suggest_enable");
							p.setChecked(false);
						}
					}
				})
				.create();
			return confirmDlg;
		default:
			return super.onCreateDialog(id);
		}
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if ("delicious_login".equals(preference.getKey())) {
			Intent i = new Intent(this, LoginActivity.class);
			startActivity(i);
			return true;
		} else if ("suggest_sync".equals(preference.getKey())) {
			showDialog(ID_SYNC_CONFIRM_DIALOG);
		}
		return false;
	}
	
	// Assign "random" message IDs
	private static final int MSG_SYNC_COMPLETE = R.id.usernameEdit;
	
	private static final ActivityHandler<SettingsActivity> sHandler = 
		new ActivityHandler<SettingsActivity>() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_SYNC_COMPLETE:
				SettingsActivity activity = getActivity();
				if (activity == null) {
					// FIXME - This can potentially hang the application??
					break;
				}
				activity.mNeverSynced = false;
				activity.dismissDialog(ID_PROGRESS_DIALOG);
				break;
			}
		}
	};
}
