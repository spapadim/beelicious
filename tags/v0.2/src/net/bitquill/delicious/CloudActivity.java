package net.bitquill.delicious;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.TextView;

public class CloudActivity extends Activity {
	
	public static final int TAG_CLOUD_SIZE = 52;  // Max number of tags to include
	
	public static final float MAX_FONT_SCALE = 2.2f;
	public static final float MIN_FONT_SCALE = 1.0f;
	public static final float LINEAR_FONT_SCALE_BOOST = 1.25f; // hack to better fill screen - rescale if using non-logarithmic tag sizes
	
	private Map<String,Float> mCloud;
	private TextView mCloudText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tag_cloud);
		
		mCloudText = (TextView)findViewById(R.id.cloudText);
		
		// Restore cloud size-factors map, if available
		@SuppressWarnings("unchecked")
		Map<String,Float> cloud = (Map<String,Float>)getLastNonConfigurationInstance();
		if (cloud == null) {
			cloud = calculateCloud (); 
		}
		mCloud = cloud;

		setupCloudText();
	}
	
	private Map<String,Float> calculateCloud () {
		Map<String,Float> cloud = new HashMap<String,Float>();
				
		// Get font scaling setting
		boolean logScaling = getSharedPreferences(DeliciousApp.PREFS_NAME, MODE_PRIVATE)
			.getBoolean("cloud_logarithmic", true);

		// Get absolute counts first
		Cursor c = TagsCache.getCursor(this, null); // sorted by count descending
		//startManagingCursor(c);  // XXX - do I need this or not??
		int tagColId = c.getColumnIndex("tag");
		int countColId = c.getColumnIndex("count");
		float minCount = Float.MAX_VALUE;
		float maxCount = Float.MIN_VALUE;
		if (c.moveToFirst()) {  // cursor is non-empty
			for (int i = 0;  i < TAG_CLOUD_SIZE;  i++) {
				float count = c.getFloat(countColId);
				cloud.put(c.getString(tagColId), count);
				minCount = Math.min(minCount, count);
				maxCount = Math.max(maxCount, count);
				if (!c.moveToNext()) {
					break;
				}
			}
		}
		c.close();
		
		// Re-scale range [minCount,maxCount] to [MIN_FONT_SCALE,MAX_FONT_SCALE]
		float slope = 1.0f;
		if (logScaling) {
			slope = (MAX_FONT_SCALE - MIN_FONT_SCALE)/((float)(Math.log(maxCount) - Math.log(minCount)));
		} else {
			slope = (MAX_FONT_SCALE - MIN_FONT_SCALE)/(maxCount - minCount);
		}
		for (Map.Entry<String,Float> e : cloud.entrySet()) {
			if (logScaling) {
				e.setValue(MIN_FONT_SCALE + slope*((float)(Math.log(e.getValue()) - Math.log(minCount))));			
			} else {
				e.setValue(LINEAR_FONT_SCALE_BOOST*(MIN_FONT_SCALE + slope*(e.getValue() - minCount)));
			}
		}

		return cloud;
	}
	
	private class TagSpan extends ClickableSpan {
		private String mTag;
		
		public TagSpan(String tag) {
			mTag = tag;
		}
		
		@Override
		public void onClick(View view) {
			android.util.Log.v(DeliciousApp.LOG_TAG, "onClick tag " + mTag);
			BookmarkListActivity.actionSearchTag(view.getContext(), mTag);
		}
		
		@Override
		public void updateDrawState(TextPaint ds) {
			// Do not modify appearance (underline and use link color)
		}
	}
	
	private void setupCloudText () {
		Map<String,Float> cloud = mCloud;
		
		// Sort the tags alphabetically
		List<String> sortedTags;
		sortedTags = new ArrayList<String>(cloud.keySet());
		Collections.sort(sortedTags);

		// Build spannable with tags
		SpannableStringBuilder text = new SpannableStringBuilder();
		for (String tag : sortedTags) {
			text.append(' ');
			int spanStart = text.length();
			text.append(tag);
			int spanEnd = text.length();
			text.setSpan(new TagSpan(tag), spanStart, spanEnd, 0);
			text.setSpan(new RelativeSizeSpan(cloud.get(tag)), spanStart, spanEnd, 0);
		}
		
		mCloudText.setText(text);
		mCloudText.setMovementMethod(LinkMovementMethod.getInstance()); // XXX - why doesn't android:linksClickable work?
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return mCloud;
	}

}
