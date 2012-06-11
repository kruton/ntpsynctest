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
    
    private int lastid = -1;

	public class LocalBinder extends Binder {
		public SilentDiscoPlayer getService() {
			return SilentDiscoPlayer.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "binding player");

		// make sure service stays running
        startService(new Intent(this, SilentDiscoPlayer.class));
		
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
	
	private void instantiateMediaPlayer(){
		if(mMediaPlayer != null) {
			mMediaPlayer.release();
		}
		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
			public void onCompletion(MediaPlayer mp) {
				Log.i(TAG, "Completed song playback");
				mQueue.poll(); // remove previous song from queue

//				mMediaPlayer.release();
//				mMediaPlayer = null;
				
				playNextSong();
			}
		});
		mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
			public void onSeekComplete(MediaPlayer mp) {
				Log.i(TAG, "seek complete!!!!");
				mMediaPlayer.start();
//				delayedStart();
			}
		});
//		mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//			public void onPrepared(MediaPlayer mp) {
//				delayedStart();
//			}
//		});
		mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
			public boolean onError(MediaPlayer mp, int what, int extra) {
				Log.w(TAG, "error with media player, restarting (state: " + what);
				if(what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
					mMediaPlayer.release();
					mMediaPlayer = null;
					playNextSong();
				}
				return false;
			}
		});

	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		mQueue = new LinkedList<Song>();
		mMediaPlayer = null;
		
		Timer t = new Timer();
		t.scheduleAtFixedRate(new RunningTask(), 1000, 2000);
		
		Intent ntpI = new Intent(this, NTPSyncService.class);
		bindService(ntpI, mConnection, BIND_AUTO_CREATE);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if(mMediaPlayer != null) {
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
		unbindService(mConnection);
	}
	
	public void enqueueSong(int id, String songUrl, long startTime, int duration) {
		if (id < lastid){
			Log.i(TAG, "tried to add < lastid: " + id);
			return;
		}
		else
			lastid = id;
		
		mQueue.addLast(new Song(songUrl, startTime, duration));
		if(mMediaPlayer == null) { // || !mMediaPlayer.isPlaying()) {
			Log.d(TAG, "added, start playing!");
			play();
		}
		else
			Log.d(TAG, "added, something else already playing");
	}
	
	public void play() {
		Log.d(TAG, "play()");
		playNextSong();
	}
	
	public void pausePlay() {
		if(mMediaPlayer == null) 
			return;
		
		mMediaPlayer.pause();
		try {
			Thread.sleep(75);
		} catch (InterruptedException e) { }
		mMediaPlayer.start();
	}
	
	public int getPosition() {
		if (mMediaPlayer == null)
			return 0;
		
		return mMediaPlayer.getCurrentPosition();
	}
	
	public void stop() {
		if(mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
	}
	
	public void clearQueue() {
		stop();
		mQueue.clear();
	}
	
	private void playNextSong() {
		Log.d(TAG, "nextSong()");
		if (mQueue.isEmpty()) {
			stopSelf();
			return;
		}
		
		Song song = mQueue.peek();
		instantiateMediaPlayer();
    	try {
    		mMediaPlayer.setDataSource(song.songUrl);
			mMediaPlayer.prepare();
			delayedStart();
    	} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void delayedStart() {
		Log.d(TAG, "delayedStart()");
		
		Song song = mQueue.peek();

    	long delta = getRealTime() - song.startTime;
//    	long offset = 0; //state.optInt("offset", 0);
    	int seek = (int)delta;//(int)offset+ (int)delta;
    	    	
    	if(seek == 0) {
    		mMediaPlayer.start();
    	}
    	else if (seek < 0) {
    		Timer t = new Timer();
    		t.schedule(new StartTask(), -seek);
    		Log.i(TAG, "waited to start for "+seek+"sec, real: " + getRealTime() + " / sched: " + song.startTime);
    	}
    	else if (seek > song.duration * 1000 - 3000)
    	{
    		Log.i(TAG, "!!!! skipping song");
    		mQueue.poll();
    		mMediaPlayer.release();
    		mMediaPlayer = null;
    		playNextSong();
    	}
    	else {
    		Log.i(TAG, "need to seek "+ (seek+3000));
    		if(seek > 5000)
    			Log.i(TAG, "seeking: " + seek + ", real time: " + getRealTime());
    		mMediaPlayer.seekTo(seek + 3000); // seek plus buffer
    	}
		
	}
	
	private long getRealTime() {
		NTPSyncService service = mBoundService;

		if (service == null) 
			return -1;
		
//		Log.i(TAG, "returning time: " + System.currentTimeMillis() + service.getOffset());

		return System.currentTimeMillis() + service.getOffset();
	}
	
	private class StartTask extends TimerTask {
		@Override
		public void run() {
			mMediaPlayer.start();
			
		}
	}
	private class RunningTask extends TimerTask {
		@Override
		public void run() {
			Log.i(TAG, "check to insure running");
			if (!mQueue.isEmpty() && mMediaPlayer == null) {
				play();
			}
			
		}
	}
}

class Song {
	long startTime;
	String songUrl;
	int duration;

	public Song(String songUrl, long startTime, int duration){
		this.startTime = startTime;
		this.songUrl = songUrl;
		this.duration = duration;
	}
}