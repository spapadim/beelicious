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

import net.bitquill.delicious.api.Bookmark;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;

/**
 * Simple service for background tag cache refresh and background bookmark submission. 
 */
public class BookmarkService extends Service {
    private static final String TAG = BookmarkService.class.getSimpleName();
    
    public static final String ACTION_SYNC_TAGS = "net.bitquill.delicious.intent.action.SYNC_TAGS";
    public static final String ACTION_SUBMIT_BOOKMARK = "net.bitquill.delicious.intent.action.SUBMIT_BOOKMARK";
    
    public static final String EXTRA_FORCE_SYNC = "net.bitquill.delicious.intent.extra.FORCE_SYNC";
    public static final String EXTRA_BOOKMARK = "net.bitquill.delicious.intent.extra.BOOKMARK";
    public static final String EXTRA_SHARED = "net.bitquill.delicious.intent.extra.SHARED";
    public static final String EXTRA_TAG = "net.bitquill.delicious.intent.extra.TAG";
    
    private NotificationManager mNotificationManager;
    private ConnectivityManager mConnectivityManager;
    private TelephonyManager mTelephonyManager;
    
    private boolean mSyncActive;
    
    /**
     * Cancel repeating tag sync alarms; won't actually cancel an ongoing sync.
     * @param context
     */
    public static void syncCancel (Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(context, BookmarkService.class);
        intent.setAction(ACTION_SYNC_TAGS);
        alarmManager.cancel(PendingIntent.getService(context, 0, intent, 0));
    }
    
    /**
     * Register an alarm for periodic tag syncing in the background.
     * @param context
     * @param interval
     */
    public static void syncSchedule (Context context, long interval) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        long firstWake = System.currentTimeMillis() + interval;
        Intent intent = new Intent(context, BookmarkService.class);
        intent.setAction(ACTION_SYNC_TAGS);
        alarmManager.setInexactRepeating(AlarmManager.RTC, 
                firstWake, interval, 
                PendingIntent.getService(context, 0, intent, 0));
    }
    
    /**
     * Register or cancel alarm for periodic tag sync, depending on preference value.
     * @param context
     */
    public static void syncUpdate (Context context) {
        SharedPreferences settings = 
            context.getSharedPreferences(DeliciousApp.PREFS_NAME, MODE_PRIVATE);
        int intervalInHours = Integer.parseInt(settings.getString(SettingsActivity.PREF_TAG_BG_SYNC, "24"));
        if (intervalInHours > 0) {
            syncSchedule(context, DateUtils.HOUR_IN_MILLIS * intervalInHours);
        } else {
            syncCancel(context);
        }
    }
    
    /**
     * Initiate a tag sync in the background.
     * @param context
     * @param force Set to true if sync desired even on 2G networks.
     */
    public static void actionSyncTags (Context context, boolean force) {
        Intent intent = new Intent(context, BookmarkService.class);
        intent.setAction(BookmarkService.ACTION_SYNC_TAGS);
        if (force) {
            intent.putExtra(EXTRA_FORCE_SYNC, true);
        }
        context.startService(intent);
    }
    
    /**
     * Submit a bookmark to Delicious in the background.
     * @param context
     * @param bookmark
     * @param shared
     */
    public static void actionSubmitBookmark (Context context, Bookmark bookmark, boolean shared) {
        Intent intent = new Intent(context, BookmarkService.class);
        intent.setAction(BookmarkService.ACTION_SUBMIT_BOOKMARK);
        intent.putExtra(BookmarkService.EXTRA_BOOKMARK, bookmark);
        intent.putExtra(BookmarkService.EXTRA_SHARED, shared);
        context.startService(intent);
    }
		
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		mSyncActive = false;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		String action = intent.getAction();
		if (ACTION_SYNC_TAGS.equals(action)) {
		    boolean forceSync = intent.hasExtra(EXTRA_FORCE_SYNC) && 
		        intent.getBooleanExtra(EXTRA_FORCE_SYNC, false);
		    syncTags(!forceSync);
		} else if (ACTION_SUBMIT_BOOKMARK.equals(action)) {
		    Bookmark bookmark = intent.getParcelableExtra(EXTRA_BOOKMARK);
		    boolean shared = intent.getBooleanExtra(EXTRA_SHARED, true);
		    submitBookmark(bookmark, shared);
		}
	}

	/**
	 * Determine whether the network conditions and settings are suitable
	 * for an update; based on the "Coding for Life" presentation
	 * at GoogleIO 2009.
	 * 
	 * @return True if conditions/settings are suitable for background update.
	 */
	private boolean canSync (boolean requireHighSpeed) {
	    NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
	    // Skip if no connection or background data is disabled
	    if (info == null ||
	            !mConnectivityManager.getBackgroundDataSetting()) {
	        return false;
	    }
	    // Skip if roaming
	    int netType = info.getType();
	    if (netType == ConnectivityManager.TYPE_MOBILE
	            && mTelephonyManager.isNetworkRoaming()) {
	        return false;
	    }
	    
	    // Read user setting
	    SharedPreferences settings = getSharedPreferences(DeliciousApp.PREFS_NAME, 
	            Context.MODE_PRIVATE);
	    boolean syncOn2G = settings.getBoolean(SettingsActivity.PREF_SYNC_ON_2G, false);

	    // Check if connection speed is appropriate
        int netSubtype = info.getSubtype();
	    if (syncOn2G || !requireHighSpeed) {
	        return info.isConnected();
	    } else if (netType == ConnectivityManager.TYPE_WIFI) {
	        return info.isConnected();
    	} else if (netType == ConnectivityManager.TYPE_MOBILE
    	        && netSubtype == TelephonyManager.NETWORK_TYPE_UMTS
    	        && !mTelephonyManager.isNetworkRoaming()) {
    	    return info.isConnected();
    	} else {
    	    return false;
    	}
	}
	
	/**
	 * Start sync in background, if possible and appropriate.
	 */
	private void syncTags (boolean requireHighSpeed) {
	    Log.d(TAG, "syncTags()");
	    if (mSyncActive) {
	        return;
	    }
	    Log.d(TAG, "Sync not active");
		final DeliciousApp app = DeliciousApp.getInstance();
		
		// If not possible or not appropriate, don't sync
		if (!app.mDeliciousClient.hasCredentials() || !canSync(requireHighSpeed)) {
			return;
		}

		Thread syncThread = new Thread() {
			@Override
			public void run () {
				TagsCache.syncTags(app);
				mSyncActive = false;
				mNotificationManager.cancel(R.drawable.stat_notify_sync);
			}
		};

		Notification notification = new Notification(R.drawable.stat_notify_sync, null, System.currentTimeMillis());
		notification.setLatestEventInfo(this, 
		        getText(R.string.notify_sync_title), getText(R.string.notify_sync_text), 
		        makeTagListPendingIntent());
		notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
		mNotificationManager.notify(R.drawable.stat_notify_sync, notification);  // Use drawable ID as unique notification ID

		mSyncActive = true;
		syncThread.start();
	}

	private void submitBookmark(final Bookmark bookmark, final boolean shared) {
	    final DeliciousApp app = DeliciousApp.getInstance();
	    if (!app.mDeliciousClient.hasCredentials()) {
	        // FIXME popup error notification instead; although this should not happen??
	        return;
	    }

	    Thread addBookmarkThread = new Thread() {
            @Override
            public void run () {
                boolean success = app.mDeliciousClient.addBookmark(bookmark, true, shared);
                mNotificationManager.cancel(R.drawable.stat_notify_submit);
                if (!success) {
                    Notification notification = new Notification(R.drawable.stat_notify_error, null, System.currentTimeMillis());
                    notification.setLatestEventInfo(BookmarkService.this, 
                            getText(R.string.notify_error_title), getText(R.string.notify_error_text), 
                            makeBookmarkPendingIntent(bookmark, shared, PendingIntent.FLAG_ONE_SHOT));
                    notification.flags |= Notification.FLAG_AUTO_CANCEL;
                    mNotificationManager.notify(R.drawable.stat_notify_error, notification); // FIXME notification id
                }
            }
        };

        Notification notification = new Notification(R.drawable.stat_notify_submit, null, System.currentTimeMillis());
        notification.setLatestEventInfo(this, 
                getText(R.string.notify_submit_title), getText(R.string.notify_submit_text),
                makeBookmarkPendingIntent(bookmark, shared, PendingIntent.FLAG_UPDATE_CURRENT));
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        mNotificationManager.notify(R.drawable.stat_notify_submit, notification);

        addBookmarkThread.start();
	}
	
	private PendingIntent makeBookmarkPendingIntent (Bookmark bookmark, boolean shared, int flags) {
	    Intent intent = new Intent(this, BookmarkActivity.class);
	    intent.setAction(Intent.ACTION_INSERT);
	    intent.putExtra(EXTRA_BOOKMARK, bookmark);
	    intent.putExtra(EXTRA_SHARED, shared);
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    return PendingIntent.getActivity(this, 0, intent, flags);
	}
	
	private PendingIntent makeTagListPendingIntent () {
	    Intent intent = new Intent(this, TagListActivity.class);
	    intent.setAction(Intent.ACTION_VIEW); // XXX
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}
	
}
