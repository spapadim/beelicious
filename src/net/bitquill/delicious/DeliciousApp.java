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

import net.bitquill.delicious.api.DeliciousClient;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Application object that instantiates a singleton {@link DeliciousClient} instance.
 */
public final class DeliciousApp extends Application {
	
	public static final String LOG_TAG = "bitlicious";
	
	public static final String PREFS_NAME = "BitliciousPreferences";
	
	private static DeliciousApp sMe;
	
	public DeliciousClient mDeliciousClient;
	
	public DeliciousApp () {
		sMe = this;
	}
	
	public static final DeliciousApp getInstance () {
		return sMe;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();

		// Initialize new Delicious HTTP client
		mDeliciousClient = new DeliciousClient();

		// Check for valid login credentials
		SharedPreferences settings = 
			getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		String username = settings.getString("delicious_username", null);
		String password = settings.getString("delicious_password", null);

		// Set credentials on client; if null, client will ignore
		mDeliciousClient.setCredentials(username, password);
	}

}
