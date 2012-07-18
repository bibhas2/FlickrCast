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

import com.webage.flickrcast.R;
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
		timer = Executors.newSingleThreadScheduledExecutor();
	}

	public void run() {
		Logger.v("Display queue manager timer fired.");
		showNextInQueue();
	}

	public void showNextInQueue() {
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

	public boolean isInQueue(Photo p) {
		return queue.contains(p);
	}
	
	public void prepareAndShow(Photo p) {
		Logger.v("Preparing for display: " + p.getId());
		Bitmap bm = dao.loadImageFromCache(p);
		if (bm == null) {
			Logger.v("Failed to load from cache: " + p.getId());
		}
		if (!cancelled) {
			Logger.v("Posting display request: " + p.getId());
			mainActivity.postDisplayRequest(p, bm);
		} else {
			Logger.v("Not posting display request: " + p.getId());
			mainActivity = null; //Last run
		}
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
		Logger.v("Added to display queue");
	}

	public void startTimer() {
		Logger.v("DisplayQueueManager starting timer.");
		taskFuture = timer.schedule(this, getInterval(), TimeUnit.SECONDS);
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

	public void stopTimer() {
		Logger.v("DisplayQueueManager stopping timer");
		if (taskFuture != null) {
			taskFuture.cancel(false);
			taskFuture = null;
		}
	}
}
