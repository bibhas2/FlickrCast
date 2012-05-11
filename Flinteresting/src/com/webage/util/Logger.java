package com.webage.util;

import android.util.Log;

public class Logger {
	static String TAG = "Flinteresting";
	
	public static void v(String str) {
		if (Log.isLoggable(TAG, Log.VERBOSE)) {
			Log.v(TAG, str);
		}
	}
	public static void v(String str, Throwable t) {
		if (Log.isLoggable(TAG, Log.VERBOSE)) {
			Log.v(TAG, str, t);
		}
	}
	public static void i(String str) {
		Log.i(TAG, str);
	}
}
