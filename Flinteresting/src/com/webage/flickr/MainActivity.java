package com.webage.flickr;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.webage.util.Logger;

public class MainActivity extends Activity {
	RequestQueueManager reqMgr;
	Photo currentPhoto = null;
	Bitmap currentBitmap;
	ImageView imageView;
	TextView titleText;
	ProgressBar progressBar;
	View menuBar;
	boolean wasPaused = false;
	boolean menuShown = true;
	ExecutorService threadPool;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Logger.v("MainActivity onCreate called: "
				+ (savedInstanceState != null ? " Has bundle" : "Null bundle"));
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		imageView = (ImageView) findViewById(R.id.imageView);
		titleText = (TextView) findViewById(R.id.titleText);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		menuBar = findViewById(R.id.menuBar);

		imageView.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				manageMenu();
			}
		});

		ImageButton b = (ImageButton) findViewById(R.id.previousImage);
		b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showPreviousImage();
			}
		});
		b = (ImageButton) findViewById(R.id.nextImage);
		b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showNextImage();
			}
		});
		b = (ImageButton) findViewById(R.id.saveImage);
		b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				saveImage();
			}
		});
		b = (ImageButton) findViewById(R.id.shareImage);
		b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				shareImage();
			}
		});
		b = (ImageButton) findViewById(R.id.settingsButton);
		b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				openSettings();
			}
		});

		reqMgr = new RequestQueueManager(this);
		// Start the queue managers
		reqMgr.execute();

		threadPool = Executors.newCachedThreadPool();

		// Hide menu after 1s.
		Handler h = new Handler();
		h.postDelayed(new Runnable() {
			public void run() {
				manageMenu();
			}
		}, 1000);
	}

	protected void openSettings() {
		Intent i = new Intent(this, SettingsEditorActivity.class);

		startActivity(i);
	}

	protected void shareImage() {
		Intent intent = new Intent(Intent.ACTION_SEND);
		PhotoDAO dao = new PhotoDAO(this);
		intent.setType("image/jpeg");
		File o = dao.getCachedFile(currentPhoto);
		intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(o));
		startActivity(Intent.createChooser(intent, "Share Image"));
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		manageMenu();
		return false;
	}

	protected void saveImage() {
		reqMgr.savePhoto(currentPhoto);
	}

	protected void showPreviousImage() {
		threadPool.execute(new Runnable() {
			public void run() {
				try {
					Photo p = reqMgr.getPreviousPhoto(currentPhoto);
					reqMgr.processPhoto(p);
					reqMgr.getDisplayManager().prepareAndShow(p);
				} catch (Exception e) {
					Logger.v("Error showing previous image", e);
					postMessage("Failed to download image");
				}
			}
		});
	}

	protected void showNextImage() {
		threadPool.execute(new Runnable() {
			public void run() {
				try {
				    Photo p = reqMgr.getNextPhoto(currentPhoto);
				    if (reqMgr.getDisplayManager().isInQueue(p)) {
				    	reqMgr.getDisplayManager().showNextInQueue();
				    } else {
						reqMgr.processPhoto(p);
						reqMgr.getDisplayManager().prepareAndShow(p);
				    }
				} catch (Exception e) {
					Logger.v("Error showing previous image", e);
					postMessage("Failed to download image");
				}
			}
		});
	}

	private void manageMenu() {
		TranslateAnimation slide = null;

		if (menuShown == false) {
			pause();
			// Show the menu
			slide = new TranslateAnimation(0, 0, menuBar.getHeight(), 0);
			menuShown = true;
			titleText.setVisibility(View.GONE);
		} else {
			// Hide menu
			slide = new TranslateAnimation(0, 0, 0, menuBar.getHeight());
			menuShown = false;
			titleText.setVisibility(View.VISIBLE);
			resume();
		}
		slide.setDuration(300);
		slide.setFillAfter(true);

		menuBar.startAnimation(slide);
	}

	@Override
	protected void onPause() {
		Logger.v("MainActivity onPause");
		super.onPause();
		pause();
	}

	private void pause() {
		if (wasPaused) {
			return;
		}
		reqMgr.pause();
		wasPaused = true;
	}

	protected void resume() {
		if (menuShown) {
			return;
		}
		if (wasPaused) {
			reqMgr.resume();
			wasPaused = false;
		}
	}

	@Override
	protected void onResume() {
		Logger.v("MainActivity onResume");
		super.onResume();
		resume();
	}

	/*
	 * Called from another thread.
	 */
	public void postDisplayRequest(Photo p, Bitmap bm) {
		Logger.v("Received request to display photo: " + p.getId());

		currentPhoto = p;
		currentBitmap = bm;

		runOnUiThread(new Runnable() {
			public void run() {
				displayPhoto();
			}
		});
	}

	private void displayPhoto() {
		if (reqMgr == null) {
			// This activity has been destroyed already
			return;
		}
		progressBar.setVisibility(View.GONE);
		imageView.setImageBitmap(currentBitmap);
		titleText.setText(currentPhoto.getTitle());
	}

	@Override
	protected void onDestroy() {
		Logger.v("MainActivity onDestroy");
		threadPool.shutdown();
		reqMgr.shutdown();
		reqMgr = null;
		super.onDestroy();
	}

	public void postWaitSign() {
		runOnUiThread(new Runnable() {
			public void run() {
				if (reqMgr == null) {
					// This activity has been destroyed already
					return;
				}
				progressBar.setVisibility(View.VISIBLE);
			}
		});
	}

	public void postMessage(final String msg) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (reqMgr != null) {
					// This activity has not been destroyed
					Toast toast = Toast.makeText(MainActivity.this, msg,
							Toast.LENGTH_SHORT);
					toast.setGravity(Gravity.CENTER_HORIZONTAL
							| Gravity.CENTER_VERTICAL, 0, 0);
					toast.show();
				}
			}
		});
	}

}