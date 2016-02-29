package org.openaps.openAPS;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by mike on 28.02.2016.
 */
public class IobParam {
    public Double iob;
    public Double activity;
    public Double bolusIob;

    public IobParam (Double iob, Double activity, Double bolusIob) {
        this.iob = iob;
        this.activity = activity;
        this.bolusIob = bolusIob;
    }

    public JSONObject json() {
        JSONObject json = new JSONObject();
        try {
            json.put("iob", iob);
            json.put("activity", activity);
            json.put("bolusIob", bolusIob);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
}
