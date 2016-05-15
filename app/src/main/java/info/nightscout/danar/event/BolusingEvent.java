package info.nightscout.danar.event;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.client.broadcasts.Intents;
import info.nightscout.danaaps.MainApp;
import info.nightscout.danar.db.Treatment;

public class BolusingEvent {
    private static Logger log = LoggerFactory.getLogger(BolusingEvent.class);
    public String sStatus = "";
    public Treatment t = null;
    private static BolusingEvent bolusingEvent = null;

    public BolusingEvent(String status) {
        sStatus = status;
    }

    public BolusingEvent() {

    }
    public static BolusingEvent getInstance() {
        if(bolusingEvent == null) {
            bolusingEvent = new BolusingEvent();
        }
        return bolusingEvent;
    }

    public void sendToNSClient() {
        if (t == null || t._id == null || t._id.equals("")) return;
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbUpdate");
        bundle.putString("collection", "treatments");
        JSONObject data = new JSONObject();
        try {
            data.put("status", sStatus);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        bundle.putString("data", data.toString());
        bundle.putString("_id", t._id);
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        if (q.size() < 1) {
            log.error("DBUPDATE No receivers");
        } else log.debug("DBUPDATE dbUpdate " + q.size() + " receivers " + t._id + " " + data.toString());
    }

}
