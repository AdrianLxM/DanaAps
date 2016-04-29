package info.nightscout.danar.comm;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import com.squareup.otto.Bus;

import info.nightscout.client.broadcasts.Intents;
import info.nightscout.danaaps.MainApp;
import info.nightscout.danar.event.BolusingEvent;
import info.nightscout.utils.DateUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public class MsgOcclusion extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgOcclusion.class);

    public MsgOcclusion() {
        super("CMD_PUMPOWAY_SYSTEM_STATUS");
        SetCommand(SerialParam.CMD_PUMPOWAY);
        SetSubCommand(SerialParam.CMD_SUB_PUMPOWAY__SYSTEM_STATUS);
    }

    public MsgOcclusion(String cmdName) {
        super(cmdName);
    }

     public void handleMessage(byte[] bytes) {
        BolusingEvent bolusingEvent = BolusingEvent.getInstance();
        MsgBolusStop.stopped = true;
        bolusingEvent.sStatus = "Oclusion";
        MainApp.bus().post(bolusingEvent);
        sendToNSClient();
    }

    public void sendToNSClient() {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbAdd");
        bundle.putString("collection", "treatments");
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Announcement");
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("notes", "Occlusion detected");
            data.put("isAnnouncement", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        bundle.putString("data", data.toString());
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        if (q.size() < 1) {
            log.error("DBADD No receivers");
        } else log.debug("DBADD dbAdd " + q.size() + " receivers " + data.toString());
    }

}
