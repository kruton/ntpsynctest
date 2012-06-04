package mobisocial.silentdisco;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import mobisocial.ntpsync.NTPSyncService;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbFeed;
import mobisocial.socialkit.musubi.DbIdentity;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.musubi.multiplayer.FeedRenderable;
import mobisocial.socialkit.musubi.multiplayer.TurnBasedApp;
import mobisocial.socialkit.obj.MemObj;
import mobisocial.silentdisco.R;
import mobisocial.silentdisco.R.id;
import mobisocial.silentdisco.R.layout;
import mobisocial.silentdisco.R.raw;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class SilentDiscoActivity extends Activity {
    private static final String TAG = "SilentDisco";
    private static final int PICKER_REQUEST = 1;

    private SDMultifeed mMultifeed;
    //private Button mTokenButton;
    private Button mPlayButton;
    private Button mPauseButton;
    private Button mSearchButton;

    private MediaPlayer mMediaPlayer;
	private NTPSyncService mBoundService;
	private SilentDiscoPlayer mBoundPlayer;

	private TextView mTimeDisplay;
	private DateFormat mFormat;
	private Calendar mCalendar = Calendar.getInstance();

	
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
        mPlayButton = (Button)findViewById(R.id.play);
        mPauseButton = (Button)findViewById(R.id.pause);
        mPlayButton.setOnClickListener(mPlayListener);
        mPauseButton.setOnClickListener(mPauseListener);
       
		mTimeDisplay = (TextView) findViewById(R.id.time1);
		mFormat = SimpleDateFormat.getTimeInstance(DateFormat.FULL);
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
	        addSongToQueue(title, songUrl, intent.getLongExtra("time", -1));
	    }
	    else {
	    	Log.w(TAG, "bad activity result");
	    }
	}
	
	private void addSongToQueue(String title, String songUrl, long time) {
		try {
			JSONObject newSong = new JSONObject();
			newSong.put("id", mMultifeed.getLastTurnNumber()+1);
			newSong.put("title", title);
			newSong.put("songUrl", songUrl);
			newSong.put("startTime", time + 10000);
			
			JSONObject state = mMultifeed.getLatestState();
			JSONArray queue = state.optJSONArray("queue");
			if(queue == null){
				queue = new JSONArray();
			}
			queue.put(newSong);
			
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
			JSONArray queue = state.getJSONArray("queue");
			Log.i(TAG, "queue: " + queue.toString());
			
			String list = "";
			SilentDiscoPlayer player = mBoundPlayer;
			for(int i = 0; i < queue.length(); i++){
				JSONObject song = queue.optJSONObject(i);
				if(song != null && song.optString("title") != null) {
					list += song.optString("title") + "\n";
					if(player != null)
						player.enqueueSong(song.optInt("id"), song.optString("songUrl"), song.optLong("startTime"));
				}
			} 
			status = list;
		} catch (JSONException e) {
			e.printStackTrace();
		}
        
    	long delta = -1;
 
        //DbIdentity user = mMultifeed.getUser(mMultifeed.getGlobalMemberCursor());
//        if (state.optBoolean("play")){//mMultifeed.isMyTurn()) {
//            status = "Playing at offset:" + state.optLong("offset") + " and delta: " + delta;
//        } else {
//            status = "Paused.";
//        }

        ((TextView)findViewById(R.id.status)).setText(status);

        
    }
    
	private long getRealTime() {
		NTPSyncService service = mBoundService;

		if (service == null) 
			return -1;
		Log.i(TAG, "returning time: " + System.currentTimeMillis() + service.getOffset());

		return System.currentTimeMillis() + service.getOffset();
	}


    private View.OnClickListener mPlayListener = new View.OnClickListener() {
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
	
	private View.OnClickListener mPauseListener = new View.OnClickListener() {
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
        	o.put("play", false);
        	o.put("offset", 0);
        	JSONArray queue = new JSONArray();
        	o.put("queue", queue);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to set play", e);
        }
        return o;
    };
}