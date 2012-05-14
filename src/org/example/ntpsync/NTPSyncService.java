/**
 * 
 */
package org.example.ntpsync;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * @author kenny
 * 
 */
public class NTPSyncService extends Service {
	private static final String TAG = "NtpSyncService";

	private static final String DEFAULT_NTP_POOL_HOST = "pool.ntp.org";

	private HandlerThread mThread;

	private NtpHandler mHandler;

	private final IBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder {
		public NTPSyncService getService() {
			return NTPSyncService.this;
		}
	}

	private static class NtpHandler extends Handler {
		private static final int INITIAL_SETUP = 1;

		private static final int SYNC_TIME = 2;

		private boolean mIsInitialized;

		private InetAddress mHost;

		private NTPUDPClient mClient;

		private long mOffset;

		public NtpHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case INITIAL_SETUP:
				if (!mIsInitialized) {
					mClient = new NTPUDPClient();
					mClient.setDefaultTimeout(500000);

					try {
						mHost = InetAddress.getByName(DEFAULT_NTP_POOL_HOST);
						mIsInitialized = true;
						sendEmptyMessage(SYNC_TIME);
						Log.i(TAG, "Looked up NTP host " + DEFAULT_NTP_POOL_HOST + " at address "
								+ mHost.getHostAddress());
					} catch (UnknownHostException e) {
						Log.w(TAG, "Couldn't lookup NTP host " + DEFAULT_NTP_POOL_HOST
								+ "; will try again");
						sendEmptyMessageDelayed(INITIAL_SETUP, 10 * 1000);
					}
				}
				break;
			case SYNC_TIME:
				try {
					TimeInfo timeInfo = mClient.getTime(mHost, NTPUDPClient.DEFAULT_PORT);
					timeInfo.computeDetails();
					mOffset = timeInfo.getOffset();
				} catch (IOException e) {
					Log.w(TAG, "Could not get time info", e);
				}

				sendEmptyMessageDelayed(SYNC_TIME, 60 * 1000);
				break;
			}
		}

		public void startSync() {
			sendEmptyMessage(INITIAL_SETUP);
		}

		public long getOffset() {
			return mOffset;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		mThread = new HandlerThread(getClass().getSimpleName());
		mThread.start();

		mHandler = new NtpHandler(mThread.getLooper());

		mHandler.startSync();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		mThread.quit();
	}

	public long getOffset() {
		return mHandler.getOffset();
	}
}
