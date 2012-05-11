package com.webage.flickr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import com.webage.util.Logger;

public class RequestQueueManager extends AsyncTask<Void, Void, Void> {
	public static final int MAX_SIZE = 10;
	private static final int DEFAULT_CACHE_DURATION = 1;
	//This queue is unbounded
	BlockingQueue<Photo> queue = new LinkedBlockingQueue<Photo>();
	MainActivity mainActivity;
	DisplayQueueManager displayMgr;
	PhotoDAO dao;

	public RequestQueueManager(MainActivity a) {
		mainActivity = a;
		displayMgr = new DisplayQueueManager(a);
		dao = new PhotoDAO(mainActivity);
	}

	protected Void doInBackground(Void... params) {
		Logger.v("RequestQueueManager starting up.");
		//Start the display queue manager
		displayMgr.execute();
		//Purge cache
		purgeCache();
		
		while (isCancelled() == false) {
			Photo p = null;

			try {
				p = queue.poll(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
			}
			if (p == null) {
				Logger.v("No photo in request queue.");
				continue;
			}
			if (isCancelled()) {
				continue;
			}

			try {
				processPhoto(p);
				queueForDisplay(p);
			} catch (Exception e) {
				Logger.v("Failed to download image", e);
				removeCache(p);
			}
		}
		Logger.v("RequestQueueManager ending work.");
		mainActivity = null;
		
		return null;
	}

	public void shutdown() {
		displayMgr.shutdown();
		this.cancel(false);
		mainActivity = null;
	}
	/*
	 * Delete files older than 7 days.
	 */
	private void purgeCache() {
		Logger.v("Purging cache");
		int maxAgeDays = dao.getPrefAsInt("cache_duration", DEFAULT_CACHE_DURATION);
		File cacheDir = dao.getCachedDir();
		File[] listFiles = cacheDir.listFiles(); 
		long purgeTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000);
		for(File f : listFiles) {
			if(f.lastModified() < purgeTime) {
				if(f.delete()) {
					Logger.v("Purged cache file: " + f.getName());
				} else {
					Logger.v("Unable to purge cache file: " + f.getName());
				}
			}
		}
	}

	private void removeCache(Photo p) {
		File cacheFile = dao.getCachedFile(p);

		if (cacheFile.exists()) {
			Logger.v("Deleting cache file");
			cacheFile.delete();
		}
	}

	/*
	 * This method can block if display queue is full.
	 * This will throttle how many images are fetched from
	 * the Internet in rapid succession.
	 */
	private void queueForDisplay(Photo p) {
		//This can block
		displayMgr.addToQueue(p);
	}

	public void copyStream(InputStream is, OutputStream os) throws Exception {
		final int buffer_size = 512;
		byte[] bytes = new byte[buffer_size];
		for (;;) {
			if (isCancelled()) {
				throw new Exception("Interrupting file copy");
			}
			int count = is.read(bytes, 0, buffer_size);
			if (count == -1)
				break;
			os.write(bytes, 0, count);
		}
	}

	public void savePhoto(Photo p) {
		new SaveProcessor(p).execute();
	}
	
	class SaveProcessor extends AsyncTask<Void, Void, Void> {
		private Photo photo;
		
		SaveProcessor(Photo p) {
			this.photo = p;
		}
		protected Void doInBackground(Void... params) {
			File targetDir;
			
			targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
			//See if directory exists
			if (targetDir.exists() == false) {
				targetDir.mkdir();
			}
			File outFile = new File(targetDir, photo.getId() + ".jpg");
			Logger.v("Saving photo: " + outFile.getAbsolutePath());
			
			try {
				OutputStream os = new FileOutputStream(outFile);
				InputStream is = new FileInputStream(dao.getCachedFile(photo));
				
				copyStream(is, os);
				is.close();
				os.close();
				
            	Logger.v("Media scan started");
				MediaScannerConnection.scanFile(mainActivity,
			                new String[] { outFile.toString() }, null,
			                new MediaScannerConnection.OnScanCompletedListener() {
			            public void onScanCompleted(String path, Uri uri) {
			            	Logger.v("Media scan completed");
			            }
			     });
				mainActivity.postMessage("Photo was saved successfully");
			} catch (Exception e) {
				Logger.v("Photo save failed", e);
				mainActivity.postMessage("Error: Could not save photo on SD card.");
			}
			return null;
		}
	}
	
	private void processPhoto(Photo p) throws Exception {
		Logger.v("Processing photo from request queue: " + p.getId());
		
		if (isCancelled()) {
			return;
		}

		// Save the image in cache
		File cacheFile = dao.getCachedFile(p);

		if (cacheFile.exists()) {
			// This photo is already downloaded
			Logger.v("Got a cache hit");
			return;
		}

		OutputStream os = new FileOutputStream(cacheFile);
		try {
			Logger.v("Downloading from: " + p.getUrl());
			URL imageUrl = new URL(p.getUrl());
			HttpURLConnection conn = (HttpURLConnection) imageUrl
					.openConnection();
			conn.setConnectTimeout(30000);
			conn.setReadTimeout(30000);
			InputStream is = conn.getInputStream();
			Logger.v("Caching photo: " + cacheFile.getAbsolutePath());
			copyStream(is, os);
			is.close();
		} finally {
			os.close();
		}
	}

	public void addToQueue(Photo photo) {
		Logger.v("Adding to request queue: " + photo.getId());
		queue.add(photo);
	}
	
	public boolean isEmpty() {
		return queue.isEmpty();
	}
	
	public void pause() {
		/*
		 * Just pause the display queue manager. This will eventually fill up
		 * the display queue and this thread will be blocked.
		 */
		displayMgr.pause();
	}
	public void resume() {
		displayMgr.resume();
	}

	public DisplayQueueManager getDisplayManager() {
		return displayMgr;
	}
}
