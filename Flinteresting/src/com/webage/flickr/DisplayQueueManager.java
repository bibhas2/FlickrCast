package com.webage.flickr;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;

import com.webage.util.Logger;

public class DisplayQueueManager extends TimerTask {
	public static final int MAX_SIZE = 10;
	private static final int DEFAULT_SHOW_INTERVAL = 5;
	BlockingQueue<Photo> queue = new ArrayBlockingQueue<Photo>(MAX_SIZE);
	ScheduledExecutorService timer = null;
	ScheduledFuture taskFuture = null;
	long interval = DEFAULT_SHOW_INTERVAL;
	MainActivity mainActivity;
	boolean cancelled = false;
	PhotoDAO dao;
	int countEmptyQueueReads = 0;

	public DisplayQueueManager(MainActivity a) {
		mainActivity = a;
		dao = new PhotoDAO(mainActivity);
	}

	public void run() {
		Logger.v("Display queue manager timer fired.");

		try {
			/*
			 * If display queue is empty, wait for a while. There is nothing
			 * else you can do.
			 */
			Photo p = queue.poll(getInterval(), TimeUnit.SECONDS);
			if (p == null) {
				Logger.v("Display queue is empty. Will try later.");
				if (!cancelled) {
					mainActivity.postWaitSign();
					++countEmptyQueueReads;
					if (countEmptyQueueReads >= 2) {
						mainActivity.postMessage("Unable to load images. Trying...");
						countEmptyQueueReads = 0;
					}
				}
				return;
			} else {
				countEmptyQueueReads = 0;
			}
			prepareAndShow(p);
		} catch (InterruptedException e) {
		}
	}

	private void prepareAndShow(Photo p) {
		Bitmap bm = loadPhoto(p);
		if (bm == null) {
			return;
		}
		if (!cancelled) {
			mainActivity.postDisplayRequest(p, bm);
		} else {
			mainActivity = null; //Last run
		}
	}

	private Bitmap loadPhoto(Photo p) {
		File cacheFile = dao.getCachedFile(p);
		
		return decodeFile(cacheFile);
	}

	private Bitmap decodeFile(File f) {
		try {
			/*
			 * // decode image size BitmapFactory.Options o = new
			 * BitmapFactory.Options(); o.inJustDecodeBounds = true;
			 * BitmapFactory.decodeStream(new FileInputStream(f), null, o);
			 * 
			 * // Find the correct scale value. It should be the power of 2.
			 * final int REQUIRED_SIZE = 70; int width_tmp = o.outWidth,
			 * height_tmp = o.outHeight; int scale = 1; while (true) { if
			 * (width_tmp / 2 < REQUIRED_SIZE || height_tmp / 2 < REQUIRED_SIZE)
			 * break; width_tmp /= 2; height_tmp /= 2; scale *= 2; }
			 */
			Logger.v("Loading photo from cache: " + f.getName());
			// decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			// o2.inSampleSize = scale;
			InputStream is = new FileInputStream(f);
			Bitmap bm = BitmapFactory.decodeStream(is, null, o2);
			is.close();

			return bm;
		} catch (Exception e) {
			Logger.v("Failed to load image", e);
		}
		return null;
	}

	public void addToQueue(Photo p) {
		Logger.v("Adding to display queue: " + p.getId());
		if (queue == null) {
			return;
		}
		try {
			queue.put(p);
		} catch (InterruptedException e) {
			Logger.v("Failed to put image in queue", e);
		}
	}

	public void execute() {
		Logger.v("DisplayQueueManager strating up.");
		timer = Executors.newSingleThreadScheduledExecutor();
		taskFuture = timer.scheduleWithFixedDelay(this, getInterval(), getInterval(), TimeUnit.SECONDS);
	}

	public void shutdown() {
		Logger.v("DisplayQueueManager ending work.");

		timer.shutdown();
		/*
		 * This can free up any blocking
		 * put() calls.
		 */
		queue.clear(); 
		queue = null;
		cancelled = true;
		mainActivity = null; //Good for GC.
	}

	public long getInterval() {
		return dao.getPrefAsInt("show_speed", DEFAULT_SHOW_INTERVAL);
	}

	public void pause() {
		Logger.v("DisplayQueueManager pausing");
		if (taskFuture != null) {
			taskFuture.cancel(false);
			taskFuture = null;
		}
	}
	
	public void resume() {
		Logger.v("DisplayQueueManager resuming");
		taskFuture = timer.scheduleWithFixedDelay(this, 0, getInterval(), TimeUnit.SECONDS);
	}

	class ImmediateProcessor extends AsyncTask<Void, Void, Void> {
		private Photo photo;
		
		ImmediateProcessor(Photo p) {
			this.photo = p;
		}
		protected Void doInBackground(Void... params) {
			Logger.v("Showing immediate: " + photo.getId());
			prepareAndShow(photo);
			return null;
		}
	}
	
	/*
	 * Can be called from main thread. Loads photo in the timer's thread
	 * and shows the photo in ImageView from main thread.
	 */
	public void showImmediate(Photo p) {
		new ImmediateProcessor(p).execute();
	}
}
