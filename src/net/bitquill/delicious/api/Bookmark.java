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
package net.bitquill.delicious.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Simple immutable class to encapsulate all Del.icio.us bookmark fields.
 * See http://www.delicious.com/help/api for details.
 */
public final class Bookmark implements Parcelable {
	private String url, description, extended, tags;
	private Date date;
	private String meta; // optional field

	private static final char[] HEX_CHARS = {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
	};
	
	// XXX - Untested!!!
	public static final String digestHash (String str) {
		// Compute MD5 digest
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("md5");
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
		md.update(str.getBytes());

		// Convert to hex string
		byte[] hash = md.digest();
		char[] buf = new char[hash.length*2];
		for (int i = 0;  i < hash.length;  i++) {
			buf[2*i]   = HEX_CHARS[(hash[i] >> 4) & 0xF];
			buf[2*i+1] = HEX_CHARS[hash[i] & 0xF];
		}
		return new String(buf);
	}
	
	private static final String ISO_DATE_SPEC = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	private static final DateFormat sUtcDateFormat;
	static {
		sUtcDateFormat = new SimpleDateFormat(ISO_DATE_SPEC);
		sUtcDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	/**
	 * Return an ISO-formatted representation of {@link date} in UTC.
	 * @param date   Timestamp.
	 * @return       ISO-formatted representation of {@link date}.
	 */
	public static final String utcString (Date date) {
		return sUtcDateFormat.format(date);
	}
	
	/**
	 * Parse an ISO-formatted date-time string, assumed to represent time in UTC.
	 * 
	 * @param dateString   ISO-formatted (e.g., "2009-01-18T01:05:31Z") date-time.
	 * @return             Date, assuming string represents time in UTC.
	 * @throws ParseException
	 */
	public static final Date utcParse (String dateString) throws ParseException {
		return sUtcDateFormat.parse(dateString);
	}	

	/**
	 * Constructor meant primarily for use by Delicious client functions.
	 */
	public Bookmark (String url, String description,
			String extended, String tags, String dateString,
			String meta) throws ParseException {
		this(url, description, extended, tags, utcParse(dateString));
		this.meta = meta;
	}
	
	public Bookmark (String url, String description, 
			String extended, String tags, Date date) {
		this.url = url;
		this.description = description;
		this.extended = extended;
		this.tags = tags;
		this.date = (Date) date.clone();
	}
	
	private Bookmark (Parcel in) {
	    this.url = in.readString();
	    this.description = in.readString();
	    this.extended = in.readString();
	    this.tags = in.readString();
	    try {
	        this.date = utcParse(in.readString());
	    } catch (ParseException pe) {
	        this.date = new Date(); // XXX 
	    }
	    int hasMeta = in.readInt();
	    if (hasMeta != 0) {
	        this.meta = in.readString();
	    }
	}
	
	@Override
	public void writeToParcel(Parcel out, int flags) {
	    out.writeString(this.url);
	    out.writeString(this.description);
	    out.writeString(this.extended);
	    out.writeString(this.tags);
	    out.writeString(utcString(this.date));
	    if (this.meta == null) {
	        out.writeInt(0);
	    } else {
	        out.writeInt(1);
	        out.writeString(this.meta);
	    }
	}
	
	public static final Parcelable.Creator<Bookmark> CREATOR 
	    = new Parcelable.Creator<Bookmark>() {
	        public Bookmark createFromParcel(Parcel in) {
	            return new Bookmark(in);
	        }

	        public Bookmark[] newArray(int size) {
	            return new Bookmark[size];
	        }
	};
	
	@Override
	public int describeContents () {
	    return 0;
	}
	
	public Bookmark (String url, String description, 
			String extended, String tags) {
		this(url, description, extended, tags, new Date());
	}
	
	public Bookmark (String url, String description, String tags) {
		this(url, description, null, tags);
	}
	
	public String getUrl ()  { return url; }
	public String getDescription()  { return description; }
	public String getExtended()  { return extended; }
	public String getTags()  { return tags; }
	public Date getDate()  { return date; }  // FIXME - dates are mutable objects
	public String getDateString()  { return utcString(date); }
	public String getMeta()  { return meta; }
	public String getHash()  { return digestHash(url); }
	
	@Override
	public String toString ()  { 
		return description;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Bookmark)) {
			return false;
		}
		Bookmark other = (Bookmark)o;
		return url.equals(other.url);
	}

	@Override
	public int hashCode() {
		return url.hashCode();
	}	
}