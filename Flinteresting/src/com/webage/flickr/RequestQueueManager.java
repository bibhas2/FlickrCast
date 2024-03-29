package com.webage.flickr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import com.webage.flickrcast.R;
import com.webage.util.Logger;

public class RequestQueueManager extends AsyncTask<Void, Void, Void> {
	public static final int MAX_SIZE = 10;
	private static final int DEFAULT_CACHE_DURATION = 1;
	public static final int MAX_CYCLES = 1;
	
	public static final int CYCLE_END = 1;
	public static final int CYCLE_ERROR = 2;
	public static final int CYCLE_CANCEL = 3;
	
	//This queue is unbounded
	MainActivity mainActivity;
	DisplayQueueManager displayMgr;
	PhotoDAO dao;
	int currentIndex = 0;
	int numCycles = 0;
	ArrayList<Photo> photoList = null;

	public RequestQueueManager(MainActivity a) {
		mainActivity = a;
		displayMgr = new DisplayQueueManager(a);
		dao = new PhotoDAO(mainActivity);
	}

	protected Void doInBackground(Void... params) {
		Logger.v("RequestQueueManager starting up.");
		//Start the display queue manager
		displayMgr.startTimer();
		
		int endStatus = 0;
		do {
			Logger.v("****Starting cycle.");
			endStatus = startCycle();
		} while (endStatus == CYCLE_END);
		
		Logger.v("RequestQueueManager ending work. End status: " + endStatus);
		mainActivity = null;
		
		return null;
	}

	private int startCycle() {
		//Get master list
		photoList = dao.fetchInterestingList();
		if (photoList == null) {
			mainActivity.postMessage("Failed to connect to Flickr");
			return CYCLE_ERROR;
		}
		//Purge cache
		purgeCache();
		
		while (isCancelled() == false) {
			Photo p = photoList.get(currentIndex);

			try {
				processPhoto(p);
				queueForDisplay(p);
			} catch (Exception e) {
				Logger.v("Failed to download image", e);
				removeCache(p);
			}
			++currentIndex;
			if (currentIndex == photoList.size()) {
				currentIndex = 0;
				++numCycles;
				if (numCycles == MAX_CYCLES) {
					return CYCLE_END;
				}
			}
		}
		return CYCLE_CANCEL;
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

	/**
	 * Called from main thread.
	 * 
	 * @param p
	 */
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
	
	public void processPhoto(Photo p) throws Exception {
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

	
	public void pause() {
		/*
		 * Just pause the display queue manager. This will eventually fill up
		 * the display queue and this thread will be blocked.
		 */
		displayMgr.stopTimer();
	}
	public void resume() {
		displayMgr.startTimer();
	}

	public DisplayQueueManager getDisplayManager() {
		return displayMgr;
	}

	public Photo getPreviousPhoto(Photo from) {
	    for (int i = 0; i < photoList.size(); ++i) {
	        Photo p = photoList.get(i);
	        if (p == from) {
	            //Found it!
	            int resultIdx = i - 1;
	            if (resultIdx < 0) {
	                resultIdx = photoList.size() - 1; //wrap to end
	            }
	            return photoList.get(resultIdx);
	        }
	    }
	    return null;
	}
	/*
	 * Returns next photo.
	 */
	public Photo getNextPhoto(Photo from) {
	    for (int i = 0; i < photoList.size(); ++i) {
	    	Photo p = photoList.get(i);
	        if (p == from) {
	            //Found it!
	            int resultIdx = i + 1;
	            if (resultIdx == photoList.size()) {
	                resultIdx = 0; //wrap to end
	            }
	            return photoList.get(resultIdx);
	        }
	    }
	    return null;
	}
}
