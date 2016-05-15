package info.nightscout.danar.event;


import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import info.nightscout.danaaps.MainApp;
import info.nightscout.utils.DateUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;

public class StatusEvent {
    private static Logger log = LoggerFactory.getLogger(StatusEvent.class);

    private static StatusEvent statusEvent = null;

    public String connectedPump = "";

    public Double remainUnits = 0d;
    public int remainBattery = 0;

    public boolean tempBasalInProgress = false;
    public int tempBasalRatio = -1;
    public int tempBasalRemainMin = 0 ;
    public int tempBasalTotalSec;
    public int tempBasalAgoSecs;
    public Date tempBasalStart;

    public Date time = new Date(0,0,0);
    public Date timeLastSync = new Date(0,0,0);


    public double currentBasal = 0;
    public double last_bolus_amount = 0;
    public Date last_bolus_time = new Date(0,0,0);

    public boolean statusBolusExtendedInProgress = false;
    public int statusBolusExtendedDurationInMinutes = 0;
    public int statusBolusExtendedDurationSoFarInMinutes = 0;
    public double statusBolusExtendedPlannedAmount = 0d;
    public double statusBolusExtendedAbsoluteRate = 0d;
    public Date statusBolusExtendedStart = null;
    public int statusBolusExtendedRemainingMin = 0;

    public static StatusEvent getInstance() {
        if(statusEvent == null) {
            statusEvent = new StatusEvent();
        }
        statusEvent.updateTempBasalData();


        return statusEvent;
    }

    private void updateTempBasalData() {
        Date currentTime = new Date();
        long lastSyncMinAgo = (currentTime.getTime() - timeLastSync.getTime())/60_000;
        log.debug("updateTempBasalData: lastSyncMinAgo:"+lastSyncMinAgo +
                " tempBasalInProgress:"+tempBasalInProgress);

        if(lastSyncMinAgo > 1 && tempBasalInProgress) {
            if(statusEvent.tempBasalStart != null) {

                long tempBasalMinAgo = (currentTime.getTime() - tempBasalStart.getTime()) / 60_000;
                tempBasalRemainMin = /* duration */ (int) (60 - tempBasalMinAgo);
                log.debug("updateTempBasalData: tempBasalMinAgo:" + tempBasalMinAgo +
                        " tempBasalStart:" + tempBasalStart +
                        " tempBasalRemainMin:" + tempBasalRemainMin);

                if (tempBasalRemainMin <= 0) {
                    tempBasalRemainMin = 0;
                    tempBasalInProgress = false;
                    tempBasalRatio = -1;
                    log.debug("Temp basal expired");
                }
            }
        }

        if(lastSyncMinAgo > 1 && statusBolusExtendedInProgress) {
            if(statusEvent.statusBolusExtendedStart != null) {

                long extendedMinAgo = (currentTime.getTime() - statusBolusExtendedStart.getTime()) / 60_000;
                statusBolusExtendedRemainingMin = /* duration */ (int) (statusBolusExtendedDurationInMinutes - extendedMinAgo);
                log.debug("updateExtenedData: extendedMinAgo:" + extendedMinAgo +
                        " statusBolusExtendedStart:" + statusBolusExtendedStart +
                        " statusBolusExtendedRemainingMin:" + statusBolusExtendedRemainingMin);

                if (statusBolusExtendedRemainingMin <= 0) {
                    statusBolusExtendedRemainingMin = 0;
                    statusBolusExtendedInProgress = false;
                    log.debug("Exteneded bolus expired");
                }
            }
        }
    }

    @Override
    public String toString() {
        return "StatusEvent{" +
                "remainUnits=" + remainUnits +
                ", remainBattery=" + remainBattery +
                ", tempBasalInProgress=" + tempBasalInProgress +
                ", tempBasalRatio=" + tempBasalRatio +
                ", tempBasalRemainMin=" + tempBasalRemainMin +
                ", tempBasalTotalSec=" + tempBasalTotalSec +
                ", tempBasalAgoSecs=" + tempBasalAgoSecs +
                ", tempBasalStart=" + tempBasalStart +
                ", time=" + time +
                ", timeLastSync=" + timeLastSync +
                ", currentBasal=" + currentBasal +
                ", last_bolus_amount=" + last_bolus_amount +
                ", last_bolus_time=" + last_bolus_time +
                '}';
    }

    public JSONObject getJSONStatus(){
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        try {
            battery.put("percent", remainBattery);
            status.put("status", "normal");
            status.put("lastbolus", last_bolus_amount);
            status.put("lastbolustime", DateUtil.toISOString(last_bolus_time));
            if (tempBasalRatio != -1) {
                status.put("tempbasalpct", tempBasalRatio);
                if (tempBasalStart != null) status.put("tempbasalstart", DateUtil.toISOString(tempBasalStart));
                if (tempBasalRemainMin != 0) status.put("tempbasalremainmin", tempBasalRemainMin);
            }
            status.put("timestamp", DateUtil.toISOString(timeLastSync));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("reservoir", remainUnits.intValue());
            pump.put("clock", DateUtil.toISOString(time));
        } catch (JSONException e) {
        }
        return pump;
    }
}
