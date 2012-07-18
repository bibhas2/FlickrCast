package com.webage.flickr;

import com.webage.flickrcast.R;

public interface Delegate<E> {
	public void onLoadStart();
	public void onError(Exception e);
	public void onLoad(E data);
	public void onLoadEnd();
}
