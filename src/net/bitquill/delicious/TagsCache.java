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

import java.text.ParseException;
import java.util.Date;
import java.util.Map;

import net.bitquill.delicious.api.Bookmark;
import net.bitquill.delicious.api.DeliciousClient;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

/**
 * Auxiliary class to manage a trivial cache of the user's tags.
 * Stores tags, as returned by {@link DeliciousClient#getTags()}, in an SQLite database.
 */
public final class TagsCache {
	
	private static final String DATABASE_NAME = "tagcache.db";
	private static final int DATABASE_VERSION = 1;
	
	private static final String PREF_KEY_UPDATE = "tags_last_sync";
	
	private static class DatabaseHelper extends SQLiteOpenHelper {

		DatabaseHelper (Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE tags ("
            		+ "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "tag TEXT,"
                    + "count INTEGER"
                    + ");");			
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS tags");
            onCreate(db);			
		}
	}

	private TagsCache () { }  // cannot instantiate
	
	public static final void syncTags (Context context) {
		DeliciousClient deliciousClient = DeliciousApp.getInstance().mDeliciousClient;
		DatabaseHelper helper = new DatabaseHelper(context);
		SQLiteDatabase db = helper.getWritableDatabase();
		
		try {
			Map<String,Integer> tags = deliciousClient.getTags();
			if (tags == null) {
				Toast.makeText(context, R.string.fetch_tags_failed_message, Toast.LENGTH_LONG)
					.show();
				return;
			}
			db.delete("tags", null, null); // Delete all rows
			ContentValues values = new ContentValues();
			for (Map.Entry<String,Integer> e : tags.entrySet()) {
				values.put("tag", e.getKey());
				values.put("count", e.getValue());
				db.insert("tags", null, values);
			}
		} finally {
			db.close();
		}
		
		// Save update date to preferences
		SharedPreferences settings = 
			context.getSharedPreferences(DeliciousApp.PREFS_NAME, Context.MODE_PRIVATE);
		Editor editor = settings.edit();
		editor.putString(PREF_KEY_UPDATE, Bookmark.utcString(new Date()));
		editor.commit();
	}
	
	public static final Date getLastSyncDate (Context context) {
		SharedPreferences settings = 
			context.getSharedPreferences(DeliciousApp.PREFS_NAME, Context.MODE_PRIVATE);
		String lastUpdateStr = settings.getString(PREF_KEY_UPDATE, null);
		try {
			if (lastUpdateStr != null) {
				return Bookmark.utcParse(lastUpdateStr);
			}
		} catch (ParseException e) { }
		return null;
	}
	
	public static final void checkSync (Context context) {
		Date lastUpdate = getLastSyncDate(context);
		// TODO - check first
	}
	
	/**
	 * Return a cursor, sorted by count descending.
	 * @param context
	 * @param filterStr
	 * @return
	 */
	public static final Cursor getCursor (Context context, CharSequence filterStr) {
		DatabaseHelper helper = new DatabaseHelper(context);
		SQLiteDatabase db = helper.getReadableDatabase();
		if (filterStr != null && filterStr.length() > 0) {
			return db.query("tags", new String[] {"_id", "tag", "count"}, 
					//"tag LIKE '%?%'", new String[] {filterStr.toString()},
					"tag LIKE '" + filterStr + "%'", null, // FIXME - figure out why above form does not work
					null, null, "count DESC");			
		} else {
			return db.query("tags", new String[] {"_id", "tag", "count"}, 
					null, null, null, null, "count DESC");
		}
	}
}
