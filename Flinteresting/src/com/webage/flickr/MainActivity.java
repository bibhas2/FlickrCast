package com.webage.flickr;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.webage.flickrcast.R;
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

	private static final int SWIPE_MIN_DISTANCE = 70;
	private static final int SWIPE_MAX_OFF_PATH = 250;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;
	GestureDetector gestureDetector;
	    
    class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
                    return false;
                // right to left swipe
                if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    onLeftFling();
                    return true;
                }  else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                	onRightFling();
                	return true;
                }
            } catch (Exception e) {
                // nothing
            }
            return false;
        }
    }
    
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

		//Setup gesture handler
		gestureDetector = new GestureDetector(new MyGestureDetector());
		imageView.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return gestureDetector.onTouchEvent(event);
			}
		});
		
		// Hide menu after 1s.
		Handler h = new Handler();
		h.postDelayed(new Runnable() {
			public void run() {
				manageMenu();
			}
		}, 1000);
	}

	public void onRightFling() {
		Logger.v("****Right fling");
		reqMgr.getDisplayManager().stopTimer();
		showPreviousImage();
	}

	public void onLeftFling() {
		Logger.v("****Left fling");
		reqMgr.getDisplayManager().stopTimer();
		showNextImage();
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
		
		if (bm == null) {
			//Cache is bad!
			Logger.v("Could not load from cache. Skipping: " + p.getId());
			if (menuShown == false) {
				reqMgr.getDisplayManager().startTimer();
			}
		}

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
		Logger.v("Displayed photo: " + currentPhoto.getId());
		if (menuShown == false) {
			reqMgr.getDisplayManager().startTimer();
		}
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