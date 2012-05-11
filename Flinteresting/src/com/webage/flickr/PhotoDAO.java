package com.webage.flickr;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.preference.PreferenceManager;

import com.webage.util.XMLHttpRequestEventTarget;
import com.webage.util.XmlHttpRequest;
import com.webage.util.XmlHttpRequestImpl;

public class PhotoDAO {
	private static final int DEFAULT_FETCH_COUNT = 100;
	Delegate<ArrayList<Photo>> delegate;
	XmlHttpRequest req = new XmlHttpRequestImpl();
	Logger logger = Logger.getLogger("Flinteresting");
	Context context = null;

	public PhotoDAO() {
		
	}
	
	public PhotoDAO(Context ctx) {
		context = ctx;
	}
	
	public File getCachedFile(Photo p) {
		return new File(getCachedDir(), p.getId());
	}
	
	public File getCachedDir() {
		File dir = context.getExternalCacheDir();
		
		if (dir == null) {
			dir = context.getCacheDir();
		}
		
		return dir;
	}
	
	public int getPrefAsInt(String key, int defaultValue) {
		String str = PreferenceManager.getDefaultSharedPreferences(context).getString(key, String.valueOf(defaultValue));
		
		return Integer.parseInt(str);
	}

	public void fetchInterestingList(Delegate<ArrayList<Photo>> d) {
		int fetchCount = getPrefAsInt("fetch_count", DEFAULT_FETCH_COUNT);
		this.delegate = d;
		delegate.onLoadStart();
		
		req.open("get", "http://api.flickr.com/services/rest/?method=flickr.interestingness.getList&api_key=8fa47539dde7b426a299aa41da9b02fb&per_page=" + fetchCount, null, null);
		req.setOnReadyStateChange(new XMLHttpRequestEventTarget() {
			@Override
			public void onLoad() {
				logger.info("Done downloading.");
				Document doc = req.getResponseXML();
				NodeList list = doc.getElementsByTagName("photo");
				ArrayList<Photo> photoList = new ArrayList<Photo>(500);
				
				for (int i = 0; i < list.getLength(); ++i) {
					Element e = (Element) list.item(i);
					Photo p = new Photo();
					
					p.setId(e.getAttribute("id"));
					p.setTitle(e.getAttribute("title"));
					
					String farm = e.getAttribute("farm");
					String server = e.getAttribute("server");
					String secret = e.getAttribute("secret");
					String url = "http://farm" + farm + ".staticflickr.com/" + server + "/" + p.getId() + "_" + secret + ".jpg";
					p.setUrl(url);
					photoList.add(p);
				}
				//Randomize the list
				Collections.shuffle(photoList);
				delegate.onLoad(photoList);
			}

			@Override
			public void onError() {
				logger.log(Level.SEVERE, "Error downloading image list", req.getError());
				delegate.onError(req.getError());
			}
		});
		req.send(null);
		delegate.onLoadEnd();
	}

}
