package com.webage.flickr;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.webage.flickrcast.R;
import com.webage.util.Logger;
import com.webage.util.XMLHttpRequestEventTarget;
import com.webage.util.XmlHttpRequest;
import com.webage.util.XmlHttpRequestImpl;
import com.webage.util.XmlUtil;

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
	
	public Bitmap loadImageFromCache(Photo p) {
		File cacheFile = getCachedFile(p);
		
		return decodeFile(cacheFile);
	}

	private Bitmap decodeFile(File f) {
		try {
			/*
			 * // decode image size BitmapFactory.Options o = new
			 * BitmapFactory.Options(); o.inJustDecodeBounds = true;
			 * BitmapFactory.decodeStream(new FileInputStream(f), null, o);
			 * 
			 * // Find the correct scale value. It should be the power of 2.
			 * final int REQUIRED_SIZE = 70; int width_tmp = o.outWidth,
			 * height_tmp = o.outHeight; int scale = 1; while (true) { if
			 * (width_tmp / 2 < REQUIRED_SIZE || height_tmp / 2 < REQUIRED_SIZE)
			 * break; width_tmp /= 2; height_tmp /= 2; scale *= 2; }
			 */
			Logger.v("Loading photo from cache: " + f.getName());
			// decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			// o2.inSampleSize = scale;
			InputStream is = new FileInputStream(f);
			Bitmap bm = BitmapFactory.decodeStream(is, null, o2);
			is.close();

			return bm;
		} catch (Exception e) {
			Logger.v("Failed to load image", e);
		}
		return null;
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
				ArrayList<Photo> photoList = buildPhotoList(qualityFlag, doc);
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
	
	public ArrayList<Photo> fetchInterestingList() {
		int fetchCount = getPrefAsInt("fetch_count", DEFAULT_FETCH_COUNT);
		final String qualityFlag = computeQualityFlag();
		
		Logger.v("Quality flag: " + qualityFlag);
		
		Document doc = XmlUtil.loadDocument("http://api.flickr.com/services/rest/?method=flickr.interestingness.getList&api_key=8fa47539dde7b426a299aa41da9b02fb&per_page=" + fetchCount);
		ArrayList<Photo> photoList = buildPhotoList(qualityFlag, doc);
		
		return photoList;
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

	private ArrayList<Photo> buildPhotoList(final String qualityFlag,
			Document doc) {
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
		return photoList;
	}

}
