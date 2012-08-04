/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sip;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.net.sip.*;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.text.ParseException;
import java.util.ArrayList;

/**
 * Handles all calling, receiving calls, and UI interaction in the WalkieTalkie
 * app.
 */
public class WalkieTalkieActivity extends Activity implements
		View.OnTouchListener {

	public String sipAddress = null;
	private ArrayList<String> addList = new ArrayList<String>();
	public SipManager manager = null;
	public SipProfile me = null;
	public SipAudioCall call = null;
	public IncomingCallReceiver callReceiver;

	private static final int CALL_ADDRESS = 5;
	private static final int ADDRESS_LIST = 1;
	private static final int SET_AUTH_INFO = 2;
	private static final int UPDATE_SETTINGS_DIALOG = 3;
	private static final int HANG_UP = 4;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.walkietalkie);

		Log.v("OnCreate ", "OnCreate ");
		ToggleButton pushToTalkButton = (ToggleButton) findViewById(R.id.pushToTalk);
		pushToTalkButton.setOnTouchListener(this);

		// Set up the intent filter. This will be used to fire an
		// IncomingCallReceiver when someone calls the SIP address used by this
		// application.
		IntentFilter filter = new IntentFilter();
		filter.addAction("android.SipDemo.INCOMING_CALL");
		callReceiver = new IncomingCallReceiver();
		this.registerReceiver(callReceiver, filter);

		// "Push to talk" can be a serious pain when the screen keeps turning
		// off.
		// Let's prevent that.
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		Log.v("call Initializemanager ", "call Initializemanager ");
		initializeManager();
	}

	@Override
	public void onStart() {
		super.onStart();
		// When we get back from the preference setting Activity, assume
		// settings have changed, and re-login with new auth info.
		initializeManager();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (call != null) {
			call.close();
		}

		closeLocalProfile();

		if (callReceiver != null) {
			this.unregisterReceiver(callReceiver);
		}
	}

	public void initializeManager() {

		Log.v("Method call Initializemanager ",
				"Method call Initializemanager ");
		if (manager == null) {
			manager = SipManager.newInstance(this);
		}
		Log.v("call initializeLocalProfile", "call initializeLocalProfile");
		initializeLocalProfile();
	}

	/**
	 * Logs you into your SIP provider, registering this device as the location
	 * to send SIP calls to for your SIP address.
	 */
	public void initializeLocalProfile() {
		Log.v("Method call initializeLocalProfile",
				"Method call initializeLocalProfile");
		Log.v("manager", manager + "  get velus");
		if (manager == null) {
			return;
		}
		Log.v("me", me + "  get velus");
		if (me != null) {

			Log.v("call closeLocalProfile", "call closeLocalProfile");
			closeLocalProfile();
		}

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());
		String username = prefs.getString("namePref", "");
		String domain = prefs.getString("domainPref", "");
		String password = prefs.getString("passPref", "");

		Log.v("username", username);
		Log.v("domain", domain);
		Log.v("password", password);

		if (username.length() == 0 || domain.length() == 0
				|| password.length() == 0) {
			showDialog(UPDATE_SETTINGS_DIALOG);
			return;
		}

		try {
			Log.v("SipProfile.Builder", "SipProfile.Builder");

			SipProfile.Builder builder = new SipProfile.Builder(username,
					domain);
			builder.setPassword(password);
			me = builder.build();
			Log.v("me", me + "  get velus");

			Intent i = new Intent();
			i.setAction("android.SipDemo.INCOMING_CALL");
			PendingIntent pi = PendingIntent.getBroadcast(this, 0, i,
					Intent.FILL_IN_DATA);
			manager.open(me, pi, null);
			Log.v("intent call ", "intent call");

			// This listener must be added AFTER manager.open is called,
			// Otherwise the methods aren't guaranteed to fire.

			manager.setRegistrationListener(me.getUriString(),
					new SipRegistrationListener() {
						public void onRegistering(String localProfileUri) {
							updateStatus("Registering with SIP Server...");
						}

						public void onRegistrationDone(String localProfileUri,
								long expiryTime) {
							updateStatus("Ready");
						}

						public void onRegistrationFailed(
								String localProfileUri, int errorCode,
								String errorMessage) {
							updateStatus("Registration failed.  Please check settings.");
						}
					});
		} catch (ParseException pe) {
			updateStatus("Connection Error.");
		} catch (SipException se) {
			updateStatus("Connection error.");
		}
	}

	/**
	 * Closes out your local profile, freeing associated objects into memory and
	 * unregistering your device from the server.
	 */
	public void closeLocalProfile() {
		Log.v("closeLocalProfile manager", manager + "  closeLocalProfile");
		if (manager == null) {
			return;
		}
		try {
			Log.v("closeLocalProfile me", me + "  get velus");

			if (me != null) {
				Log.v("close me URI ", me.getUriString() + "  get velus");
				manager.close(me.getUriString());
			}
		} catch (Exception ee) {
			Log.d("WalkieTalkieActivity/onDestroy",
					"Failed to close local profile.", ee);
		}
	}

	/**
	 * Make an outgoing call.
	 */
	public void initiateCall() {

		updateStatus(sipAddress);

		try {
			SipAudioCall.Listener listener = new SipAudioCall.Listener() {
				// Much of the client's interaction with the SIP Stack will
				// happen via listeners. Even making an outgoing call, don't
				// forget to set up a listener to set things up once the call is
				// established.
				@Override
				public void onCallEstablished(SipAudioCall call) {
					call.startAudio();
					call.setSpeakerMode(true);
					// if (call.isMuted()) {
					// call.toggleMute();
					// }
					// call.toggleMute();
					updateStatus(call, 2);
				}

				@Override
				public void onCallEnded(SipAudioCall call) {
					updateStatus("Call End");
				}
			};

			call = manager.makeAudioCall(me.getUriString(), sipAddress,
					listener, 30);

		} catch (Exception e) {
			Log.i("WalkieTalkieActivity/InitiateCall",
					"Error when trying to close manager.", e);
			if (me != null) {
				try {
					manager.close(me.getUriString());
				} catch (Exception ee) {
					Log.i("WalkieTalkieActivity/InitiateCall",
							"Error when trying to close manager.", ee);
					ee.printStackTrace();
				}
			}
			if (call != null) {
				call.close();
			}
		}
	}

	/**
	 * Updates the status box at the top of the UI with a messege of your
	 * choice.
	 * 
	 * @param status
	 *            The String to display in the status box.
	 */
	public void updateStatus(final String status) {
		// Be a good citizen. Make sure UI changes fire on the UI thread.
		this.runOnUiThread(new Runnable() {
			public void run() {
				TextView labelView = (TextView) findViewById(R.id.sipLabel);
				labelView.setText(status);
			}
		});
	}

	/**
	 * Updates the status box with the SIP address of the current call.
	 * 
	 * @param call
	 *            The current, active call.
	 */
	public void updateStatus(SipAudioCall call, int val) {
		String useName = call.getPeerProfile().getDisplayName();
		if (useName == null) {
			useName = call.getPeerProfile().getUserName();
		}
		if (val == 1) {
			updateStatus("Recieve call :- " + useName + "@"
					+ call.getPeerProfile().getSipDomain());
		} else {
			updateStatus("Dial call :- " + useName + "@"
					+ call.getPeerProfile().getSipDomain());
		}

	}

	/**
	 * Updates whether or not the user's voice is muted, depending on whether
	 * the button is pressed.
	 * 
	 * @param v
	 *            The View where the touch event is being fired.
	 * @param event
	 *            The motion to act on.
	 * @return boolean Returns false to indicate that the parent view should
	 *         handle the touch event as it normally would.
	 */
	public boolean onTouch(View v, MotionEvent event) {
		if (call == null) {
			return false;
		} else if (event.getAction() == MotionEvent.ACTION_DOWN && call != null
				&& call.isMuted()) {
			call.toggleMute();
		} else if (event.getAction() == MotionEvent.ACTION_UP
				&& !call.isMuted()) {
			call.toggleMute();
		}
		return false;
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, ADDRESS_LIST, 0, "Call someone");
		menu.add(0, SET_AUTH_INFO, 0, "Edit your SIP Info.");
		menu.add(0, HANG_UP, 0, "End Current Call.");

		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case ADDRESS_LIST:

			showDialog(ADDRESS_LIST);
			break;
		case SET_AUTH_INFO:
			updatePreferences();
			break;
		case HANG_UP:
			Log.v("End call", "true");
			if (call != null) {
				Log.v("End call not null", "true");
				try {
					call.endCall();
					updateStatus("End Call");
				} catch (SipException se) {
					Log.d("WalkieTalkieActivity/onOptionsItemSelected",
							"Error ending call.", se);
				}
				call.close();
			}
			break;
		}
		return true;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case ADDRESS_LIST:
			
			final Dialog dialog = new Dialog(WalkieTalkieActivity.this);

			dialog.setContentView(R.layout.address_list);
			dialog.setTitle("Address List");

			ListView add_list = (ListView) dialog.findViewById(R.id.add_list);
			Button new_add = (Button) dialog.findViewById(R.id.btn_new_add);

			ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
					android.R.layout.simple_list_item_1, android.R.id.text1,
					addList);
			add_list.setAdapter(adapter);
			
			add_list.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1,
						int arg2, long arg3) {
					// TODO Auto-generated method stub
					sipAddress = addList.get(arg2);
					initiateCall();
					dialog.dismiss();
				}
			});
			

			new_add.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					showDialog(CALL_ADDRESS);
					dialog.dismiss();
				}
			});
			dialog.show();
			break;

		case CALL_ADDRESS:

			LayoutInflater factory = LayoutInflater.from(this);
			final View textBoxView = factory.inflate(
					R.layout.call_address_dialog, null);
			return new AlertDialog.Builder(this)
					.setTitle("Call Someone.")
					.setView(textBoxView)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									EditText textField = (EditText) (textBoxView
											.findViewById(R.id.calladdress_edit));
									addList.add(textField.getText().toString());

									sipAddress = textField.getText().toString();
									initiateCall();

								}
							})
					.setNegativeButton(android.R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									// Noop.
								}
							}).create();
			

		case UPDATE_SETTINGS_DIALOG:
			return new AlertDialog.Builder(this)
					.setMessage("Please update your SIP Account Settings.")
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									updatePreferences();
								}
							})
					.setNegativeButton(android.R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									// Noop.
								}
							}).create();
		}
		return null;
	}

	public void updatePreferences() {
		Intent settingsActivity = new Intent(getBaseContext(),
				SipSettings.class);
		startActivity(settingsActivity);
	}
}
