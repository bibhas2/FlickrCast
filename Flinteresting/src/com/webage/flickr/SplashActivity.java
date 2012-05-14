package com.webage.flickr;

import java.util.ArrayList;
import java.util.logging.Logger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Toast;

public class SplashActivity extends Activity {
	Logger logger = Logger.getLogger("Flinteresting");

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.splash);

		PhotoDAO dao = new PhotoDAO(this);
		dao.fetchInterestingList(new Delegate<ArrayList<Photo>>() {
			public void onLoadStart() {

			}

			public void onLoadEnd() {

			}

			public void onLoad(ArrayList<Photo> data) {
				MainActivity.setList(data);
				Intent i = new Intent(SplashActivity.this, MainActivity.class);
				startActivity(i);
			}

			public void onError(Exception e) {
				showMessage("Failed to connect to Flickr");
				finish();
			}
		});
	}

	public void showMessage(String msg) {
		// This activity has not been destroyed
		Toast toast = Toast
				.makeText(this, msg, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL,
				0, 0);
		toast.show();
	}

}