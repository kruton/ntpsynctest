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
    private static final String TAG = "sd";

    private SDMultifeed mMultifeed;
    //private Button mTokenButton;
    private Button mPlayButton;
    private Button mPauseButton;
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
			mBoundPlayer.enqueueSong("test", 7);

		}

		public void onServiceDisconnected(ComponentName className) {
			Log.w(TAG, "time service disconnected");

			mBoundPlayer = null;
		}
	};


    //private final List<Button> mmSquares = new ArrayList<Button>();

    private static final String BLANK = "  ";

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
		Log.w(TAG, "creating silent disco");
		
		Button search = (Button) findViewById(R.id.search);
        search.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent myIntent = new Intent(view.getContext(), YoutubePickerActivity.class);
                startActivityForResult(myIntent, 0);
            }
        });


//        for (int i = 0; i < 9; i++) {
//            mmSquares.get(i).setOnClickListener(mBoardClickedListener);
//            mmSquares.get(i).setTag(i);
//        }
//        findViewById(R.id.clear).setOnClickListener(mClearAll);

        // Display the game's current state:
        render(mMultifeed.getLatestState());
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

    /**
     * Extracts the board state from the given json object and
     * renders it to screen.
     */
    private void render(JSONObject state) {
        //mTokenButton.setText(mMultifeed.getPlayerToken());
        //((ImageView)findViewById(R.id.image)).setImageBitmap(user.getPicture());

        // The game state is completely stored in the UI as the labels of our buttons:
//        JSONArray s = state.optJSONArray("s");
//        for (int i = 0; i < 9; i++) {
//            mmSquares.get(i).setText(s.optString(i));
//        }            
        
        
    	long delta = -1;
        if(state.optBoolean("play")){
        	if(mMediaPlayer == null){
//        		mMediaPlayer = MediaPlayer.create(SilentDiscoActivity.this, R.raw.arn);
        		try {
            		mMediaPlayer = MediaPlayer.create(SilentDiscoActivity.this, 
            				Uri.parse("rtsp://v3.cache7.c.youtube.com/CjYLENy73wIaLQlNya7a5kKkDhMYDSANFEIJbXYtZ29vZ2xlSARSBWluZGV4YKvnz7_Go62-Tww=/0/0/0/video.3gp"));
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
        		if (mMediaPlayer == null) 
        			mMediaPlayer = MediaPlayer.create(SilentDiscoActivity.this, R.raw.arn);
        		mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
        			public void onCompletion(MediaPlayer mp) {
        				// TODO Auto-generated method stub
        				mp.release();
        				mMediaPlayer = null;
        			}
        		});
        	}
			
        	NTPSyncService service = mBoundService;
			if (service == null) {
				return;
			}
			long realTime = System.currentTimeMillis() + service.getOffset();
			long startTime = state.optLong("time", realTime);
        	delta = realTime - startTime;
        	long offset = state.optInt("offset", 0);
        	int seek = (int)offset+ (int)delta;
        	
        	mMediaPlayer.seekTo(seek);
    		mMediaPlayer.start();
        }
        else {
        	if(mMediaPlayer != null)
        		mMediaPlayer.pause();
        }

        String status;
        //DbIdentity user = mMultifeed.getUser(mMultifeed.getGlobalMemberCursor());
        if (state.optBoolean("play")){//mMultifeed.isMyTurn()) {
            status = "Playing at offset:" + state.optLong("offset") + " and delta: " + delta;
        } else {
            status = "Paused.";
        }

        ((TextView)findViewById(R.id.status)).setText(status);

        
    }

    /**
     * Computes the local view of the application state
     * and returns it as a JSON object.
     */
    private JSONObject getState() {
        JSONObject o = new JSONObject();
        JSONArray s = new JSONArray();
        try {
//            for (Button b : mmSquares) {
//                s.put(b.getText());
//            }
//            o.put("s", s);
            o.put("play", true);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to get board state", e);
        }
        return o;
    }

    private View.OnClickListener mPlayListener = new View.OnClickListener() {
		public void onClick(View arg0) {
			
			NTPSyncService service = mBoundService;
			if (service == null) {
				return;
			}
			long startTime = System.currentTimeMillis() + service.getOffset();

			// TODO Auto-generated method stub
	        JSONObject o = new JSONObject();
	        try {
	        	o.put("play", true);
	        	
	        	if(mMediaPlayer != null)
	        		o.put("offset", mMediaPlayer.getCurrentPosition());
	        	else
	        		o.put("offset", 0);
	        	
	        	o.put("time", startTime);
	        } catch (JSONException e) {
	            Log.w(TAG, "Failed to set play", e);
	        }
            mMultifeed.postJSON(o);			
		}
	};
	private View.OnClickListener mPauseListener = new View.OnClickListener() {
		public void onClick(View arg0) {
			// TODO Auto-generated method stub
	        JSONObject o = new JSONObject();
	        try {
	        	o.put("play", false);
	        	o.put("offset", 0);
	        } catch (JSONException e) {
	            Log.w(TAG, "Failed to set play", e);
	        }
            mMultifeed.postJSON(o);			
		}
	};
	
//    private View.OnClickListener mBoardClickedListener = new View.OnClickListener() {
//        @Override
//        public void onClick(View v) {
//        	MediaPlayer mp = MediaPlayer.create(SilentDiscoActivity.this, R.raw.tada);   
//            mp.start();
//            mp.setOnCompletionListener(new OnCompletionListener() {
//                public void onCompletion(MediaPlayer mp) {
//                    // TODO Auto-generated method stub
//                    mp.release();
//                }
//            });
//        	
//        	
//            // This call isn't necessary, since takeTurn() checks internally. But it doesn't hurt.
//            //if (!mMultifeed.isMyTurn()) {
//            //    return;
//            //}
//
//            Button square = mmSquares.get((Integer)v.getTag());
//            if (!square.getText().equals(BLANK)) {
//                return;
//            }
//            square.setText(mMultifeed.getPlayerToken());
//            mMultifeed.postJSON(getState());
//        }
//    };
//
//    private View.OnClickListener mClearAll = new View.OnClickListener() {
//        @Override
//        public void onClick(View arg0) {
//            mMultifeed.postJSON(getInitialState());
//        }
//    };

    private class SDMultifeed extends MultiFeed {
    	private String mToken = null;

    	public SDMultifeed(Musubi musubi, DbObj objContext) {
    		super(musubi, objContext);
    	}

    	public String getPlayerToken() {
    		// First player is X, second is O. getLocalMemberIndex() returns
    		// the player corresponding to this device:
    		if(mToken == null) {
    			mToken = "?"; //(getLocalMemberIndex() == 0) ? "X" : "O";
    		}
    		return mToken;
    	}

    	@Override
    	protected void onStateUpdate(JSONObject state) {
    		render(state);
    	}

    	@Override
    	protected FeedRenderable getFeedView(JSONObject state) {
//    		try {
    			//JSONArray squares = state.getJSONArray("s");
    			StringBuilder html = new StringBuilder("<html><head><style>")
    			.append("td { min-width:18px; }")
    			.append("table { padding:8px; border-collapse: collapse;}")
    			.append(".left { border-right:1px solid black; }")
    			.append(".right { border-left:1px solid black; }")
    			.append(".top { border-bottom:1px solid black; }")
    			.append(".bottom { border-top:1px solid black; }")
    			.append("</style></head>")
    			.append("<body><div>Hi");
    			html.append("</div></body>")
    			.append("</html>");
    			return FeedRenderable.fromHtml(html.toString());
//    		} catch (JSONException e) {
//    			Log.w(TAG, "Error getting renderable state");
//    			return FeedRenderable.fromText("[TicTacToe rendering error]");
//    		}
    	}
    }

    public static JSONObject getInitialState() {
//        JSONObject o = new JSONObject();
//        JSONArray s = new JSONArray();
//        try {
//        	s.put("play");
//        	s.put("pau");
//            for (int i = 2; i < 9; i++) {
//                s.put(BLANK);
//            }
//            o.put("s", s);
//        } catch (JSONException e) {
//            Log.w(TAG, "Failed to get board state", e);
//        }
        JSONObject o = new JSONObject();
        try {
        	o.put("play", false);
        	o.put("offset", 0);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to set play", e);
        }
        return o;
    };
}