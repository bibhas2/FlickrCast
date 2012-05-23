package com.webage.util;

import java.io.InputStream;

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

public class XmlUtil {
	public static Document loadDocument(String url) {
		HttpParams params = new BasicHttpParams();
		Document doc = null;
		
		HttpConnectionParams.setConnectionTimeout(params, 10000);
		HttpConnectionParams.setSoTimeout(params, 10000);
		HttpClient client = new DefaultHttpClient(params);
		HttpGet get = new HttpGet(url);
		HttpResponse res;
		try {
			res = client.execute(get);
			int status = res.getStatusLine().getStatusCode();
			if (status != 200) {
				throw new Exception("Invalid status code: " + status);
			}
			String contentType = res.getEntity().getContentType()
					.getValue();
			if (isXML(contentType)) {
				// Get DOM
				doc = buildDocument(res.getEntity()
						.getContent());
			} else {
				throw new Exception("Invalid content type: " + contentType);
			}
		} catch (Exception e) {
			Logger.v("Error in loadDocument()", e);
		}
		
		return doc;
	}
	
	private static boolean isXML(String cType) {
		String parts[] = cType.split(";");
		
		return ((parts[0].equals("text/xml") || parts[0].equals("application/xml")));
	}
	private static Document buildDocument(InputStream in) throws Exception {
		DocumentBuilderFactory factory = null;
		DocumentBuilder builder = null;
		Document ret = null;

		factory = DocumentBuilderFactory.newInstance();
		builder = factory.newDocumentBuilder();

		ret = builder.parse(new InputSource(in));
		return ret;
	}
}
