package info.nightscout.danaapp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.client.broadcasts.Intents;
import info.nightscout.danar.db.PumpStatus;
import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 27.02.2016.
 *
 {
     "device": "openaps://indy-e3",
     "pump": {
         "battery": {
             "status": "normal",
             "voltage": 1.39
         },
         "status": {
             "status": "normal",
             "timestamp": "2016-02-27T05:22:40.000Z",
             "bolusing": false,
             "suspended": false
         },
         "reservoir": 103.825,
         "clock": "2016-02-26T21:13:02-08:00"
     },
     "openaps": {
         "suggested": {
             "bg": 170,
             "temp": "absolute",
             "snoozeBG": 138,
             "timestamp": "2016-02-27T05:14:35.000Z",
             "reason": "Eventual BG 220>=120, temp 2 >~ req 1.65U/hr",
             "eventualBG": 220,
             "mealAssist": "On: Carbs: 55 Boluses: 2.7 Target: 100 Deviation: 50 BGI: -8.76",
             "tick": "+11"
         },
         "iob": {
             "netbasalinsulin": 0,
             "timestamp": "2016-02-27T05:14:35.000Z",
             "basaliob": 0.477,
             "activity": 0.0219,
             "bolussnooze": 0.979,
             "iob": 2.491
         },
         "enacted": {
             "requested": {
                 "duration": 30,
                 "rate": 2,
                 "temp": "absolute"
             },
             "bg": 154,
             "temp": "absolute",
             "snoozeBG": 152,
             "timestamp": "2016-02-27T05:04:55.000Z",
             "reason": "Eventual BG 200>=120, temp 1.375<2U/hr",
             "rate": 2,
             "eventualBG": 200,
             "recieved": true,
             "duration": 30,
             "mealAssist": "On: Carbs: 55 Boluses: 2.7 Target: 100 Deviation: 46 BGI: -6.52",
             "tick": "+8"
         }
     },
     "created_at": "2016-02-27T15:30:11.303Z"
 }
 *
 */

public class DeviceStatus {
    private static Logger log = LoggerFactory.getLogger(DeviceStatus.class);
    public static DeviceStatus deviceStatus;

    public static String device = null;
    public static JSONObject pump = null;
    public static JSONObject enacted = null;
    public static JSONObject suggested = null;
    public static JSONObject iob = null;
    public static String created_at = null;

    public static JSONObject lowsuspend = null;

    public static DeviceStatus getInstance() {
        if(deviceStatus == null) {
            deviceStatus = new DeviceStatus();
        }
        return deviceStatus;
    }

    public static JSONObject mongoRecord () {
        JSONObject record = new JSONObject();

        try {
            if (device != null) record.put("device" , device);
            if (pump != null) record.put("pump" , pump);
            if (suggested != null) {
                JSONObject openaps = new JSONObject();
                if (enacted != null) openaps.put("enacted", enacted);
                if (suggested != null) openaps.put("suggested", suggested);
                if (iob != null) openaps.put("iob", iob);
                record.put("openaps", openaps);
            }
            if (lowsuspend != null) record.put("lowsuspend" , lowsuspend);
            if (created_at != null) record.put("created_at" , created_at);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return record;
    }

    public void sendToNSClient() {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbAdd");
        bundle.putString("collection", "devicestatus");
        bundle.putString("data", mongoRecord().toString());
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        if (q.size() < 1) {
            log.error("DBADD No receivers");
        } else log.debug("DBADD dbAdd " + q.size() + " receivers " + mongoRecord().toString());
    }
}
