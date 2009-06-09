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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Simple service for background tag cache sync. 
 */
public class TagSyncService extends Service {
	
	public static final int START_NOW = 1;
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		if (startId == START_NOW) {
			startSync();
		}
	}
	
	/**
	 * Start sync in background, iff user has provided login credentials.
	 */
	private void startSync () {
		final DeliciousApp app = DeliciousApp.getInstance();
		
		if (!app.mDeliciousClient.hasCredentials()) {
			return;
		}

		Thread syncThread = new Thread() {
			@Override
			public void run () {
				TagsCache.syncTags(app);
			}
		};
		syncThread.start();
	}

}
