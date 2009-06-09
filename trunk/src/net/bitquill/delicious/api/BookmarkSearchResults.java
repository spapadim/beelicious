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

import java.util.ArrayList;


/**
 * A list of bookmarks, representing a range  in the list of results from a bookmark query.
 * The range is represented by a start offset and a count.
 * The list can be extended with additional results, but only if its start offset is zero.
 * 
 * The query is a represented by a tag and specifies a list consisting of 
 * "all bookmarks that contain that tag".  A null query tag has special meaning of
 * "user's most recent bookmarks"; such a list is always non-extensible.
 * 
 * XXX - next cruft-scrubbing target; this null business is ugly, for starters
 */
public class BookmarkSearchResults {
	
	public static final int TOTAL_UNDEFINED = -1;
	
	/**
	 * The query tag, which defines which bookmarks are contained in the list.
	 * A null value has special meaning of "user's most recent bookmarks".
	 */
	private String mQueryTag;
	
	/**
	 * The total size of the set of bookmarks containing the query tag.
	 * An unknown total size is represented by {@link TOTAL_UNDEFINED}.  
	 */
	private int mTotal;
	
	/**
	 * The start offset of the partial results stored.  Should be less than {@link mTotal}.
	 */
	private int mStart;
	
	/**
	 * The list of (potentiall partial) results.
	 */
	private ArrayList<Bookmark> mPosts;
	
	public BookmarkSearchResults (String queryTag, int start, int total) {
		mStart = start;
		mTotal = total;
		mQueryTag = queryTag;
		mPosts = new ArrayList<Bookmark>();
	}
	
	public BookmarkSearchResults (String queryTag) {
		this(queryTag, 0, TOTAL_UNDEFINED);
	}
	
	/**
	 * Adds a single bookmark to the result list. Does not check if the list will exceed
	 * the total length; the caller should make sure not to violate this condition.
	 * @param b  Bookmark to add in the result list.
	 * @return   true
	 */
	public boolean add (Bookmark b) {
		return mPosts.add(b);
	}
	
	/**
	 * Extend the list of partial results with the results contained in {@link tail}.
	 * The starting offset of {@link tail} should exactly match the end of the current
	 * list of partial results.
	 * 
	 * A list that represents the most recent bookmarks (null query tag) cannot be extended.
	 * 
	 * @param tail  Additional results to append in the current list of (partial) results.
	 * @return
	 */
	public boolean addAll (BookmarkSearchResults tail) {
		if (getQueryTag() == null || mTotal == TOTAL_UNDEFINED) {
			throw new IllegalArgumentException("Cannot extend null-query or unknown length results");
		}
		if (!getQueryTag().equals(tail.getQueryTag())) {
			throw new IllegalArgumentException("Query tags do not match");
		}
		if (tail.getStart() != size() || size() + tail.size() > getTotal()) {
			throw new IllegalArgumentException("Offset of additional results does not match");
		}
		return mPosts.addAll(tail.mPosts);
	}
	
	public String getQueryTag()  { return mQueryTag; }
	public int getStart ()  { return mStart; }
	public int getTotal ()  { return (mTotal == TOTAL_UNDEFINED) ? size() : mTotal; }
	public int size()       { return mPosts.size(); }
	
	public Bookmark get (int location) { return mPosts.get(location); }
	
	@Override
	public String toString () {
		return "BookmarkSearchResults (query tag='" + getQueryTag() + 
			"', total=" + getTotal() + ", start=" + getStart() + 
			", size=" + size();
	}
}