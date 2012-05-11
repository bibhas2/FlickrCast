package com.webage.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import android.os.AsyncTask;

public class XmlHttpRequestImpl implements XmlHttpRequest {
	HttpClient mClient = null;
	private String method = "GET";
	private String url;
	private String user;
	private String password;
	private long timeout = -1;
	AsyncTask<Void, Void, Integer> mTask;
	private int status;
	private XMLHttpRequestEventTarget target;
	private Document responseXML;
	protected String responseText;
	Exception error = null;
	
	public XmlHttpRequestImpl() {

	}

	public XmlHttpRequestImpl(HttpClient cli) {
		mClient = cli;
	}
	public void open(String method, String url, String user, String password) {
		this.method = method;
		this.url = url;
		this.user = user;
		this.password = password;
	}
	
	public void setRequestHeader(String header, String value) {

	}

	public void setTimeOut(long timeout) {
		this.timeout = timeout;
	}

	private Document buildDocument(InputStream in) throws Exception {
		DocumentBuilderFactory factory = null;
		DocumentBuilder builder = null;
		Document ret = null;

		factory = DocumentBuilderFactory.newInstance();
		builder = factory.newDocumentBuilder();

		ret = builder.parse(new InputSource(in));
		return ret;
	}

	public String buildString(InputStream is) throws Exception {
		Writer writer = new StringWriter();

		char[] buffer = new char[1024];
		Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		int n;
		while ((n = reader.read(buffer)) != -1) {
			writer.write(buffer, 0, n);
		}
		return writer.toString();
	}

	public boolean isXML(String cType) {
		String parts[] = cType.split(";");
		
		return ((parts[0].equals("text/xml") || parts[0].equals("application/xml")));
	}
	
	public void send(String data) {
		if (mClient == null) {
			HttpParams params = new BasicHttpParams();
			if (timeout > 0) {
				HttpConnectionParams.setConnectionTimeout(params, 10000);
				HttpConnectionParams.setSoTimeout(params, 10000);
			}
			mClient = new DefaultHttpClient(params);
		} else {
			// Abort last ongoing request
			abort();
		}

		init();
		
		mTask = new AsyncTask<Void, Void, Integer>() {
			protected Integer doInBackground(Void... params) {
				HttpGet get = new HttpGet(url);
				HttpResponse res;
				try {
					res = mClient.execute(get);
					status = res.getStatusLine().getStatusCode();
					String contentType = res.getEntity().getContentType()
							.getValue();
					if (isXML(contentType)) {
						// Get DOM
						responseXML = buildDocument(res.getEntity()
								.getContent());
					} else {
						responseText = buildString(res.getEntity()
								.getContent());
					}
					//res.getEntity().getContent().close();
				} catch (Exception e) {
					error = e;
				}

				return null;
			}

			protected void onPostExecute(Integer params) {
				if (error != null) {
					target.onError();
				} else {
					target.onLoad();
				}
			}

			protected void onPreExecute() {
			}
		};
		mTask.execute();
	}

	public int getReadyState() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getStatus() {
		return status;
	}

	public String getResponseHeader(String name) {
		return null;
	}

	public String getResponseText() {
		return responseText;
	}

	public Document getResponseXML() {
		return responseXML;
	}

	public void setOnReadyStateChange(XMLHttpRequestEventTarget target) {
		this.target = target;

	}

	public void abort() {
		
	}
	public void init() {
		error = null;
		status = 0;
		responseText = null;
		responseXML = null;
	}

	public Exception getError() {
		return error;
	}
}
