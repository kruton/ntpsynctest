package mobisocial.ntpsync;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.widget.TextView;

public class NTPSyncActivity extends Activity {
	private static final String TAG = "NTPSyncActivity";

	// private NtpServer mServer;

	private NTPSyncService mBoundService;

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

			final long realTime = System.currentTimeMillis() + mBoundService.getOffset();
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
		}

		public void onServiceDisconnected(ComponentName className) {
			mBoundService = null;
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.main);

		mTimeDisplay = null;//(TextView) findViewById(R.id.time1);

		mFormat = SimpleDateFormat.getTimeInstance(DateFormat.FULL);
		// Button b = (Button) findViewById(R.id.button1);
		// b.setOnClickListener(new OnClickListener() {
		// @Override
		// public void onClick(View v) {
		// try {
		// mServer.start();
		// } catch (IOException e) {
		// Log.e(TAG, "Can't start server", e);
		// }
		// }
		// });
		// mServer = new MyNtpServer();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// mServer.stop();
	}

	@Override
	protected void onStart() {
		super.onStart();

		Intent i = new Intent(this, NTPSyncService.class);
		bindService(i, mConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();

		unbindService(mConnection);
	}
}