package info.nightscout.danaaps.services;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import org.json.JSONObject;
import org.openaps.openAPS.DatermineBasalResult;
import org.openaps.openAPS.DetermineBasalAdapterJS;
import org.openaps.openAPS.IobParam;
import org.openaps.openAPS.LowSuspendResult;
import org.openaps.openAPS.ScriptReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import info.nightscout.danaaps.AppExpire;
import info.nightscout.danaaps.DeviceStatus;
import info.nightscout.danaaps.calc.CarbCalc;
import info.nightscout.danaaps.calc.Iob;
import info.nightscout.danaaps.tempBasal.Basal;
import info.nightscout.danar.DanaConnection;
import info.nightscout.danaaps.MainActivity;
import info.nightscout.danaaps.MainApp;
import info.nightscout.danaaps.ReceiverBG;
import info.nightscout.danar.Result;
import info.nightscout.danar.ServiceConnection;
import info.nightscout.danar.db.Treatment;
import info.nightscout.danar.event.LowSuspendStatus;
import info.nightscout.danar.event.StatusEvent;
import info.nightscout.utils.DateUtil;

public class ServiceBG extends android.app.IntentService {
    private static Logger log = LoggerFactory.getLogger(ServiceBG.class);

    public static final String ACTION_NEW_DATA = "danaR.action.BG_DATA";

    public static final DecimalFormat numberFormat = new DecimalFormat("0.00");
    SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");

    boolean weRequestStartOfConnection = false;

//    DetermineBasalAdapterJS determineBasalAdapterJS;

    public ServiceBG() {
        super("ServiceBG");
    }
    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null)
            return;

        if (AppExpire.isExpired(MainApp.instance().getApplicationContext()))
            return;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (!preferences.getBoolean("masterSwitch", false)) {
            log.debug("Master switch off. Quitting ...");
            return;
        }

        try {

            Bundle bundle = intent.getExtras();
            Date time =  new Date(bundle.getLong("time"));
            int glucoseValue = bundle.getInt("value");
            int delta = bundle.getInt("delta");
            double avgdelta = bundle.getDouble("avgdelta");

            String msgReceived = "time: " + dateFormat.format(time)
                    + " bg: " + glucoseValue
                    + " dlta: " + delta
                    + " avgdlta: " + numberFormat.format(avgdelta);
            log.debug("onHandleIntent " + msgReceived);

            // Store last BG for bolus wizard
            if (MainActivity.lastBGTime == null || time.getTime() > MainActivity.lastBGTime.getTime()) {
                MainActivity.lastBG = glucoseValue;
                MainActivity.lastBGTime = time;
            }

            LowSuspendStatus lowSuspendStatus = LowSuspendStatus.getInstance();
            lowSuspendStatus.bg = glucoseValue;
            lowSuspendStatus.time = time;
            lowSuspendStatus.delta = delta;
            lowSuspendStatus.avgdelta = avgdelta;

            StatusEvent statusEvent = StatusEvent.getInstance();
            DanaConnection danaConnection = getDanaConnection();

            boolean openAPSenabled = preferences.getBoolean("OpenAPSenabled", false);
            DatermineBasalResult determineBasalResult = null;

            double percent = 100;
            double rate = statusEvent.currentBasal;

            // IOB calculation
            List<Treatment> treatmentList = MainActivity.loadTreatments();
            Iob bolusIobOpenAPS = MainActivity.getIobOpenAPSFromTreatments(treatmentList);
            Iob bolusIob = MainActivity.getIobFromTreatments(treatmentList);
            Iob basalIob = MainActivity.getIobFromTempBasals(MainActivity.loadTempBasalsDB());
            CarbCalc.Meal mealdata = MainActivity.getCarbsFromTreatments(treatmentList);

            //broadcastIob(bolusIobOpenAPS, bolusIob, basalIob);
            basalIob.plus(bolusIobOpenAPS);
            IobParam iobParam = new IobParam(basalIob.iobContrib, basalIob.activityContrib, bolusIobOpenAPS.iobContrib);
            //log.debug("IOB prepared: " + iobParam.json().toString());

            LowSuspendResult lowSuspendResult = lowSuspend(glucoseValue, avgdelta);
            boolean lowSuspendEnabled = preferences.getBoolean("LowSuspendEnabled", false);

            boolean performingLowSuspend = false;
            boolean performingOpenAps = false;

            // Create new devicestatus record for upload to NS
            DeviceStatus deviceStatus = DeviceStatus.getInstance();
            deviceStatus.device = "openaps://" + danaConnection.devName;
            deviceStatus.pump = statusEvent.getJSONStatus();
            deviceStatus.iob = new JSONObject();

            Iob fromBasal = MainActivity.getIobFromTempBasals(MainActivity.loadTempBasalsDB());
            deviceStatus.iob.put("iob", bolusIob.iobContrib + fromBasal.iobContrib);
            deviceStatus.iob.put("activity", bolusIob.activityContrib + fromBasal.iobContrib);
            deviceStatus.iob.put("basaliob", fromBasal.iobContrib);
            deviceStatus.iob.put("timestamp", DateUtil.toISOString(new Date()));
            deviceStatus.lowsuspend = null;
            deviceStatus.suggested = null;
            deviceStatus.created_at = DateUtil.toISOString(new Date());

            if (lowSuspendEnabled && lowSuspendResult.percent == 0) {
                // Low suspend activated
                deviceStatus.lowsuspend = lowSuspendResult.json();
                percent = 0;
                rate = 0;
                performingLowSuspend = true;
            } else if (MainApp.getNSProfile() == null) {
                log.debug("No profile received. Skipping OpenAPS");
                lowSuspendStatus.openApsText = "No profile";
            } else {
                determineBasalResult = openAps(glucoseValue, delta, avgdelta, statusEvent, lowSuspendStatus, iobParam, mealdata);
                if (openAPSenabled) {
                    if (determineBasalResult.rate == -1 || determineBasalResult.duration == 0) {
                        // do nothing
                    } else {
                        percent = determineBasalResult.rate / statusEvent.currentBasal * 10;
                        percent = Math.floor(percent) * 10;
                        rate = determineBasalResult.rate;
                        log.debug("openApsTempAbsolute:" + determineBasalResult.rate + " percent:" + percent);
                        if (percent < 0) {
                            percent = 0;
                            rate = 0;
                        }
                    }
                    determineBasalResult.json.put("timestamp", DateUtil.toISOString(new Date()));
                    deviceStatus.suggested = determineBasalResult.json;
                    performingOpenAps = true;
                }
            }

            if((new Date().getTime() -  statusEvent.timeLastSync.getTime())>60*60_000) {
                log.debug("Requesting status ...");
                danaConnection.connectIfNotConnected("ServiceBG 1hour");
            }

            Basal request = new Basal();
            request.rate = rate;
            request.ratePercent = (int) percent;
            request.duration = determineBasalResult.duration;
            log.debug("setExtendedTempBasal request" + request.log());
            final Result result = danaConnection.setExtendedTempBasal(request);
            log.debug("setExtendedTempBasal result" + result.log());

            if (result.result) {
                if (result.enacted) {
                    deviceStatus.enacted = new JSONObject(determineBasalResult.json.toString());
                    if (rate != -1)
                        deviceStatus.enacted.put("rate", rate);
                    if (performingOpenAps) {
                        deviceStatus.enacted.put("recieved", true);
                        if (rate != -1)
                            deviceStatus.enacted.put("duration", result.duration);
                        JSONObject requested = new JSONObject();
                        if (determineBasalResult.duration != -1) requested.put("duration", determineBasalResult.json.getInt("duration"));
                        if (determineBasalResult.rate != -1) requested.put("rate", determineBasalResult.json.getInt("rate"));
                        requested.put("temp", determineBasalResult.json.getString("temp"));
                        deviceStatus.enacted.put("requested", requested);
                    }
                } else {
                    log.info("No Action: Temp basal as requested: " + percent + "% rate: " + rate);
                }
            } else {
                log.debug("Setting of basal failed");
            }
/*
            int tempPercent = (int) percent;
            Double tempAbs = statusEvent.currentBasal * tempPercent / 100;

            if(tempPercent != 100 && tempPercent != statusEvent.tempBasalRatio) {
                if (performingOpenAps) {
                    deviceStatus.enacted = new JSONObject(determineBasalResult.json.toString());
                    deviceStatus.enacted.put("rate", tempAbs);
                }
                danaConnection.connectIfNotConnected("ServiceBG setTemp");
                try {
                    if(statusEvent.tempBasalRemainMin != 0) {
                        danaConnection.tempBasalOff(false);
                        danaConnection.tempBasal(tempPercent, 1);
                    } else {
                        danaConnection.tempBasal(tempPercent, 1);
                    }

                    if(statusEvent.tempBasalRatio!=tempPercent) {
                        log.error("Temp basal set failed");
                    } else {
                        log.info("Temp basal set "+statusEvent.tempBasalRatio);
                        if (performingOpenAps) {
                            deviceStatus.enacted.put("recieved", true);
                            deviceStatus.enacted.put("duration", 60);
                            JSONObject requested = new JSONObject();
                            requested.put("duration", determineBasalResult.json.getInt("duration"));
                            requested.put("rate", determineBasalResult.json.getInt("rate"));
                            requested.put("temp", determineBasalResult.json.getString("temp"));
                            deviceStatus.enacted.put("requested", requested);
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(),e);
                }
            } else if (tempPercent==100 && statusEvent.tempBasalRemainMin != 0
//                    && statusEvent.tempBasalRatio==0
                    ) {
                if (performingOpenAps) {
                    deviceStatus.enacted = new JSONObject(determineBasalResult.json.toString());
                    deviceStatus.enacted.put("rate", 0);
                    deviceStatus.enacted.put("duration", 0);
                }
                log.error("Temp basal off ");
                danaConnection.connectIfNotConnected("ServiceBG");
                danaConnection.tempBasalOff(true);
                if(statusEvent.tempBasalRemainMin != 0) {
                    log.error("Temp basal off failed");
                } else {
                    if (performingOpenAps) {
                        deviceStatus.enacted.put("recieved", true);
                        JSONObject requested = new JSONObject();
                        requested.put("duration", determineBasalResult.json.getInt("duration"));
                        requested.put("rate", determineBasalResult.json.getInt("rate"));
                        requested.put("temp", determineBasalResult.json.getString("temp"));
                        deviceStatus.enacted.put("requested", requested);
                    }
                }
            } else {
                log.info("No Action: Temp basal as requested: " + tempPercent + " tempBasalRatio:" + statusEvent.tempBasalRatio);
            }
*/
            deviceStatus.sendToNSClient();
            MainApp.bus().post(StatusEvent.getInstance());

        } catch (Throwable x){
            log.error(x.getMessage(),x);

        } finally {
            ReceiverBG.completeWakefulIntent(intent);
        }

    }

    private DatermineBasalResult openAps(int glucoseValue, int delta, double deltaAvg15min, StatusEvent status, LowSuspendStatus lowSuspendStatus, IobParam iobParam, CarbCalc.Meal mealdata) {
        DetermineBasalAdapterJS determineBasalAdapterJS = null;
        try {
            determineBasalAdapterJS = new DetermineBasalAdapterJS(new ScriptReader(MainApp.instance().getBaseContext()));
        } catch (IOException e) {
            log.error(e.getMessage(),e);
        }

        determineBasalAdapterJS.setGlucoseStatus(glucoseValue,delta,deltaAvg15min);
        //disabled taken from prefs !!!!!!!!!!!!!!! determineBasalAdapterJS.setProfile_Sens(Settings.insSensitivity);


        determineBasalAdapterJS.setIobData(iobParam);

        determineBasalAdapterJS.setMealData(mealdata);

        determineBasalAdapterJS.setProfile_CurrentBasal(status.currentBasal);
        //disabled taken from prefs !!!!!!!!!!!!!!! determineBasalAdapterJS.setProfile_MaxBasal(status.currentBasal*2);
        double tempBasalRatioAbsolute = status.tempBasalInProgress ? status.tempBasalRatio / 100.0d * status.currentBasal : 0;
        int tempBasalRemainMin = status.tempBasalRemainMin;
        if(tempBasalRemainMin>30) {
            tempBasalRemainMin = 30;
        }
        determineBasalAdapterJS.setCurrentTemp(tempBasalRemainMin, tempBasalRatioAbsolute);

        DatermineBasalResult determineBasalResult = determineBasalAdapterJS.invoke();

        lowSuspendStatus.openApsText = determineBasalResult.reason;

        determineBasalAdapterJS.release();
        determineBasalAdapterJS = null;

        return determineBasalResult;
    }
/*
    private void broadcastIob(IobCalc.Iob bolusIobOpenAPS, IobCalc.Iob bolusIob, IobCalc.Iob basalIob) {
        Intent intent = new Intent("danaR.action.IOB_DATA");

        Bundle bundle = new Bundle();

        bundle.putLong("time", new Date().getTime());
        bundle.putDouble("bolusIob", bolusIob.iobContrib);
        bundle.putDouble("bolusIobAPS", bolusIobOpenAPS.iobContrib);
        bundle.putDouble("bolusIobActivity", bolusIobOpenAPS.activityContrib);
        bundle.putDouble("basalIob", basalIob.iobContrib);
        bundle.putDouble("basalIobActivity", basalIob.activityContrib);

        intent.putExtras(bundle);
        MainApp.instance().getApplicationContext().sendBroadcast(intent);
    }
*/
    private LowSuspendResult lowSuspend(int glucoseValue, double deltaAvg15min) throws InterruptedException {
        LowSuspendResult lowSuspendResult = new LowSuspendResult();

        lowSuspendResult.lowProjected = (glucoseValue + 6.0*deltaAvg15min) <90;
        lowSuspendResult.low = glucoseValue < 90;

        LowSuspendStatus.getInstance().lowSuspenResult = lowSuspendResult;

        if(lowSuspendResult.low) {
            lowSuspendResult.percent = 0;
            lowSuspendResult.reason = "LowSuspend: Low: Temp basal 0%";
        } else if (lowSuspendResult.lowProjected) {
            lowSuspendResult.percent = 0;
            lowSuspendResult.reason = "LowSuspend: Low projected: Temp basal 0%";
        } else {
            lowSuspendResult.percent = 100;
            lowSuspendResult.reason = "LowSuspend: No action";
        }
        log.info(lowSuspendResult.reason);

        return lowSuspendResult;
    }


    private DanaConnection getDanaConnection() throws InterruptedException {
        DanaConnection danaConnection = MainApp.getDanaConnection();
        if(danaConnection==null) {
            weRequestStartOfConnection = true;
            getApplicationContext().startService(new Intent(getApplicationContext(), ServiceConnection.class));
            int counter = 0;
            do{
                danaConnection = MainApp.getDanaConnection();
                Thread.sleep(100);
                counter++;
            }while(danaConnection == null && counter < 10);
            if(danaConnection == null) {
                log.error("danaConnection == null");
                weRequestStartOfConnection = false;
            }
        }
        return danaConnection;
    }

}
