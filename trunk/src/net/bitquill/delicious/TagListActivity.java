package net.bitquill.delicious;

import android.app.Activity;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

public class TagListActivity extends ListActivity {

    private static final int REQ_LOGIN = 100;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.tag_list);
		setListAdapter(TagsCache.getCursorAdapter(this, null, true)); // XXX managed?
		getListView().setTextFilterEnabled(true);
		
        // We need to check valid login info, since this is an alternative entry app entry point
        // If this is the first time running, we need to prompt for login info
        if (!DeliciousApp.getInstance().mDeliciousClient.hasCredentials()) {
            LoginActivity.actionLogin(this, REQ_LOGIN);
            // FIXME - do this check only when called "externally" ??
        }
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
            }
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String tag = (String)l.getItemAtPosition(position);
        Intent intent = new Intent(this, BookmarkListActivity.class);
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(SearchManager.QUERY, tag);
        startActivity(intent);
    }

    private static final int MENU_ITEM_ADD = Menu.FIRST;
    private static final int MENU_ITEM_SETTINGS = Menu.FIRST + 1;
    private static final int MENU_ITEM_TAGCLOUD = Menu.FIRST + 2;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ITEM_ADD, 0, R.string.menu_add)
            .setShortcut('5', 'a')
            .setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, MENU_ITEM_TAGCLOUD, 0, R.string.menu_tagcloud)
            .setShortcut('9', 'c') // TODO
            .setIcon(R.drawable.ic_menu_cloud); 
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
        case MENU_ITEM_TAGCLOUD:
            startActivity(new Intent(this, CloudActivity.class));
            return true;
        case MENU_ITEM_SETTINGS:
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }    
}
