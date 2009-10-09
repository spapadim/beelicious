package net.bitquill.delicious;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.SimpleCursorAdapter;

public class TagListActivity extends ListActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.tag_list);
		
		Cursor tagsCursor = TagsCache.getCursor(this, null);
		//startManagingCursor(tagsCursor);  // XXX
		SimpleCursorAdapter tagsAdapter = new SimpleCursorAdapter(this, 
                android.R.layout.simple_list_item_1, 
                tagsCursor, 
                new String[]{"tag"}, 
                new int[] {android.R.id.text1});
		setListAdapter(tagsAdapter);
	}

}
