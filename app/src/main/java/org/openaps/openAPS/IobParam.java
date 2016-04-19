package org.openaps.openAPS;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 28.02.2016.
 */
public class IobParam {
    public Double iob;
    public Double activity;
    public Double bolussnooze;
    public Double basaliob;
    public Double netbasalinsulin;
    public Double hightempinsulin;

    public IobParam (Double iob, Double activity, Double bolussnooze) {
        this.iob = iob;
        this.activity = activity;
        this.bolussnooze = bolussnooze;
        this.basaliob = 0d;
        this.netbasalinsulin = 0d;
        this.hightempinsulin = 0d;
    }

    public IobParam (Double iob, Double activity, Double bolussnooze, Double basaliob, Double netbasalinsulin, Double hightempinsulin) {
        this.iob = iob;
        this.activity = activity;
        this.bolussnooze = bolussnooze;
        this.basaliob = basaliob;
        this.netbasalinsulin = netbasalinsulin;
        this.hightempinsulin = hightempinsulin;
    }

    public JSONObject json() {
        JSONObject json = new JSONObject();
        try {
            json.put("iob", iob);
            json.put("activity", activity);
            json.put("bolusIob", bolussnooze);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public JSONObject nsJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("iob", bolussnooze);
            json.put("basaliob", iob);
            json.put("activity", activity);
            json.put("timestamp", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
}
