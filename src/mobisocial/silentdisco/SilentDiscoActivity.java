package mobisocial.silentdisco;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import mobisocial.ntpsync.NTPSyncService;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.musubi.multiplayer.FeedRenderable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SilentDiscoActivity extends Activity {
    private static final String TAG = "SilentDisco";
    private static final int PICKER_REQUEST = 1;

    private SDMultifeed mMultifeed;
    private Button mSyncButton;
    private Button mStopButton;
    private Button mClearButton;
    private Button mSearchButton;

//    private MediaPlayer mMediaPlayer;
	private NTPSyncService mBoundService;
	private SilentDiscoPlayer mBoundPlayer;
	
	private TextView mTimeDisplay;
	private TextView mQueueText;
	private DateFormat mFormat;
	private Calendar mCalendar = Calendar.getInstance();
	private ProgressBar mProgressBar;
	
	
	private Handler mTimeHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			final NTPSyncService service = mBoundService;
			if (service == null) {
				return;
			}
			final long realTime = System.currentTimeMillis() + service.getOffset();
			mCalendar.setTimeInMillis(realTime);
			mTimeDisplay.setText(mFormat.format(mCalendar.getTime()));
			mTimeDisplay.postInvalidate();

			final long delayMs = (1000 - (realTime % 1000)) + 50;
			sendEmptyMessageDelayed(0, delayMs);
			
			final SilentDiscoPlayer player = mBoundPlayer;
			if (player == null)
				return;
			
			mProgressBar.setProgress(player.getPosition());
		}
	};

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mBoundService = ((NTPSyncService.LocalBinder) service).getService();
			mTimeHandler.sendEmptyMessage(0);
			Log.w(TAG, "time service connected");
		}

		public void onServiceDisconnected(ComponentName className) {
			Log.w(TAG, "time service disconnected");
			mBoundService = null;
		}
	};
	
	private ServiceConnection mPlayerConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mBoundPlayer = ((SilentDiscoPlayer.LocalBinder) service).getService();
			Log.w(TAG, "player service connected");
			
			JSONObject state = mMultifeed.getLatestState();
			try {
				SilentDiscoPlayer player = mBoundPlayer;

	        	if(state.getBoolean("clear")) {
	    			player.clearQueue();
	        	}
	        	
				JSONArray queue = state.getJSONArray("queue");
				Log.i(TAG, "queue: " + queue.toString());
				
				for(int i = 0; i < queue.length(); i++){
					JSONObject song = queue.optJSONObject(i);
					if(song != null) {
						Log.i("SongQueue", song.optString("title"));
						player.enqueueSong(song.optInt("id"), song.optString("songUrl"), song.optLong("startTime"), song.optInt("duration")*1000);
					}
				} 
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			Log.w(TAG, "player service disconnected");
			mBoundPlayer = null;
		}
	};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Set up the game's backend:
        Musubi musubi = new Musubi(this);
        mMultifeed = new SDMultifeed(musubi, musubi.objFromIntent(getIntent()));

        // Bind UI to actions:
        mSyncButton = (Button)findViewById(R.id.sync);
        mStopButton = (Button)findViewById(R.id.stop);
        mClearButton = (Button)findViewById(R.id.clear);

        mSyncButton.setOnClickListener(mSyncListener);
        mStopButton.setOnClickListener(mStopListener);
        mClearButton.setOnClickListener(mClearListener);
        
        
        mQueueText = (TextView)findViewById(R.id.status);
		mTimeDisplay = (TextView) findViewById(R.id.time1);
		mFormat = SimpleDateFormat.getTimeInstance(DateFormat.FULL);
		
		mProgressBar = (ProgressBar) findViewById(R.id.progressBar1);
		
		Log.i(TAG, "creating silent disco");
		
		mSearchButton = (Button) findViewById(R.id.search);
		mSearchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent myIntent = new Intent(view.getContext(), YoutubePickerActivity.class);
                startActivityForResult(myIntent, PICKER_REQUEST);
            }
        });


        // Display the game's current state:
        updatePlayer(mMultifeed.getLatestState());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMultifeed.enableStateUpdates();
        updatePlayer(mMultifeed.getLatestState());
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMultifeed.disableStateUpdates();
    }
    
	@Override
	protected void onStart() {
		super.onStart();

		Intent ntpI = new Intent(this, NTPSyncService.class);
		bindService(ntpI, mConnection, BIND_AUTO_CREATE);
		
		Intent playI = new Intent(this, SilentDiscoPlayer.class);
		bindService(playI, mPlayerConnection, BIND_AUTO_CREATE);

	}

	@Override
	protected void onStop() {
		super.onStop();
		
		unbindService(mConnection);
		unbindService(mPlayerConnection);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
	    super.onActivityResult(requestCode, resultCode, intent);
	    if(intent != null && requestCode == PICKER_REQUEST) {
	    	String title = intent.getStringExtra("title");
	    	String songUrl = intent.getStringExtra("songUrl");
	        Log.i(TAG, "picked song: " + title + " / url: " + songUrl);
	        addSongToQueue(title, songUrl, intent.getLongExtra("time", -1), intent.getIntExtra("duration", -1));
	    }
	    else {
	    	Log.w(TAG, "bad activity result");
	    }
	}
	
	private void addSongToQueue(String title, String songUrl, long time, int duration) {
		try {
			
			JSONObject state = mMultifeed.getLatestState();
			state.put("clear", false);
			
			JSONArray oldQueue = state.optJSONArray("queue");
			JSONArray newQueue = new JSONArray();
			
			
			if(oldQueue != null) {
				for(int i = 0; i < oldQueue.length(); i++) {
					JSONObject song = oldQueue.getJSONObject(i);
					long endTime = song.getLong("startTime") + song.getInt("duration") * 1000; 
					if(time < endTime)
						newQueue.put(song);
				}
			}

			state.put("queue", newQueue);
			long startTime;
			if(newQueue.length() == 0){
				startTime = time + 8000; // + delay
			}
			else {
				 JSONObject lastSong = newQueue.getJSONObject(newQueue.length()-1);
				 startTime = lastSong.getLong("startTime") + lastSong.getInt("duration") * 1000;// + 5000;
			}
			
			JSONObject newSong = new JSONObject();
			newSong.put("id", mMultifeed.getLastTurnNumber()+1);
			newSong.put("title", title);
			newSong.put("songUrl", songUrl);
			newSong.put("startTime", startTime);
			newSong.put("duration", duration);
			
			newQueue.put(newSong);
			
			Log.i(TAG, "Sending queue: " + newQueue.toString());
	        mMultifeed.postJSON(state);			
		} catch (JSONException e) {
			Log.w(TAG, "Failed to add song to Queue");
			e.printStackTrace();
		}
	}
	
    /**
     * Extracts the song queue from the given json object and
     * add it to the player service.
     */
    private void updatePlayer(JSONObject state) {
    	Log.i(TAG, "got player update");
        
    	String status = "";

        try {
			SilentDiscoPlayer player = mBoundPlayer;

        	if(player != null && state.getBoolean("clear")) {
    			player.clearQueue();
    			mQueueText.setText("Empty Queue");
    			return;
        	}
        	
			JSONArray queue = state.getJSONArray("queue");
			Log.i(TAG, "queue: " + queue.toString());
			
			String list = "";
			for(int i = 0; i < queue.length(); i++){
				JSONObject song = queue.optJSONObject(i);
				if(song != null) {
					Log.i("SongQueue", song.optString("title"));
					
//					newSong.put("startTime", startTime);
//					newSong.put("duration", duration);
					long end = song.getInt("duration")*1000 + song.getLong("startTime");

					if (i==0) {
						list += "Now Playing\n";
						mProgressBar.setMax(song.optInt("duration", 1)*1000);
					}
					if(i ==1) {
						list += "Queue: \n";
					}
					else if (i == 5)
						list += "...";
					
					if(i < 5) {
						list += song.optString("title") + "\n";// + 
//					            song.getLong("startTime") +"\t"+
//					            end +"\n";
					
					}
					//else don't show the song
					
					if (player != null)
						player.enqueueSong(song.optInt("id"), song.optString("songUrl"), song.optLong("startTime"), song.optInt("duration")*1000);
					else
						Log.e(TAG, "can't encue song!!!");
				}
			} 
			status = list;
		} catch (JSONException e) {
			e.printStackTrace();
		}
        
//    	long delta = -1;
 
        //DbIdentity user = mMultifeed.getUser(mMultifeed.getGlobalMemberCursor());
//        if (state.optBoolean("play")){//mMultifeed.isMyTurn()) {
//            status = "Playing at offset:" + state.optLong("offset") + " and delta: " + delta;
//        } else {
//            status = "Paused.";
//        }

        mQueueText.setText(status);

        
    }
    
	private long getRealTime() {
		NTPSyncService service = mBoundService;

		if (service == null) 
			return -1;
		Log.i(TAG, "returning time: " + System.currentTimeMillis() + service.getOffset());

		return System.currentTimeMillis() + service.getOffset();
	}


    private View.OnClickListener mSyncListener = new View.OnClickListener() {
		public void onClick(View arg0) {
			
//			NTPSyncService service = mBoundService;
//			if (service == null) {
//				return;
//			}
//			long startTime = System.currentTimeMillis() + service.getOffset();
//
//			// TODO Auto-generated method stub
//	        JSONObject o = new JSONObject();
//	        try {
//	        	o.put("play", true);
//	        	
//	        	if(mMediaPlayer != null)
//	        		o.put("offset", mMediaPlayer.getCurrentPosition());
//	        	else
//	        		o.put("offset", 0);
//	        	
//	        	o.put("time", startTime);
//	        } catch (JSONException e) {
//	            Log.w(TAG, "Failed to set play", e);
//	        }
//            mMultifeed.postJSON(o);
			SilentDiscoPlayer player = mBoundPlayer;
			if(player == null)
				return;
			player.pausePlay();
			Log.i(TAG, "sync!!!");

		}
	};
	
	private View.OnClickListener mStopListener = new View.OnClickListener() {
		public void onClick(View arg0) {
//	        JSONObject o = new JSONObject();
//	        try {
//	        	o.put("play", false);
//	        	o.put("offset", 0);
//	        } catch (JSONException e) {
//	            Log.w(TAG, "Failed to set play", e);
//	        }
//            mMultifeed.postJSON(o);	
			SilentDiscoPlayer player = mBoundPlayer;
			if(player == null)
				return;
			player.stop();
		}
	};
	
	private View.OnClickListener mClearListener = new View.OnClickListener() {
		public void onClick(View arg0) {			
			JSONObject o = new JSONObject();
	        try {
	        	o.put("clear", true);
	        } catch (JSONException e) {
	            Log.w(TAG, "Failed to set play", e);
	        }
            mMultifeed.postJSON(o);
		}
	};

	
    private class SDMultifeed extends MultiFeed {

    	public SDMultifeed(Musubi musubi, DbObj objContext) {
    		super(musubi, objContext);
    	}

    	@Override
    	protected void onStateUpdate(JSONObject state) {
    		updatePlayer(state);
    	}

    	@Override
    	protected FeedRenderable getFeedView(JSONObject state) {
//    		try {
    			//JSONArray squares = state.getJSONArray("s");
    			StringBuilder html = new StringBuilder("<html><head><style>")
//    			.append("td { min-width:18px; }")
    			.append("</style></head>")
    			.append("<body>Rage Rage Rage")
    			.append("</body>")
    			.append("</html>");
    			return FeedRenderable.fromHtml(html.toString());
//    		} catch (JSONException e) {
//    			Log.w(TAG, "Error getting renderable state");
//    			return FeedRenderable.fromText("[TicTacToe rendering error]");
//    		}
    	}
    }

    public static JSONObject getInitialState() {
        JSONObject o = new JSONObject();
        try {
        	o.put("clear", false);
        	JSONArray queue = new JSONArray();
        	o.put("queue", queue);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to set play", e);
        }
        return o;
    };
}