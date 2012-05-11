package com.webage.util;

import org.w3c.dom.Document;

public interface XmlHttpRequest {
	public int UNSENT = 0;
	public int OPENED = 1;
	public int HEADERS_RECEIVED = 2;
	public int LOADING = 3;
	public int DONE = 4;

	public void setOnReadyStateChange(XMLHttpRequestEventTarget target);
	
	public void open(String method, String url, String user, String password);

	public void setRequestHeader(String header, String value);

	public void setTimeOut(long timeout);

	public void send(String data);
	
	public void abort();

	public int getReadyState();

	public int getStatus();

	public String getResponseHeader(String name);

	public String getResponseText();

	public Document getResponseXML();

	public Exception getError();
}
