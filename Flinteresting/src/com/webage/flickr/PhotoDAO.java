package com.webage.flickr;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.webage.util.Logger;
import com.webage.util.XMLHttpRequestEventTarget;
import com.webage.util.XmlHttpRequest;
import com.webage.util.XmlHttpRequestImpl;

public class PhotoDAO {
	private static final int DEFAULT_FETCH_COUNT = 100;
	Delegate<ArrayList<Photo>> delegate;
	XmlHttpRequest req = new XmlHttpRequestImpl();
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
		final String qualityFlag = computeQualityFlag();
		
		Logger.v("Quality flag: " + qualityFlag);
		
		req.open("get", "http://api.flickr.com/services/rest/?method=flickr.interestingness.getList&api_key=8fa47539dde7b426a299aa41da9b02fb&per_page=" + fetchCount, null, null);
		req.setOnReadyStateChange(new XMLHttpRequestEventTarget() {
			@Override
			public void onLoad() {
				Logger.v("Done downloading.");
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
					String url = "http://farm" + farm + ".staticflickr.com/" + server + "/" + p.getId() + "_" + secret;
					if (qualityFlag != null) {
						url = url + "_" + qualityFlag;
					}
					url += ".jpg";
					
					p.setUrl(url);
					photoList.add(p);
				}
				//Randomize the list
				Collections.shuffle(photoList);
				delegate.onLoad(photoList);
			}

			@Override
			public void onError() {
				Logger.v("Error downloading image list", req.getError());
				delegate.onError(req.getError());
			}
		});
		req.send(null);
		delegate.onLoadEnd();
	}

	private String computeQualityFlag() {
		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager mgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		mgr.getDefaultDisplay().getMetrics(metrics);
		
		int dimension = metrics.heightPixels > metrics.widthPixels ? metrics.heightPixels : metrics.widthPixels;
		
		String flagList[] = {"n", null, "z", "c", "b"};
		int sizeList[] = {320, 500, 640, 800, 1024};
		
		//Return the smallest image size which is larger than the 
		//device
		for (int i = 0; i < sizeList.length; ++i) {
			if (sizeList[i] >= dimension) {
				return flagList[i];
			}
		}
		
		//return max image size
		return flagList[sizeList.length - 1];
	}

}
