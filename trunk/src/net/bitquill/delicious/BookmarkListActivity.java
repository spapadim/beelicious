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
import net.bitquill.delicious.api.DeliciousClient;
import android.app.Activity;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

// FIXME - this code is f***ing crufty, jeezus!!
public class BookmarkListActivity extends ListActivity implements ListView.OnScrollListener {
	
	private static final String LOG_TAG = DeliciousApp.LOG_TAG;

    // Menu item IDs
	private static final int MENU_ITEM_ADD = Menu.FIRST;
	private static final int MENU_ITEM_SEARCH = Menu.FIRST + 1;
    private static final int MENU_ITEM_SETTINGS = Menu.FIRST + 2;
    private static final int MENU_ITEM_VIEW = Menu.FIRST + 3;
    private static final int MENU_ITEM_EDIT = Menu.FIRST + 4;

    // Request codes
    private static final int REQ_LOGIN = 100;
    
    private String mQueryTag;
    
    private static final int SEARCH_RESULT_SIZE_INCREMENT = 20;

	private TextView mHeaderText;
	private View mFooterView;
	
	// Assign "random" message type IDs
	private static final int MSG_BOOKMARKS_RECEIVED = R.id.progressBar;

	private BookmarkResultHandler mResultHandler;
	
	public static void actionSearchTag (Context context, String tag) {
		Intent intent = new Intent(context, BookmarkListActivity.class);
		intent.setAction(Intent.ACTION_SEARCH);
		intent.putExtra(SearchManager.QUERY, tag);
		context.startActivity(intent);
	}
	
	// XXX - Start cleaning up here... jeez!
	private static class BookmarkResultHandler extends ActivityHandler<BookmarkListActivity> {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_BOOKMARKS_RECEIVED:
				BookmarkListActivity activity = getActivity();
				if (activity == null) {
					Log.v(LOG_TAG, "Bummer, null activity");
					break;
				}
				//Log.v(LOG_TAG, "handleMessage, activity.mQueryTag = " + activity.mQueryTag);

				BookmarkSearchResults searchResults = (BookmarkSearchResults)msg.obj;
				if (searchResults != null) {
					// Fetch success
					activity.addSearchResultsToList(searchResults);
				} else {
					// Fetch failure
					activity.setEmptyViewMessageFailed();
				}
				
				// Remove footer view with message that additional results are loading
				activity.showLoadingFooter(false);
				
				activity.mFetchPending = false;

				break;
			}
		}
	};

	private void addSearchResultsToList (BookmarkSearchResults results) {
		if (results.getStart() == 0) {
			// First batch of results
			BookmarksAdapter baseAdapter = new BookmarksAdapter(this, results);
			mHeaderText.setText(baseAdapter.getHeaderTitle());
			setListAdapter(baseAdapter);
		} else {
			// Subsequent batch of results that needs to be appended
			BookmarksAdapter baseAdapter = (BookmarksAdapter)getListAdapter();
			baseAdapter.addAll(results);
		}		
	}
	
	private void setEmptyViewMessageFailed () {
		ProgressBar bar = (ProgressBar)findViewById(R.id.progressBar);
		TextView txt = (TextView)findViewById(R.id.loadingText);
		bar.setVisibility(View.INVISIBLE);
		txt.setText(R.string.load_failed);		
	}
	
	private void showLoadingFooter (boolean show) {
		setProgressBarIndeterminateVisibility(show);
		if (show) {
			getListView().addFooterView(mFooterView, null, false);
		} else {
			getListView().removeFooterView(mFooterView);
		}
	}
	
	@Override
	protected void onDestroy() {
		mResultHandler.setActivity(null);
		super.onDestroy();
	}
	
	private static final String QUERY_TAG = "query_tag";
	private static final String FETCH_PENDING = "fetch_pending";
	
	@Override
	public void onSaveInstanceState (Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(QUERY_TAG, mQueryTag);
		outState.putBoolean(FETCH_PENDING, mFetchPending);
	}

	/**
	 * Utility class to bundle a pair of values: a handler reference and an adapter reference. 
	 */
	private static class NonConfigurationState {
		public BookmarkResultHandler mResultHandler;
		public BookmarksAdapter mAdapter;
		public NonConfigurationState (BookmarkResultHandler resultHandler, BookmarksAdapter adapter) {
			mResultHandler = resultHandler;
			mAdapter = adapter;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.bookmark_list);
		
		NonConfigurationState nonConfigState = (NonConfigurationState)getLastNonConfigurationInstance();
		BookmarksAdapter previousAdapter = (nonConfigState != null) ? nonConfigState.mAdapter : null;
		
		if (nonConfigState != null) {
			mResultHandler = nonConfigState.mResultHandler;
		} else {
			mResultHandler = new BookmarkResultHandler();
		}
		mResultHandler.setActivity(this);
		
    	Intent intent = getIntent();
		if (savedInstanceState != null) {
			mFetchPending = savedInstanceState.getBoolean(FETCH_PENDING);
			mQueryTag = savedInstanceState.getString(QUERY_TAG);
		} else {
			mFetchPending = false;
			mQueryTag = null;
			if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			    mQueryTag = intent.getStringExtra(BookmarkService.EXTRA_TAG);
			} else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
				mQueryTag = intent.getStringExtra(SearchManager.QUERY); 
			}
		}
		setProgressBarIndeterminateVisibility(mFetchPending);
		
		mHeaderText = (TextView)getLayoutInflater()
				.inflate(R.layout.bookmark_list_header, getListView(), false);
		mFooterView = getLayoutInflater()
				.inflate(R.layout.bookmark_list_footer, getListView(), false);
		getListView().addHeaderView(mHeaderText, null, false);
		
		getListView().setOnScrollListener(this);
		
		registerForContextMenu(getListView());

        // If this is the first time running, we need to prompt for login info
        if (!DeliciousApp.getInstance().mDeliciousClient.hasCredentials()) { 
        	LoginActivity.actionLogin(this, REQ_LOGIN);
        	// Defer fetching bookmark list until login info is set 
        } else {
        	if (previousAdapter != null) {
        		setListAdapter(previousAdapter);
				mHeaderText.setText(previousAdapter.getHeaderTitle());
        	} else if (!mFetchPending) {
        		//Log.v(LOG_TAG, "Starting onCreate() fetch");
            	fetchBookmarks(0);        		
        	}
        }
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return new NonConfigurationState(mResultHandler, (BookmarksAdapter)getListAdapter());
	}

	private void fetchBookmarks (final int start) {
		//Log.v(LOG_TAG, "In fetchBookmarkList(" + start + ")...");
        Thread fetchThread = new Thread() {
        	@Override
        	public void run () {
        		DeliciousClient deliciousClient = DeliciousApp.getInstance().mDeliciousClient;
        		Message msg = mResultHandler.obtainMessage(MSG_BOOKMARKS_RECEIVED);
        		if (mQueryTag != null) {
        			msg.obj = deliciousClient.searchBookmarks(mQueryTag, SEARCH_RESULT_SIZE_INCREMENT, start);
        		} else {
        			msg.obj = deliciousClient.getRecent(null, SEARCH_RESULT_SIZE_INCREMENT);
        		}
        		mResultHandler.sendMessage(msg);
    			//Log.v(LOG_TAG, "Fetch thread completed!");
        	}
        };
        mFetchPending = true;
		fetchThread.start();		
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQ_LOGIN) {
			if (resultCode == Activity.RESULT_CANCELED) {
				// If user hit cancel in login screen, we must also exit
				finish();
			} else {
			    // Do initial tag fetch
			    BookmarkService.actionSyncTags(this, true);
				// Do deferred fetch of bookmark list
				fetchBookmarks(0);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_ITEM_ADD, 0, R.string.menu_add)
			.setShortcut('5', 'a')
			.setIcon(android.R.drawable.ic_menu_add);
		menu.add(0, MENU_ITEM_SEARCH, 0, R.string.menu_search)
			.setShortcut('2', 'l') // TODO
			.setIcon(android.R.drawable.ic_menu_search);
		menu.add(0, MENU_ITEM_SETTINGS, 0, R.string.menu_settings)
			.setShortcut('1', 's')
			.setIcon(android.R.drawable.ic_menu_preferences);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ITEM_ADD:
        	startActivity(new Intent(this, BookmarkActivity.class));
        	return true;
        case MENU_ITEM_SEARCH:
        	onSearchRequested();
        	return true;
        case MENU_ITEM_SETTINGS:
        	startActivity(new Intent(this, SettingsActivity.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
	}

	private void viewUrl (String url) {
        Intent i = new Intent();
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);	    
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Bookmark bm = (Bookmark) l.getItemAtPosition(position);
		viewUrl(bm.getUrl());
	}

	@Override
    public boolean onContextItemSelected(MenuItem item) {
	    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	    Bookmark bm = (Bookmark) getListView().getItemAtPosition(info.position);
	    switch (item.getItemId()) {
	    case MENU_ITEM_VIEW:
	        viewUrl(bm.getUrl());
	        return true;
	    case MENU_ITEM_EDIT:
	        BookmarkActivity.actionEditBookmark(this, bm);
	        return true;
	    default:
	        return super.onContextItemSelected(item);
	    }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, MENU_ITEM_VIEW, 0, R.string.menu_view);
        menu.add(0, MENU_ITEM_EDIT, 0, R.string.menu_edit);
    }

    private boolean mFetchPending = false;
	
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {

		// Do nothing if another fetch is pending completion
		if (mFetchPending) {
			return;
		}

		// If end of list is visible
		if (firstVisibleItem + visibleItemCount >= totalItemCount) {
			BookmarksAdapter baseAdapter = (BookmarksAdapter)getListAdapter();
			if (baseAdapter == null) {
				// No results yet!
				return;
			}
			int totalResultCount = baseAdapter.getTotal();
			// If there are more results than items currently in the list
			if (totalItemCount < totalResultCount) {
				//Log.v(LOG_TAG, "Start fetch more items, start offset " + totalItemCount);
				showLoadingFooter(true);
				fetchBookmarks(baseAdapter.getCount()); // different from totalItemCount, which includes header view(s)
			}
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// Do nothing
	}
	
}
