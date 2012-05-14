package mobisocial.silentdisco;

import java.util.List;

import mobisocial.socialkit.Obj;
import mobisocial.socialkit.User;
import mobisocial.socialkit.musubi.DbFeed;
import mobisocial.socialkit.musubi.DbIdentity;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.musubi.multiplayer.FeedRenderable;
import mobisocial.socialkit.musubi.multiplayer.TurnBasedApp;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

public abstract class MultiFeed {
	static final String CATEGORIY_MUSUBI_MENU = "musubi.intent.category.MENU";

	//static final String OBJ_MEMBERSHIP = "membership";
	static final String OBJ_STATE = "state";
	//static final String OBJ_MEMBER_CURSOR = "member_cursor";

	static final boolean DBG = true;
	static final String TAG = "musubi";

	static final int NO_OBJ = -1;


	/**
	 * AppState objects provide state updates to an application instance.
	 */
	static final String TYPE_APP_STATE = "appstate";
	//static final String TYPE_INTERRUPT_REQUEST = "interrupt";

	private final Musubi mMusubi;
	private final ContentObserver mObserver;
	private final DbObj mObjContext;
	private final DbFeed mDbFeed;
	private final String mLocalMember;
	private boolean mObservingUpdates;

	private JSONObject mLatestObj;
	//private String[] mMembers;
	//private int mLocalMemberIndex;
	//private int mGlobalMemberCursor;
	private int mLastObjCount = NO_OBJ;

	/**
	 * Prepares a new TurnBasedApp object that can be inserted into a feed.
	 * Once inserted, the object can be used to create a new TurnBasedApp
	 * instance via {@link TurnBasedApp#TurnBasedApp(DbObj)}.
	 */
	public static Obj newInstance(String type, /*List<DbIdentity> participants,*/ JSONObject initialState) {
		JSONObject json = new JSONObject();
//		JSONArray members = new JSONArray();
//		for (DbIdentity id : participants) {
//			members.put(id.getId());
//		}
		try {
			json.put(OBJ_STATE, initialState);
//			json.put(OBJ_MEMBERSHIP, members);
//			json.put(OBJ_MEMBER_CURSOR, 0);
			json.put(Obj.FIELD_RENDER_TYPE, Obj.RENDER_LATEST);
		} catch (JSONException e) {
			throw new IllegalArgumentException(e);
		}
		return new MemObj(type, json, null, 0);
	}

	public MultiFeed(Musubi musubi, DbObj objContext) {
		if (musubi == null || objContext == null) {
			throw new NullPointerException("ObjContext is null");
		}

		mMusubi = musubi;
		mObjContext = objContext;
		mDbFeed = mObjContext.getSubfeed();
		mObserver = new FeedObserver(new Handler(mMusubi.getContext().getMainLooper()));
		mLocalMember = mDbFeed.getLocalUser().getId();
	}

	DbObj fetchLatestState() {
		String[] projection = null;
		String selection = "type = ?";
		String[] args = new String[] { TYPE_APP_STATE };
		String sortOrder = DbObj.COL_INT_KEY + " desc LIMIT 1";
		Cursor cursor = mDbFeed.query(projection, selection, args, sortOrder);
		if (cursor != null && cursor.moveToFirst()) {
			try {
				return mMusubi.objForCursor(cursor);
			} finally {
				cursor.close();
			}
		}
		return null;
	}

	/**
	 * Often called in an activity's onResume method.
	 */
	public void enableStateUpdates() {
		Uri uri = mDbFeed.getUri();
		mMusubi.getContext().getContentResolver().registerContentObserver(uri, false, mObserver);
		mObserver.onChange(false); // keep getLatestState() synchronized
		mObservingUpdates = true;
	}

	/**
	 * Often called in an activity's onPause method.
	 */
	public void disableStateUpdates() {
		mMusubi.getContext().getContentResolver().unregisterContentObserver(mObserver);
		mObservingUpdates = false;
	}

	public User getLocalUser() {
		return mDbFeed.getLocalUser();
	}

	/**
	 * Returns a view suitable for display in a feed.
	 */
	protected abstract FeedRenderable getFeedView(JSONObject state);

//	private void setMembershipFromJson(JSONArray memberArr) {
//		mMembers = new String[memberArr.length()];
//		for (int i = 0; i < memberArr.length(); i++) {
//			mMembers[i] = memberArr.optString(i);
//			if (mMembers[i].equals(mLocalMember)) {
//				mLocalMemberIndex = i;
//			}
//		}
//	}

	/**
	 * Returns the index within the membership list that represents the
	 * local user.
	 */
//	public int getLocalMemberIndex() {
//		return mLocalMemberIndex;
//	}

	/**
	 * Returns a cursor within the membership list that points to
	 * the user with control of the state machine.
	 */
//	public int getGlobalMemberCursor() {
//		return mGlobalMemberCursor;
//	}

	public boolean postJSON(JSONObject state) {
		getLatestState(); // force update
		JSONObject out = new JSONObject();
		try {
			//mGlobalMemberCursor = nextPlayer; 
			//out.put(OBJ_MEMBER_CURSOR, mGlobalMemberCursor);
			//out.put(OBJ_MEMBERSHIP, members);
			out.put(OBJ_STATE, state);
			mLatestObj = state;

			postAppStateRenderable(out, getFeedView(state));
			//if (DBG) Log.d(TAG, "Sent cursor " + out.optInt(OBJ_MEMBER_CURSOR));
			return true;
		} catch (JSONException e) {
			Log.e(TAG, "Failed to update cursor.", e);
			return false;
		}
	}

	/**
	 * Updates the state machine with the user's move, passing control
	 * to nextPlayer. The state machine is only updated if it is the
	 * local user's turn.
	 * @return true if a turn was taken.
	 */
//	public boolean postJSON(JSONObject state) {
//		return postJSON(membersJsonArray(), state);
//	}

	/**
	 * Updates the state machine with the user's move, passing control to
	 * the next player in {@link #getMembers()}. The state machine
	 * is only updated if it is the local user's turn.
	 * @return true if a turn was taken.
	 */
	/*
	public boolean takeTurn(JSONObject state) {
		int next = (mGlobalMemberCursor + 1) % mMembers.length;
		return takeTurn(next, state);
	}*/

//    public JSONArray membersJsonArray() {
//        JSONArray r = new JSONArray();
//        for (String m : mMembers) {
//            r.put(m);
//        }
//        return r;
//    }
    
	/**
	 * Returns the latest application state.
	 */
	public JSONObject getLatestState() {
		if (!mObservingUpdates) {
			mObserver.onChange(false);
		}
		return mLatestObj;
	}

	public int getLastTurnNumber() {
		if (!mObservingUpdates) {
			mObserver.onChange(false);
		}
		return mLastObjCount;
	}

	/**
	 * Override this method to handle state updates to this turn-basd app.
	 */
	protected abstract void onStateUpdate(JSONObject json);

	/**
	 * Returns the array of member identifiers.
	 */
//	public String[] getMembers() {
//		return mMembers;
//	}

//	public DbIdentity getUser(int memberIndex) {
//		return mDbFeed.userForGlobalId(mMembers[memberIndex]);
//	}

	private void postAppStateRenderable(JSONObject state, FeedRenderable thumbnail) {
		try {
			JSONObject b = new JSONObject(state.toString());
			if (thumbnail != null) {
				thumbnail.addToJson(b);
			}
			mDbFeed.postObj(new MemObj(TYPE_APP_STATE, b, null, mLastObjCount + 1));
		} catch (JSONException e) {}
	}

	/**
	 * Monitors this turn-based app's subfeed for state updates and populates related
	 * state member variables.
	 */
	 class FeedObserver extends ContentObserver {
		 public FeedObserver(Handler handler) {
			 super(handler);
		 }

		 @Override
		 public void onChange(boolean selfChange) {
			 updateState(selfChange);
		 }

		 void updateState(boolean explicitRequest) {
			 DbObj obj = fetchLatestState();
			 if (DBG) Log.e(TAG, "fetched " + obj);
			 if (obj == null) {
				 obj = mObjContext;
			 }

			 JSONObject json = obj.getJson();
			 if (json == null) {
				 if (DBG) Log.e(TAG, "App state has no membership.");
				 //mMembers = null;
				 //mLocalMemberIndex = -1;
				 //mGlobalMemberCursor = 0;
				 return;
			 }
//			 try {
//				 setMembershipFromJson(json.getJSONArray(OBJ_MEMBERSHIP));
//			 } catch (JSONException e) {
//				 Log.e(TAG, "Error parsing membership", e);
//				 return;
//			 }

			 Integer turnTaken = obj.getIntKey();
			 if (turnTaken == null) {
				 if (DBG) Log.e(TAG, "no turn taken.");
				 return;
			 }

			 if (mLastObjCount != NO_OBJ && turnTaken <= mLastObjCount) {
				 if (DBG) Log.d(TAG, "Turn " + turnTaken + " is at/before known turn " + mLastObjCount);
				 return;
			 }
			 mLastObjCount = turnTaken;
			 JSONObject newState = obj.getJson();
			 if (newState == null || !newState.has(OBJ_STATE)) {
				 if (DBG) Log.w(TAG, "No state for update " + obj);
				 return;
			 }
			 //try {
				 mLatestObj = newState.optJSONObject(OBJ_STATE);
				 //mGlobalMemberCursor = newState.getInt(OBJ_MEMBER_CURSOR);
				 //if (DBG) Log.i(TAG, "Updated cursor to " + mGlobalMemberCursor);
			 //} catch (JSONException e) {
			//	 Log.e(TAG, "Failed to get member_cursor from " + newState);
			 //}

			 if (!explicitRequest) {
				 onStateUpdate(mLatestObj);
			 }
		 }

	 }
}

