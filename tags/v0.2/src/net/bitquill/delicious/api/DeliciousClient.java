/**
 * Copyright 2009 Spiros Papadimitriou <spapadim@cs.cmu.edu>
 * 
 * This file is part of Bitlicious.
 * 
 * Bitlicious is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Bitlicious is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Bitlicious.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bitquill.delicious.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bitquill.delicious.DeliciousApp;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.util.Log;

/**
 * Class that encapsulates Del.icio.us API calls.
 * See http://www.delicious.com/help/api for details.
 * Uses a thread-safe HTTP client connection manager.
 */
public final class DeliciousClient {
    private static final String TAG = DeliciousClient.class.getSimpleName();

    private String mApiEndpoint = API_ENDPOINT_DEFAULT;
	private UsernamePasswordCredentials mCredentials;
	private DefaultHttpClient mHttpClient;
	private XmlPullParserFactory mXmlParserFactory;
	
	public static final int MAX_TOTAL_CONNECTIONS = 5;
	
	public static final String USER_AGENT_STRING = "bitlicious/0.1.2" +
		" (Linux; U; Android " + android.os.Build.VERSION.RELEASE + ")" +
		" Apache-HttpClient/UNAVAILABLE" + 
		" spapadim@cs.cmu.edu";
	
	public static final String API_ENDPOINT_DEFAULT = "https://api.del.icio.us/v1/";
	
	public static final String API_POSTS_UPDATE = "posts/update";
	public static final String API_POSTS_ADD = "posts/add?";
	public static final String API_POSTS_GET = "posts/get?";
	public static final String API_POSTS_ALL = "posts/all?";
	public static final String API_POSTS_DELETE = "posts/delete?";
	//public static final String API_POSTS_SUGGEST = "posts/suggest?sort=popular&popular_count=10&";
	public static final String API_POSTS_SUGGEST = "posts/suggest?";
	public static final String API_POSTS_RECENT = "posts/recent?";
	public static final String API_TAGS_GET = "tags/get";
	
	// String parameters
	public static final String API_PARAM_URL = "url=";
	public static final String API_PARAM_DESCRIPTION = "description=";
	public static final String API_PARAM_EXTENDED = "extended=";
	public static final String API_PARAM_TAG = "tag=";
	public static final String API_PARAM_TAGS = "tags=";
	public static final String API_PARAM_HASHES = "hashes=";
	public static final String API_PARAM_DATE = "dt=";
	public static final String API_PARAM_DATE_FROM = "fromdt=";
	public static final String API_PARAM_DATE_TO = "todt=";
	// Numeric parameters
	public static final String API_PARAM_COUNT = "count=";
	public static final String API_PARAM_RESULTS = "results=";
	public static final String API_PARAM_START = "start=";
	// Boolean parameters
	public static final String API_PARAM_SHARED = "shared=";
	public static final String API_PARAM_REPLACE = "replace=";
	public static final String API_PARAM_META = "meta=";
	// Boolean values
	public static final String API_VALUE_NO = "no";
	public static final String API_VALUE_YES = "yes";
	
	//public static final String FEEDS_ENDPOINT = "http://feeds.delicious.com/v2/json/";
	//public static final String FEEDS_POPULAR = "popular/";
	
	public DeliciousClient () {
	    this(API_ENDPOINT_DEFAULT);
	}
	
	public DeliciousClient (String endpoint) {
		// Create HTTP client and set parameters
		HttpParams params = new BasicHttpParams();
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
		HttpProtocolParams.setUseExpectContinue(params, true);
		HttpProtocolParams.setUserAgent(params, USER_AGENT_STRING);
        ConnManagerParams.setMaxTotalConnections(params, MAX_TOTAL_CONNECTIONS);
		SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		registry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
		ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager(params, registry);
		mHttpClient = new DefaultHttpClient(connManager, params);
		setApiEndpoint(endpoint);
	}
	
	private final XmlPullParser getXmlParser (HttpEntity e) 
		throws XmlPullParserException, IllegalStateException, IOException {
		if (mXmlParserFactory == null) {
			mXmlParserFactory = XmlPullParserFactory.newInstance();
		}
		XmlPullParser parser = mXmlParserFactory.newPullParser();
		String charSet = EntityUtils.getContentCharSet(e);
		if (charSet == null) {
			charSet = HTTP.DEFAULT_CONTENT_CHARSET;
		}
		parser.setInput(e.getContent(), charSet);
		return parser;
	}
	
	/**
	 * Contains all parser creation and exception handling logic, for parsers
	 * that consume an HttpEntity stream and produce a single object as a result.
	 * 
	 * XXX - does not provide a way to return a result before input is completely consumed
	 * 
	 * @author spapadim
	 *
	 */
	protected abstract class XmlResponseParser<T> {
		private HttpEntity e;
		private T result;
		
		public XmlResponseParser (HttpEntity e)  { this.e = e; } 
	
		public void setResult (T result) { this.result = result; }
		public T getResult ()  { return result; }
		
		/**
		 * Handle an XML event from the parser.
		 * @return  False if parsing is complete, true otherwise
		 */
		abstract public boolean onXmlEvent (XmlPullParser parser, int eventType) throws XmlPullParserException, IOException;
		
		public void onInit (XmlPullParser parser) { }
		
		public T parse () {
			try {
				XmlPullParser parser = getXmlParser(e);
				onInit(parser);
				int eventType = parser.getEventType();
				while (eventType != XmlPullParser.END_DOCUMENT) {
					if (!onXmlEvent(parser, eventType)) {
						break;
					}
					eventType = parser.next();
				}
			} catch (Throwable t) {
				Log.e(TAG, "XmlResponseParser exception", t);
				setResult(null);
		 	} finally {
		 		try {
		 			e.consumeContent();
		 		} catch (Throwable t2) { }
		 	}
		 	return getResult();
		}
	}
	
	public final void setApiEndpoint (String endpoint) {
	    mApiEndpoint = endpoint;
	}
	
	public final String getApiEndpoint () {
	    return mApiEndpoint;
	}
	
	private String getApiHostname () {
	    return URI.create(mApiEndpoint).getHost();
	}
	
	public final void setCredentials (String username, String password) {
		if (username != null && password != null) {
			mCredentials = new UsernamePasswordCredentials(username, password);
			mHttpClient.getCredentialsProvider()
				.setCredentials(new AuthScope(getApiHostname(), AuthScope.ANY_PORT), 
								mCredentials);
		}
	}
	
	public final boolean hasCredentials () {
		return (mCredentials != null);
	}
	
	public final String getUsername () {
		return (mCredentials == null) ? null : mCredentials.getUserName();
	}
	
	public final String getPassword () {
		return (mCredentials == null) ? null : mCredentials.getPassword();
	}
	
	public final boolean validateCredentials () {
		// Check that credentials have been set credential
		if (mCredentials == null) {
			return false;
		}
		// Try a request with a short response and see if it succeeds
		return (lastUpdate() != null);
	}
	
	public final String fetchHtmlTitle (String url) {
		HttpGet get = new HttpGet(url);
		
		HttpEntity e;
		try {
			HttpResponse resp = mHttpClient.execute(get);
			e = resp.getEntity();
		} catch (Throwable t) {
		    Log.d(TAG, "HTTP request failed", t);
			return null;
		}
		
		XmlResponseParser<String> titleParser = new XmlResponseParser<String>(e) {
			@Override
			public boolean onXmlEvent(XmlPullParser parser, int eventType)
					throws XmlPullParserException, IOException {
				switch(eventType) {
				case XmlPullParser.START_TAG:
				    String tagName = parser.getName().toLowerCase();
					if ("title".equals(tagName)) {
						// Handle HTML
						setResult(parser.nextText());
						return false;
					} else if ("card".equals(tagName)) {
						// Handle WML - XXX - check that this is proper semantics
						setResult(parser.getAttributeValue(null, "title"));
						return false;
					}
					break;
				}
				return true;
			}
		};
		return titleParser.parse();
	}
	
	private final String parseResultResponse (HttpEntity e) {
		XmlResponseParser<String> resultParser = new XmlResponseParser<String>(e) {
			@Override
			public boolean onXmlEvent(XmlPullParser parser, int eventType)
					throws XmlPullParserException, IOException {
				switch (eventType) {
				case XmlPullParser.START_TAG:
					if ("result".equals(parser.getName())) {
						setResult(parser.getAttributeValue(null, "code"));
					}
					break;
				}
				return true;
			}
		};
		return resultParser.parse();
	}
	
	public final String lastUpdate () {
		HttpGet get = new HttpGet(mApiEndpoint + API_POSTS_UPDATE);
		HttpEntity e;
		try {
			HttpResponse resp = mHttpClient.execute(get);
			e = resp.getEntity();
		} catch (Throwable t) {
			return null;
		}

		XmlResponseParser<String> updateParser = new XmlResponseParser<String>(e) {
			@Override
			public boolean onXmlEvent(XmlPullParser parser, int eventType)
					throws XmlPullParserException, IOException {
				switch (eventType) {
				case XmlPullParser.START_TAG:
					if ("update".equals(parser.getName())) {
						setResult(parser.getAttributeValue(null, "time"));
					}
					break;
				} 		
				return true;
			}
		};
		return updateParser.parse();
	}
	
	public final boolean addBookmark (Bookmark bm, boolean replace, boolean shared) {
		String url = bm.getUrl();
		String description = bm.getDescription(); 
		String extended = bm.getExtended();
		String tags = bm.getTags();
		String dateStr = bm.getDateString();
		
		// XXX - move this check to Bookmark class
		if (url == null || "".equals(url) || 
			description == null || "".equals(description)) {
			return false;
		}
		
		String reqUrl = mApiEndpoint + API_POSTS_ADD +
			API_PARAM_URL + URLEncoder.encode(url) + "&" +
			API_PARAM_DESCRIPTION + URLEncoder.encode(description);
		if (extended != null && !"".equals(extended)) {
			reqUrl += "&" + API_PARAM_EXTENDED + URLEncoder.encode(extended);
		}
		reqUrl += "&" + 
			API_PARAM_TAGS + URLEncoder.encode(tags) + "&" +
			API_PARAM_DATE + dateStr;
		if (!replace) {
			reqUrl += "&" + API_PARAM_REPLACE + API_VALUE_NO;
		}
		if (!shared) {
			reqUrl += "&" + API_PARAM_SHARED + API_VALUE_NO;
		}

		HttpGet get = new HttpGet(reqUrl);

		try {
			HttpResponse resp = mHttpClient.execute(get);
			String resultMessage = parseResultResponse(resp.getEntity());
			return (resultMessage != null && "done".equals(resultMessage));
		} catch (Throwable e) {
			return false;
		}
	}
		
	public final Map<String,Integer> getTags () {
		HttpGet get = new HttpGet(mApiEndpoint + API_TAGS_GET);

		HttpEntity e;
		try {
			HttpResponse resp = mHttpClient.execute(get);
			e = resp.getEntity();
		} catch (Throwable t) {
			return null;
		}

		XmlResponseParser<Map<String,Integer>> tagsParser = new XmlResponseParser<Map<String,Integer>> (e) {
			@Override
			public void onInit (XmlPullParser parser) {
				setResult(new HashMap<String,Integer>());
			}

			@Override
			public boolean onXmlEvent(XmlPullParser parser, int eventType)
					throws XmlPullParserException, IOException {
				switch (eventType) {
				case XmlPullParser.START_TAG:
					if ("tag".equals(parser.getName())) {
						String tagName = parser.getAttributeValue(null, "tag");
						int count = Integer.parseInt(parser.getAttributeValue(null, "count"));
						getResult().put(tagName, count);
					}
					break;
				}
				return true;
			}
			
		};
		return tagsParser.parse();		
	}
	
	public final List<String> suggestTags (String url) {
		HttpGet get = new HttpGet(mApiEndpoint + API_POSTS_SUGGEST + 
					API_PARAM_URL + URLEncoder.encode(url));
		
		HttpEntity e;
		try {
			HttpResponse resp = mHttpClient.execute(get);
			e = resp.getEntity();
		} catch (Throwable t) {
			return null;
		}

		XmlResponseParser<List<String>> suggestParser = new XmlResponseParser<List<String>>(e) {
			@Override
			public void onInit (XmlPullParser parser) {
				setResult(new ArrayList<String>());
			}

			@Override
			public boolean onXmlEvent(XmlPullParser parser, int eventType)
					throws XmlPullParserException, IOException {
				switch (eventType) {
				case XmlPullParser.START_TAG:
					if ("popular".equals(parser.getName()) || 
						"recommended".equals(parser.getName()) ||
						"network".equals(parser.getName())) {
						String tag = parser.nextText();
						if (!getResult().contains(tag)) {
							getResult().add(tag);
						}
					}
					break;
				}		
				return true;
			}
		};
		return suggestParser.parse();
	}
	
	private static int parseIntegerOrNull (String s, int nullValue) {
		return (s == null) ?  nullValue : Integer.parseInt(s);
	}
	
	private final BookmarkSearchResults parsePostListResponse (HttpEntity e, final String queryTag) {
		XmlResponseParser<BookmarkSearchResults> postsParser = new XmlResponseParser<BookmarkSearchResults>(e) {
			@Override
			public boolean onXmlEvent(XmlPullParser parser, int eventType)
					throws XmlPullParserException, IOException {
				switch (eventType) {
				case XmlPullParser.START_TAG:
					//android.util.Log.v(DeliciousApp.LOG_TAG, "parser for " + queryTag + " results got tag: " + parser.getName());
					if ("post".equals(parser.getName())) {
						String url = parser.getAttributeValue(null, "href");
						String desc = parser.getAttributeValue(null, "description");
						String ext = parser.getAttributeValue(null, "extended");
						String tags = parser.getAttributeValue(null, "tag");
						String dateStr = parser.getAttributeValue(null, "time");
						String meta = parser.getAttributeValue(null, "meta");
						try {
							Bookmark bm = new Bookmark(url, desc, ext, tags, dateStr, meta);
							getResult().add(bm);
						} catch (ParseException ex) {
							// do nothing - FIXME
						}
					} else if ("posts".equals(parser.getName())) {
						int total = parseIntegerOrNull(parser.getAttributeValue(null, "total"), BookmarkSearchResults.TOTAL_UNDEFINED);
						//int count = parseIntegerOrNull(parser.getAttributeValue(null, "count"), -1);
						int start = parseIntegerOrNull(parser.getAttributeValue(null, "start"), 0);
						BookmarkSearchResults results = new BookmarkSearchResults(queryTag, start, total);
						setResult(results);
					}
					break;
				}
				return true;
			}
		};
		return postsParser.parse();
	}
	
	public final BookmarkSearchResults getRecent (String tag, int count) {
		// Adjust count as necessary
		if (count <= 0) {
			count = 15;   // Set to delicious default value 
		}
		if (count > 100) {
			count = 100;  // Delicious does not allow values larger than 100 
		}
		// Construct initial url string
		String reqUrl = mApiEndpoint + API_POSTS_RECENT +
			API_PARAM_COUNT + count;
		// Append tag, if given
		if (tag != null) {
			reqUrl += API_PARAM_TAG + tag;
		}
		
		HttpGet get = new HttpGet(reqUrl);
		try {
			HttpResponse resp = mHttpClient.execute(get);
			return parsePostListResponse(resp.getEntity(), tag);
		} catch (Throwable t) {
			android.util.Log.e(DeliciousApp.LOG_TAG, "getRecent error", t);
			return null;
		}
	}
	
	public final BookmarkSearchResults searchBookmarks (String tag, int results, int start) {
		String reqUrl = mApiEndpoint + API_POSTS_ALL +
			API_PARAM_TAG + tag;
		if (results > 0) {
			reqUrl += "&" + API_PARAM_RESULTS + results;
		}
		if (start > 0) {
			reqUrl += "&" + API_PARAM_START + start;
		}
		
		HttpGet get = new HttpGet(reqUrl);

		try {
			HttpResponse resp = mHttpClient.execute(get);
			BookmarkSearchResults searchResults = parsePostListResponse(resp.getEntity(), tag);
			return searchResults;
		} catch (Throwable t) {
			android.util.Log.e(DeliciousApp.LOG_TAG, "searchBookmarks error", t);
			return null;
		}
	}
}
