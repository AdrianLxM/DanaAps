package info.nightscout.danar.event;

import org.openaps.openAPS.LowSuspendResult;

import java.util.Date;

public class LowSuspendStatus {
    private static LowSuspendStatus instance = null;

    public Date last_time = new Date(0,0,0);
    public LowSuspendResult lowSuspenResult = new LowSuspendResult();
    public Date time = new Date(0,0,0);
    public Integer bg = 0;
    public Integer delta = 0;
    public Double deltaAvg15m = 0d;
    public Double deltaAvg30m = 0d;
    public Double avg15m = 0d;
    public Double avg30m = 0d;
    //public String dataText = "";
    public String openApsText ="";

    public static LowSuspendStatus getInstance() {
        if(instance == null) {
            instance = new LowSuspendStatus();
        }
        return instance;
    }
}
