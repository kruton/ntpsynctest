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

//    private MediaPlayer mMediaPlayer;
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
	
	
	
	@Override
	public void onCreate() {
		super.onCreate();
		mQueue = new LinkedList<Song>();
//		mMediaPlayer = null;
		
//		Timer t = new Timer();
//		t.scheduleAtFixedRate(new RunningTask(), 1000, 2000);
		
		Intent ntpI = new Intent(this, NTPSyncService.class);
		bindService(ntpI, mConnection, BIND_AUTO_CREATE);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
//		if(mMediaPlayer != null) {
//			mMediaPlayer.release();
//			mMediaPlayer = null;
//		}
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
//		if(mMediaPlayer == null) { // || !mMediaPlayer.isPlaying()) {
//			Log.d(TAG, "added, start playing!");
//			play();
//		}
//		else
//			Log.d(TAG, "added, something else already playing");
		
	}
	
	public void play() {
		Log.d(TAG, "play()");
//		playNextSong();
	}
	
	public void pausePlay() {
//		if(mMediaPlayer == null) 
//			return;
//		
//		mMediaPlayer.pause();
//		try {
//			Thread.sleep(75);
//		} catch (InterruptedException e) { }
//		mMediaPlayer.start();
		try {
			mQueue.peek().mediaPlayer.pause();
			try {
				Thread.sleep(75);
			} catch (InterruptedException e) { }
			mQueue.peek().mediaPlayer.start();
		} catch (Exception e) {}
	}
	
	public int getPosition() {
//		if (mMediaPlayer == null)
		
		try {
			return mQueue.peek().mediaPlayer.getCurrentPosition();
		}
		catch (Exception e) {};
		return 0;
//		
//		return mMediaPlayer.getCurrentPosition();
	}
	
	public void stop() {
//		if(mMediaPlayer != null) {
//			mMediaPlayer.stop();
//			mMediaPlayer.release();
//			mMediaPlayer = null;
//		}
	}
	
	public void clearQueue() {
		//stop();
		Song s;
		while ((s = mQueue.peek()) != null) {
			s.mediaPlayer.stop();
			s.mediaPlayer.reset();
			s.cleanUp();
		}
	}
	
//	private void playNextSong() {
//		Log.d(TAG, "nextSong()");
//		if (mQueue.isEmpty()) {
//			stopSelf();
//			return;
//		}
//		
//		Song song = mQueue.peek();
//		instantiateMediaPlayer();
//    	
//	}
	
	
	
	private long getRealTime() {
		NTPSyncService service = mBoundService;

		if (service == null) 
			return -1;
		
//		Log.i(TAG, "returning time: " + System.currentTimeMillis() + service.getOffset());

		return System.currentTimeMillis() + service.getOffset();
	}
	
//	private class StartTask extends TimerTask {
//		@Override
//		public void run() {
//			mMediaPlayer.start();
//			
//		}
//	}
	
	class Song {
		long startTime;
		String songUrl;
		int duration;
		MediaPlayer mediaPlayer;
		
		final String TAG = "SilentDiscoPlayerSong";

		public Song(String songUrl, long startTime, int duration){
			this.startTime = startTime;
			this.songUrl = songUrl;
			this.duration = duration;
			
			instantiateMediaPlayer();
			prepareSong();
		}
		
		public void cleanUp() {
			mediaPlayer.release();
			mediaPlayer = null;	
			
			mQueue.remove(this);
		}
		
		private void play() {
			Thread t = new Thread() {
				@Override
                public void run() {
					delayedStart();
                }
			};
			t.start();
		}
		
		public void instantiateMediaPlayer(){
			mediaPlayer = new MediaPlayer();
			
			mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
				public void onCompletion(MediaPlayer mp) {
					Log.i(TAG, "Completed song playback");
					cleanUp();
				}
			});
			mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
				public void onSeekComplete(MediaPlayer mp) {
					Log.i(TAG, "seek complete!!!!");
					play();
				}
			});
			mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
				public void onPrepared(MediaPlayer mp) {
					Log.i(TAG, "prepare complete!!!!");
					play();
					
				}
			});
			mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
				public boolean onError(MediaPlayer mp, int what, int extra) {
					Log.w(TAG, "error with media player, restarting (state: " + what);
//					if(what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
						mediaPlayer.release();
						mediaPlayer = null;
						instantiateMediaPlayer();
						prepareSong();
//						playNextSong();
//					}
					return false;
				}
			});
		}
		
		
		public void prepareSong() {
			try {
				mediaPlayer.setDataSource(songUrl);
				mediaPlayer.prepareAsync();
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
			
//	    	long delta = getRealTime() - startTime;
//	    	long offset = 0; //state.optInt("offset", 0);
	    	int seek = (int) (getRealTime() - startTime);
	    	        // = (int)delta;//(int)offset+ (int)delta;
	    	    
	    	while(seek < 0) {
	    		try {
					Thread.sleep(-seek);
				} catch (InterruptedException e) {}
	    		seek = (int) (getRealTime() - startTime);

	    	} 
	    	
	    	if (seek < 50) {
	    		mediaPlayer.start();
	    	}
	    	else if (seek > duration * 1000 - 3000)
	    	{
	    		Log.i(TAG, "!!!! skipping song");
	    		cleanUp();
	    	}
	    	else {
	    		Log.i(TAG, "need to seek "+ (seek+3000));
	    		mediaPlayer.seekTo(seek + 3000); // seek plus buffer
	    	}
			
		}
	}
}

