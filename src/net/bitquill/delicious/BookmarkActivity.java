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

import java.util.ArrayList;

import net.bitquill.delicious.api.Bookmark;
import net.bitquill.delicious.api.DeliciousClient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.provider.Browser;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Toast;

/**
 * Activity for entering bookmark information.
 * 
 * TODO - Support bookmark editing.
 */
public class BookmarkActivity extends Activity {
	
	private static final int REQ_LOGIN = 100;
	
	private DeliciousClient mDeliciousClient;
	
	private EditText mUrlEdit;
	private EditText mTitleEdit;
	private EditText mCommentsEdit;
	private MultiAutoCompleteTextView mTagsEdit;
	private Button mTagsButton;
	private CheckBox mPrivateCheck;
		
	private ArrayList<String> mTagSuggestions;
	
	public static void actionEditBookmark (Context context, Bookmark bookmark) {
	    Intent i = new Intent(context, BookmarkActivity.class);
	    i.setAction(Intent.ACTION_INSERT);
	    i.putExtra(BookmarkService.EXTRA_BOOKMARK, bookmark);
	    context.startActivity(i);
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
	
	/**
	 * A simple space-delimiter tokenizer for tag field auto-completer.
	 */
	private static class SpaceTokenizer implements MultiAutoCompleteTextView.Tokenizer {

		@Override
		public int findTokenEnd(CharSequence text, int cursor) {
			int pos = text.toString().indexOf(' ', cursor);
			return (pos < 0) ? (text.length() - 1) : (pos - 1);
		}

		@Override
		public int findTokenStart(CharSequence text, int cursor) {
			int pos = text.toString().lastIndexOf(' ', cursor);
			return (pos < 0) ? 0 : (pos + 1);
		}

		@Override
		public CharSequence terminateToken(CharSequence text) {
			String textStr = text.toString();
			return textStr.endsWith(" ") ? textStr : (textStr + " ");
		}
		
	}
	
	

    @Override
	protected void onDestroy() {
    	sResultHandler.setActivity(null);
    	super.onDestroy();
	}

	@Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.bookmark);
		sResultHandler.setActivity(this);
		
        mDeliciousClient = DeliciousApp.getInstance().mDeliciousClient;
        
        // We need to check valid login info, since this is an alternative entry app entry point
        // If this is the first time running, we need to prompt for login info
        if (!mDeliciousClient.hasCredentials()) {
        	LoginActivity.actionLogin(this, REQ_LOGIN);
        	// FIXME - do this check only when called "externally" ??
        }
        
        if (savedInstanceState != null) {
        	mTagSuggestions = savedInstanceState.getStringArrayList(TAG_SUGGESTIONS);
        }
        
        SharedPreferences settings = getSharedPreferences(DeliciousApp.PREFS_NAME, MODE_PRIVATE);
        
        mUrlEdit = (EditText)findViewById(R.id.urlEdit);
        mTitleEdit = (EditText)findViewById(R.id.titleEdit);
        mCommentsEdit = (EditText)findViewById(R.id.commentsEdit);
        mTagsEdit = (MultiAutoCompleteTextView)findViewById(R.id.tagsEdit);
        mPrivateCheck = (CheckBox)findViewById(R.id.privateCheck);

        mTagsEdit.setTokenizer(new SpaceTokenizer());
    	mTagsEdit.setAdapter(TagsCache.getCursorAdapter(this, 
    	        android.R.layout.simple_dropdown_item_1line, null, false));

        // Add a text change listener to invalidate tag suggestions if URL field changes
        mUrlEdit.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) { }

			@Override
			public void beforeTextChanged(CharSequence s, int start,
					int count, int after) {	}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (mTagSuggestions != null) {
					mTagSuggestions = null;
	    			removeDialog(ID_SUGGESTIONS_DIALOG);
	    			mTagsButton.setEnabled(true);
				}
			}
        });
        
        Button okButton = (Button)findViewById(R.id.okButton);
        okButton.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick (View view) {
        		postBookmark();
        	}
        });

        Button cancelButton  = (Button)findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick (View view) {
        		setResult(Activity.RESULT_CANCELED);
        		finish();
        	}
        });
        
        mTagsButton = (Button)findViewById(R.id.tagsButton);
        mTagsButton.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick (View view) {
        		showTagSuggestions();
        	}
        });
        
        // Prepend Android tag, if option is enabled in settings
        if (settings.getBoolean(SettingsActivity.PREF_ANDROID_TAG_ENABLE, false)) {
        	String androidTag = settings.getString(SettingsActivity.PREF_ANDROID_TAG_NAME, null);
        	String tags = mTagsEdit.getText().toString();
        	if (androidTag != null && tags.indexOf(androidTag) < 0) { // FIXME - boundary case: tag that is a super-string of androidTag
        		if (tags.length() == 0) {
        			mTagsEdit.setText(androidTag);
        		} else {
        			mTagsEdit.setText(androidTag + " " + tags);
        		}
        	}
        }
        
        // Set private checkbox according to settings
        mPrivateCheck.setChecked(settings.getBoolean(SettingsActivity.PREF_DELICIOUS_PRIVATE, false));
        
        String url = null;
        String title = null;
        
        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) &&
        		"text/plain".equals(intent.getType())) {
        	url = extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_INSERT.equals(action) && 
        		Browser.BOOKMARKS_URI.equals(intent.getData())) {
        	url = intent.getStringExtra("url");
            title = intent.getStringExtra("title");
        } else if (Intent.ACTION_INSERT.equals(action) &&
                intent.hasExtra(BookmarkService.EXTRA_BOOKMARK)) {
            Bookmark bm = intent.getParcelableExtra(BookmarkService.EXTRA_BOOKMARK);
            boolean shared = intent.getBooleanExtra(BookmarkService.EXTRA_SHARED, true);
            url = bm.getUrl();
            title = bm.getDescription();
            mCommentsEdit.setText(bm.getExtended());
            mTagsEdit.setText(bm.getTags());
            mPrivateCheck.setChecked(!shared);
            // Date should be updated, so we ignore it
            // FIXME meta and hash should be preserved, even though currently unused
        }
        
        if (url != null) {
        	mUrlEdit.setText(url);
        	mUrlEdit.setEnabled(false);
        	//mTagsEdit.requestFocus(); // Will pop up soft keyboard
        	
        	// Start fetch threads only if it's a fresh activity
        	if (savedInstanceState == null) {
        		// Title fetch, if necessary
        		if (title == null) {
        			// FIXME - check if it's ok to fire this off, even if we've redirected to the login screen
        			fetchTitle(url); 
        		} else {
        			mTitleEdit.setText(title);
        		}
        		
        		// Suggestions pre-fetch
        		mTagsButton.setEnabled(false);
        		fetchTagSuggestions(url, MSG_TAG_SUGGEST_PREFETCH);
        	}
        }
    }
	
	private String extractUrl (String text) {
	    SpannableString s = new SpannableString(text);
	    Linkify.addLinks(s, Linkify.WEB_URLS);
	    URLSpan spans[] = s.getSpans(0, s.length(), URLSpan.class);
	    if (spans.length > 0) {
	        return spans[0].getURL();
	    } else {
	        return null;
	    }
	}
	
	private void fetchTitle (final String url) {
		Thread fetchTitleThread = new Thread() {
			@Override
			public void run () {
				String title = null;
				try {
				    mDeliciousClient.fetchHtmlTitle(url);
				} catch (Exception e) { }
				if (title != null) {
					title = title.replaceAll("\\s+", " "); // Remove duplicate whitespace
				} else {
					title = url;  // Failed to fetch title, use URL
				}
				Message msg = sResultHandler.obtainMessage(MSG_HTML_TITLE_SET, title);
				sResultHandler.sendMessage(msg);
			}
		};
		mTitleEdit.setText(R.string.fetching_title_message);
		mTitleEdit.setEnabled(false);
		fetchTitleThread.start();		
	}
	
	private static final String TAG_SUGGESTIONS = "tag_suggestions";
	
	@Override
	protected void onSaveInstanceState (Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putStringArrayList(TAG_SUGGESTIONS, mTagSuggestions);
	}
    
    private AlertDialog createTagSuggestionsDialog() {
    	// Add spaces as sentinels, so we can search for entire tokens in a simple way
		final StringBuffer tags = new StringBuffer(" " + mTagsEdit.getText().toString() + " ");

		// Find and mark tag suggestions already present in the tags string
		boolean[] checkedItems = new boolean[mTagSuggestions.size()];
		final String[] tagItems = mTagSuggestions.toArray(new String[mTagSuggestions.size()]);
		for (int i = 0;  i < mTagSuggestions.size();  i++) {
			// Use spaces as token delimiters; safe because we added sentinels above
			checkedItems[i] = (tags.indexOf(" " + tagItems[i] + " ") >= 0);
		}
		
		DialogInterface.OnMultiChoiceClickListener choiceClickListener = 
			new DialogInterface.OnMultiChoiceClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int whichButton,
					boolean isChecked) {
					if (isChecked) {
						// Append corresponding tag and space sentinel
						tags.append(tagItems[whichButton] + " ");
					} else {
						// Remove corresponding tag; again, use spaces as token delimiters
						int start = tags.indexOf(" " + tagItems[whichButton] + " ");
						tags.replace(start, start + tagItems[whichButton].length() + 2, " ");
					}
				}			
		};
		
		DialogInterface.OnClickListener okClickListener = 
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// Trim sentinel spaces first, then set text
					mTagsEdit.setText(tags.toString().trim());  
				}
		};

		AlertDialog dlg = new AlertDialog.Builder(BookmarkActivity.this)
			.setTitle(getText(R.string.tags_suggest_title).toString() + " (" + tagItems.length + ")")
			.setMultiChoiceItems(tagItems, checkedItems, choiceClickListener)
			.setPositiveButton(R.string.ok_button, okClickListener)
			.create();
		
		return dlg;
    }
    
    // Assign "random" message tags
    private static final int MSG_TAG_SUGGEST = R.id.tagsButton;
    private static final int MSG_TAG_SUGGEST_PREFETCH = R.id.tagsEdit;
    private static final int MSG_HTML_TITLE_SET = R.id.titleEdit;
    
    private static final ActivityHandler<BookmarkActivity> sResultHandler = 
    	new ActivityHandler<BookmarkActivity>() {
    	@SuppressWarnings("unchecked")
		@Override
		public void handleMessage(Message msg) {
    		BookmarkActivity activity = getActivity();
    		if (activity == null) {
    			// Bummer, results will be lost!
    			return;
    		}
    		
        	// Handle message
    		ArrayList<String> tagSuggestions = null;
			switch (msg.what) {
			case MSG_TAG_SUGGEST_PREFETCH:
				tagSuggestions = (ArrayList<String>)msg.obj;
				activity.mTagSuggestions = tagSuggestions; 
				if (tagSuggestions != null && tagSuggestions.size() > 0) {
					activity.mTagsButton.setEnabled(true);
					Toast.makeText(activity, 
							Integer.toString(tagSuggestions.size()) + " " + activity.getText(R.string.tag_suggest_available), 
							Toast.LENGTH_SHORT)
						.show();
				}
				break;
			case MSG_TAG_SUGGEST:
				try {
					activity.dismissDialog(ID_SUGGEST_PROGRESS_DIALOG);
				} catch (IllegalArgumentException e) { } // Ignore, dialog might not have been shown before
				if (msg.obj != null) {
					tagSuggestions = (ArrayList<String>)msg.obj;
					activity.mTagSuggestions = tagSuggestions; 
				} else {
					tagSuggestions = activity.mTagSuggestions;
				}
				if (tagSuggestions != null && tagSuggestions.size() > 0) {
					activity.showDialog(ID_SUGGESTIONS_DIALOG);
				} else {
					activity.mTagsButton.setEnabled(false);
					Toast.makeText(activity, R.string.tags_suggest_empty, Toast.LENGTH_SHORT)
						.show();
				}
				break;
			case MSG_HTML_TITLE_SET:
				activity.mTitleEdit.setEnabled(true);
				activity.mTitleEdit.setText((String)msg.obj);
				break;
			}
    	}
    };
    
    private void fetchTagSuggestions (final String url, final int whatMessage) {
		// Fetch tag suggestions in a new thread
		Thread suggestThread = new Thread() {
			@Override
			public void run () {
				Message msg = sResultHandler.obtainMessage(whatMessage);
				msg.obj = mDeliciousClient.suggestTags(url);
				sResultHandler.sendMessage(msg);
			}
		};
		suggestThread.start();    	
    }
    
    private void showTagSuggestions() {
    	if (mTagSuggestions == null) {
    		fetchTagSuggestions(mUrlEdit.getText().toString(), MSG_TAG_SUGGEST);
    		showDialog(ID_SUGGEST_PROGRESS_DIALOG);
    	} else {
    		// Immediately send message to show tag suggestions dialog
    		sResultHandler.sendEmptyMessage(MSG_TAG_SUGGEST);
    	}
    }		
    
    private static final int ID_SUGGEST_PROGRESS_DIALOG = 1;
    private static final int ID_SUGGESTIONS_DIALOG = 2;

    @Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case ID_SUGGEST_PROGRESS_DIALOG:
			ProgressDialog suggestDlg = new ProgressDialog(this);
			suggestDlg.setMessage(getText(R.string.tags_suggest_message));
			return suggestDlg;
		case ID_SUGGESTIONS_DIALOG:
			return createTagSuggestionsDialog();
		default:
			return super.onCreateDialog(id);			
		}
	}

    private void postBookmark() {
        // Gather field values
    	final String url = mUrlEdit.getText().toString();
    	final String title = mTitleEdit.getText().toString();
    	final String comments = mCommentsEdit.getText().toString();
    	final String tags = mTagsEdit.getText().toString();
    	final boolean shared = !mPrivateCheck.isChecked();

    	// Submit request in background to service
        Bookmark bookmark = new Bookmark(url, title, comments, tags);
        BookmarkService.actionSubmitBookmark(this, bookmark, shared);
    	
        // Terminate activity with success result code
        setResult(Activity.RESULT_OK);
        finish();
    }
}