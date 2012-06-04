package mobisocial.silentdisco;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class YoutubePickerActivity extends Activity {
	private ListView list;
	private JSONAdapter jsonAdapter;
    private MediaPlayer mMediaPlayer;
    private EditText search;
    private final String YOUTUBE_URL = "https://gdata.youtube.com/feeds/api/videos?v=2&alt=jsonc&format=6&max-results=10&q=";
    private final String TAG = "YTPicker";
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search);
		list = (ListView) this.findViewById(R.id.searchResults);
		list.setOnItemClickListener(mListListener);
        search = (EditText) this.findViewById(R.id.searchBox);
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            	Log.d(TAG, "editor action");
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                	searchYoutube(null, v.getText().toString());
                    return true;
                }
                return false;
            }
        });
        //search.setOnQueryTextListener(mSearchListener);
	}
	
//	private SearchView.OnQueryTextListener mSearchListener = new SearchView.OnQueryTextListener() {
//		@Override
//		public boolean onQueryTextChange(String arg0) {
//			return false;
//		}
//
//		@Override
//		public boolean onQueryTextSubmit(String query) {
//			searchYoutube(null, query);
//			return false;
//		}
//	};
	
	private AdapterView.OnItemClickListener mListListener = new AdapterView.OnItemClickListener() {
		public void onItemClick (AdapterView parent, View view, int position, long id){
				JSONObject item = jsonAdapter.getItem(position);
		
				Log.i(TAG, "clicked item: " + item.optString("title"));
				
				// TODO add song to playlist
				
				/* Play song picked:
				 */ 
	        	if(mMediaPlayer == null)
	        		mMediaPlayer = new MediaPlayer();
	        	else
	        		mMediaPlayer.stop();
	        		mMediaPlayer.reset();
	        	
	        	JSONObject content = item.optJSONObject("content");
	        	try {
					mMediaPlayer.setDataSource(content.optString("6"));
		        	mMediaPlayer.prepare();
		        	mMediaPlayer.start();

				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SecurityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 

//				YoutubePickerActivity.this.finish();
		}
	};
	
	


	public void searchYoutube(View view, String query) {
		// Encode the query to be appended to the YouTube URL
		String search = "";
		try {
			search = URLEncoder.encode(query, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		SearchYoutubeTask task = new SearchYoutubeTask();
		task.execute(new String[]{YOUTUBE_URL+search});
	}
	
	private class SearchYoutubeTask extends AsyncTask<String, Void, String> {
		@Override
		protected String doInBackground(String... urls) {
			String response = "";
			for (String url : urls) {
				Log.d(TAG, "fetching url: " + url);
				DefaultHttpClient client = new DefaultHttpClient();
				HttpGet httpGet = new HttpGet(url);
				try {
					HttpResponse execute = client.execute(httpGet);
					InputStream content = execute.getEntity().getContent();

					BufferedReader buffer = new BufferedReader(
							new InputStreamReader(content));
					String s = "";
					while ((s = buffer.readLine()) != null) {
						response += s;
					}
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return response;
		}

		@Override
		protected void onPostExecute(String result) {
			JSONObject json;
			try {
				json = new JSONObject(result);
				jsonAdapter = new JSONAdapter(YoutubePickerActivity.this, json);
				list.setAdapter(jsonAdapter);
			} catch (JSONException e) {
				e.printStackTrace();
			}

			Log.d(TAG, result);
		}
	}
}