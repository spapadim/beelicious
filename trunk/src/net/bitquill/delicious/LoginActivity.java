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
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Login screen activity.
 */
public class LoginActivity extends Activity {

	private EditText mUsernameEdit;
	private EditText mPasswordEdit;
		
	private SharedPreferences mSettings;
	private DeliciousClient mDeliciousClient;
	
	// Saved username and password when this dialog was called
	private String mSavedUsername;
	private String mSavedPassword;

	public static void actionLogin (Activity activity, int requestCode) {
		Intent i = new Intent(activity, LoginActivity.class);
		//i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		activity.startActivityForResult(i, requestCode);
	}
	
	@Override
	protected void onDestroy() {
		sValidateResultHandler.setActivity(null);
		super.onDestroy();
	}

	@Override
	public void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);
		
		sValidateResultHandler.setActivity(this);

		mDeliciousClient = DeliciousApp.getInstance().mDeliciousClient;
		mSettings = getSharedPreferences(DeliciousApp.PREFS_NAME, MODE_PRIVATE);
		if (savedInstanceState != null) {
			mSavedUsername = savedInstanceState.getString(SAVED_USERNAME);
			mSavedPassword = savedInstanceState.getString(SAVED_PASSWORD);
		} else {
			mSavedUsername = mSettings.getString("delicious_username", null);
			mSavedPassword = mSettings.getString("delicious_password", null);
		}
	
		mUsernameEdit = (EditText)findViewById(R.id.usernameEdit);
		mPasswordEdit = (EditText)findViewById(R.id.passwordEdit);
		if (mSavedUsername != null) {
			mUsernameEdit.setText(mSavedUsername);
		}
		if (mSavedPassword != null) {
			mPasswordEdit.setText(mSavedPassword);
		}
		
		Button okButton = (Button)findViewById(R.id.okButton);
		okButton.setOnClickListener(mOnClickOk);
		Button cancelButton = (Button)findViewById(R.id.cancelButton);
		cancelButton.setOnClickListener(mOnClickCancel);
	}

	private final OnClickListener mOnClickOk = new OnClickListener() {
		@Override
		public void onClick(View v) {
			// Temporarily set new credentials on Delicious client
			String username = mUsernameEdit.getText().toString();
			String password = mPasswordEdit.getText().toString();
			mDeliciousClient.setCredentials(username, password);
			
			showDialog(ID_PROGRESS_DIALOG);
			
			// Fire off validation in separate thread
			new Thread(mValidateRunnable).start();
			// Rest of logic in message handler object			
		}
	};
	
	// Assign a "random" unique id
	private static final int MSG_VALIDATE_RESULT = R.id.usernameEdit;
	
	private static final ActivityHandler<LoginActivity> sValidateResultHandler = 
		new ActivityHandler<LoginActivity>() {
		@Override
		public void handleMessage(Message msg) {
			LoginActivity activity = (LoginActivity)getActivity();
			if (activity == null) {
				return;
			}
			switch (msg.what) {
			case MSG_VALIDATE_RESULT:
				activity.dismissDialog(ID_PROGRESS_DIALOG);
				DeliciousClient deliciousClient = activity.mDeliciousClient;
				
				boolean valid = (msg.arg1 != 0);
				if (valid) {
					// Save new credentials in shared preferences
					Editor editor = activity.mSettings.edit();
					editor.putString("delicious_username", deliciousClient.getUsername());
					editor.putString("delicious_password", deliciousClient.getPassword());
					editor.commit();
					// Notify user that the change was made
					//Toast.makeText(LoginScreen.this, R.string.msg_credentials_valid, Toast.LENGTH_SHORT)
					//	 .show();
					// Finish activity with success result
					activity.setResult(Activity.RESULT_OK);
					activity.finish();
				} else {
					// Restore old credentials; change should be made permanent only when user enters valid credentials
					deliciousClient.setCredentials(activity.mSavedUsername, activity.mSavedPassword);
					// Notify user that credentials were invalid
					Toast.makeText(activity, R.string.msg_credentials_invalid, Toast.LENGTH_LONG)
						 .show();
					// Activity should not terminate until user either enters valid credentials, or clicks Cancel
				}

				break;
			}
		}
	};
	
	private static final String SAVED_USERNAME = "saved_username";
	private static final String SAVED_PASSWORD = "saved_password";
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(SAVED_PASSWORD, mSavedPassword);
		outState.putString(SAVED_USERNAME, mSavedUsername);
	}

	private static final int ID_PROGRESS_DIALOG = 1;
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case ID_PROGRESS_DIALOG:
			ProgressDialog dlg = new ProgressDialog(this);
			dlg.setMessage(getText(R.string.login_validate_message));
			return dlg;
		default:
			return super.onCreateDialog(id);
		}
	}

	private final Runnable mValidateRunnable = new Runnable() {
		@Override
		public void run() {
			boolean valid = mDeliciousClient.validateCredentials();
			Message msg = sValidateResultHandler.obtainMessage(MSG_VALIDATE_RESULT);
			msg.arg1 = valid ? 1 : 0;
			sValidateResultHandler.sendMessage(msg);
		}
	};
	
	private final OnClickListener mOnClickCancel = new OnClickListener() {
		@Override
		public void onClick(View v) {
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
	};

}
