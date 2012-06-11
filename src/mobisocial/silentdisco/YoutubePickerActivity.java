package mobisocial.silentdisco;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import mobisocial.ntpsync.NTPSyncService;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
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
//    private MediaPlayer mMediaPlayer;
	private NTPSyncService mBoundService;

    private EditText search;
    private final String YOUTUBE_URL = "https://gdata.youtube.com/feeds/api/videos?v=2&alt=jsonc&format=6&max-results=10&q=";
    private final String TAG = "YTPicker";
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search);
		list = (ListView) this.findViewById(R.id.searchResults);
        search = (EditText) this.findViewById(R.id.searchBox);
        int i =7; // fix compiler
		list.setOnItemClickListener(mListListener);
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
	
	@Override
	protected void onStart() {
		super.onStart();

		Intent ntpI = new Intent(this, NTPSyncService.class);
		bindService(ntpI, mConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
		
		unbindService(mConnection);
	}
	
	private void returnResult(String title, String url, int duration) {
		Intent intent = new Intent();
		intent.putExtra("title", title);
		intent.putExtra("songUrl", url);
		intent.putExtra("time", getRealTime());
		intent.putExtra("duration", duration);

		setResult(RESULT_OK, intent);

		YoutubePickerActivity.this.finish();
	}
	
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mBoundService = ((NTPSyncService.LocalBinder) service).getService();
			Log.w(TAG, "time service connected");
		}

		public void onServiceDisconnected(ComponentName className) {
			Log.w(TAG, "time service disconnected");
			mBoundService = null;
		}
	};
	
	private long getRealTime() {
		NTPSyncService service = mBoundService;

		if (service == null) 
			return -1;
		
		Log.i(TAG, "returning time: " + System.currentTimeMillis() + service.getOffset());

		return System.currentTimeMillis() + service.getOffset();
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
				
				// Return song title, resource url and duration
				JSONObject content = item.optJSONObject("content");
				returnResult(item.optString("title"), content.optString("6"), item.optInt("duration"));
				
				/* Play song picked:
				 *
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
				} */
				
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