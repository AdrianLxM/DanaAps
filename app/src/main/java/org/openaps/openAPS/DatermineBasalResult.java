package org.openaps.openAPS;


import com.eclipsesource.v8.V8Object;

import org.json.JSONObject;

public class DatermineBasalResult {

    public JSONObject json = new JSONObject();
    public final String reason;
    public final double rate;
    public final double eventualBG;
    public final double snoozeBG;
    public final int duration;
    public final String mealAssist;

    public DatermineBasalResult(V8Object result, JSONObject j) {
        json = j;
        reason = result.getString("reason");
        eventualBG = result.getDouble("eventualBG");
        snoozeBG = result.getDouble("snoozeBG");
        if(result.contains("rate")) {
            rate = result.getDouble("rate");
        } else {
            rate = -1;
        }
        if(result.contains("duration")) {
            duration = result.getInteger("duration");
        } else {
            duration = -1;
        }
        if(result.contains("mealAssist")) {
            mealAssist = result.getString("mealAssist");
        } else mealAssist = "";

        result.release();
    }
}
