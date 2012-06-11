package mobisocial.silentdisco;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

class JSONAdapter extends BaseAdapter implements ListAdapter {
    private final Activity activity;
    private JSONArray jsonArray;
    
    private final String TAG = "YTPicker_JSON";
    
    public JSONAdapter(Activity activity, JSONObject json) {
    	try {
			JSONObject data = (JSONObject) json.optJSONObject("data");
			if(data != null)
				jsonArray = data.getJSONArray("items");
		} catch (JSONException e) {
			Log.w(TAG, "JSON Exception reading data");
		}
    	
    	if(jsonArray == null)
    		jsonArray = new JSONArray();
    	
        this.activity = activity;
    }

    public int getCount() {
        return jsonArray.length();
    }

    public JSONObject getItem(int position) {
        return jsonArray.optJSONObject(position);
    }

    public long getItemId(int position) {
//        JSONObject jsonObject = getItem(position);
//        return jsonObject.optLong("id");
    	return position;
    }
 
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = activity.getLayoutInflater().inflate(R.layout.result, null);

        JSONObject jsonObject = getItem(position);  

        TextView title = (TextView) convertView.findViewById(R.id.title);
        TextView subtitle = (TextView) convertView.findViewById(R.id.subtitle);        

        title.setText(jsonObject.optString("title", "null"));        
        subtitle.setText(convertSecondsToTime(jsonObject.optInt("duration")) + " \t " + 
                         jsonObject.optString("viewCount") + " views, " + 
        				 jsonObject.optString("likeCount") + " likes");
        
        return convertView;
    }
    
    public static String convertSecondsToTime(int duration)
    {
    	int minutes = duration / 60;
        int seconds = duration % 60;
        String time = minutes + ":" + ((seconds < 10) ? "0" + seconds : seconds);
        return time;
    }
    
}