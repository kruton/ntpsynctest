package mobisocial.silentdisco;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import mobisocial.ntpsync.NTPSyncService;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class SilentDiscoPlayer extends Service {
	private final IBinder mBinder = new LocalBinder();
	private NTPSyncService mBoundService;

    private MediaPlayer mMediaPlayer;
    private LinkedList<Song> mQueue;
    
    private final String TAG = "SilentDiscoPlayer";
    
    private int lastid = -10;

	public class LocalBinder extends Binder {
		public SilentDiscoPlayer getService() {
			return SilentDiscoPlayer.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "binding player");
		return mBinder;
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
	
	@Override
	public void onCreate() {
		super.onCreate();
		mQueue = new LinkedList<Song>();

		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
			public void onCompletion(MediaPlayer mp) {
				mMediaPlayer.reset();
				playNextSong();
			}
		});
		mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
			public void onSeekComplete(MediaPlayer mp) {
				// TODO Auto-generated method stub
				return;
			}
		});
		
		Intent ntpI = new Intent(this, NTPSyncService.class);
		bindService(ntpI, mConnection, BIND_AUTO_CREATE);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		unbindService(mConnection);
	}
	
	public void enqueueSong(int id, String songUrl, long startTime) {
		if (id < lastid){
			Log.i(TAG, "tried to add < lastid: " + id);
			return;
		}
		else
			lastid = id;
		
		mQueue.addLast(new Song(songUrl, startTime));
		if(!mMediaPlayer.isPlaying()) {
			Log.d(TAG, "start playing!");
			play();
		}
		else
			Log.d(TAG, "already playing");
	}
	
	public void play() {
		Log.d(TAG, "play()");
		playNextSong();
	}
	
	public void pausePlay() {
		mMediaPlayer.pause();
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) { }
		mMediaPlayer.start();
	}
	
	public void stop() {
		mMediaPlayer.stop();
		mMediaPlayer.reset();
	}
	
	private void playNextSong() {
		Log.d(TAG, "nextSong()");
		if (mQueue.isEmpty())
			return;
		
		Song next = mQueue.poll();
		try {
			mMediaPlayer.setDataSource(next.songUrl);
        	mMediaPlayer.prepare();
        	delayedStart(next.startTime);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void delayedStart(long startTime) {
		Log.d(TAG, "delayedStart()");
		
		
    	long delta = getRealTime() - startTime;
    	long offset = 0; //state.optInt("offset", 0);
    	int seek = (int)offset+ (int)delta;
    	
    	Log.i(TAG, "should seek to: " + seek + ", starting instead");
    	
    	if(seek == 0) {
    		mMediaPlayer.start();
    	}
    	else if (seek < 0) {
    		Timer t = new Timer();
    		t.schedule(new StartTask(), -seek);
    		Log.i(TAG, "waited to start, real: " + getRealTime() + " / sched: " + startTime);
    	}
    	else {
    		Log.i(TAG, "need to seek");
    		mMediaPlayer.seekTo(seek);
    		mMediaPlayer.start();
    	}
		
	}
	
	private long getRealTime() {
		NTPSyncService service = mBoundService;

		if (service == null) 
			return -1;
		
		Log.i(TAG, "returning time: " + System.currentTimeMillis() + service.getOffset());

		return System.currentTimeMillis() + service.getOffset();
	}
	
	private class StartTask extends TimerTask {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			mMediaPlayer.start();
			
		}
		
	}
}

class Song {
	long startTime;
	String songUrl;

	public Song(String songUrl, long startTime){
		this.startTime = startTime;
		this.songUrl = songUrl;
	}
}