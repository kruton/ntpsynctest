package mobisocial.silentdisco;

import java.util.LinkedList;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class SilentDiscoPlayer extends Service {
	private final IBinder mBinder = new LocalBinder();
    private MediaPlayer mMediaPlayer;
    private LinkedList<Song> mQueue;
    
    private final String TAG = "SilentDiscoPlayer";

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
	
	@Override
	public void onCreate() {
		super.onCreate();
		mMediaPlayer = new MediaPlayer();
		mQueue = new LinkedList<Song>();
	}
	
	public void enqueueSong(String songUrl, long startTime) {
		mQueue.addLast(new Song(songUrl, startTime));
		if(!mMediaPlayer.isPlaying()) {
			Log.d(TAG, "start playing!");
			play();
		}
		else
			Log.d(TAG, "already playing");
	}
	
	private void play() {
		Log.d(TAG, "play()");
	}
	
	private void nextSong() {
		Log.d(TAG, "nextSong()");
	}
	
	private void delayedStart() {
		Log.d(TAG, "delayedStart()");
	}
	
	// TODO onComplete handler

}

class Song {
	long startTime;
	String songUrl;

	public Song(String songUrl, long startTime){
		this.startTime = startTime;
		this.songUrl = songUrl;
	}
}