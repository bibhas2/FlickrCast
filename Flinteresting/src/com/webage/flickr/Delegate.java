package com.webage.flickr;

public interface Delegate<E> {
	public void onLoadStart();
	public void onError(Exception e);
	public void onLoad(E data);
	public void onLoadEnd();
}
