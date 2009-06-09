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
import net.bitquill.delicious.api.BookmarkSearchResults;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * A list adapter for {@link BookmarkSearchResults} lists.
 */
public class BookmarksAdapter extends BaseAdapter {

	private BookmarkSearchResults mBookmarks;
	private Context mContext;
	private LayoutInflater mInflater;
	
	public BookmarksAdapter (Context context, BookmarkSearchResults bookmarks) {
		if (bookmarks.getStart() != 0) {
			throw new IllegalArgumentException("Initial search results must have zero start offset");
		}
		mContext = context;
		mBookmarks = bookmarks;
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	public int getTotal ()  { return mBookmarks.getTotal(); }
	public String getQueryTag ()   { return mBookmarks.getQueryTag(); }
	
	public Context getContext() { return mContext; }
	
	public String getHeaderTitle () {
		String queryTag = getQueryTag();
		if (queryTag != null) {
			return getContext().getText(R.string.search_header_title) +
					": " + queryTag + " (" + getTotal() + ")";
		} else {
			return (String)getContext().getText(R.string.recent_header_title);
		}			
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view;
        if (convertView == null) {
            view = mInflater.inflate(R.layout.bookmark_list_item, parent, false);
        } else {
            view = convertView;
        }

        TextView titleText = (TextView)view.findViewById(R.id.title);
        titleText.setText(getItem(position).getDescription());
		TextView tagsText = (TextView)view.findViewById(R.id.tags);
		tagsText.setText(getItem(position).getTags());
		//TextView dateText = (TextView)view.findViewById(R.id.date);
		//dateText.setText("19-Jan-09"); // FIXME test value
		return view;
	}
	
	@Override
	public Bookmark getItem (int position) {
		return mBookmarks.get(position);
	}
	
	@Override
	public long getItemId(int position) {
        return position;
    }
	
	@Override
	public int getCount() {
        return mBookmarks.size();
    }
	
	public void addAll (BookmarkSearchResults tail) {
		mBookmarks.addAll(tail);
		android.util.Log.v("bitlicious", "Added " + tail.size() + " items, will call notify-changed");
		notifyDataSetChanged();
	}
}