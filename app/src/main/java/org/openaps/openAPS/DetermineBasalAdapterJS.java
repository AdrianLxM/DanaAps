package org.openaps.openAPS;


import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import info.nightscout.client.data.NSProfile;
import info.nightscout.danaaps.MainApp;

public class DetermineBasalAdapterJS {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalAdapterJS.class);


    private final ScriptReader mScriptReader;
    V8 mV8rt ;
    private V8Object mProfile;
    private V8Object mGlucoseStatus;
    private V8Object mIobData;
    private V8Object mCurrentTemp;

    private final String PARAM_currentTemp = "currentTemp";
    private final String PARAM_iobData = "iobData";
    private final String PARAM_glucoseStatus = "glucose_status";
    private final String PARAM_profile = "profile";

    public DetermineBasalAdapterJS(ScriptReader scriptReader) throws IOException {
        mV8rt = V8.createV8Runtime();
        mScriptReader  = scriptReader;

        initProfile();
        initGlucoseStatus();
        initIobData();
        initCurrentTemp();

        initLogCallback();

        initProcessExitCallback();

        initModuleParent();

        loadScript();
    }

    public DatermineBasalResult invoke() {
        mV8rt.executeVoidScript(
                "console.error(\"determine_basal(\"+\n" +
                        "JSON.stringify("+PARAM_glucoseStatus+")+ \", \" +\n" +
                        "JSON.stringify("+PARAM_currentTemp+")+ \", \" + \n" +
                        "JSON.stringify("+PARAM_iobData+")+ \", \" +\n" +
                        "JSON.stringify("+PARAM_profile+")+ \") \");");
        mV8rt.executeVoidScript(
                "var rT = determine_basal(" +
                        PARAM_glucoseStatus + ", " +
                        PARAM_currentTemp+", " +
                        PARAM_iobData +", " +
                        PARAM_profile + ", " +
                        "undefined, "+
                        "setTempBasal"+
                        ");");


        String ret = mV8rt.executeStringScript("JSON.stringify(rT);");
        log.debug(ret);

        V8Object v8ObjectReuslt = mV8rt.getObject("rT");
//        {
//            V8Object result = v8ObjectReuslt;
//            log.debug(Arrays.toString(result.getKeys()));
//        }

        DatermineBasalResult result = null;
        try {
            result = new DatermineBasalResult(v8ObjectReuslt, new JSONObject(ret));
        } catch (JSONException e) {
            e.printStackTrace();
        }


        return result;
    }

    private void loadScript() throws IOException {
        mV8rt.executeVoidScript(
                readFile("oref0/lib/determine-basal/determine-basal.js"),
                "oref0/bin/oref0-determine-basal.js", 
                0);
        mV8rt.executeVoidScript("var determine_basal = module.exports;");
        
        mV8rt.executeVoidScript(
        		"var setTempBasal = function (rate, duration, profile, rT, offline) {" +
                    "rT.duration = duration;\n" +
                "    rT.rate = rate;" +
                    "return rT;" +
                "};",
        		"setTempBasal.js",
        		0
        		);
    }

    private void initModuleParent() {
        mV8rt.executeVoidScript("var module = {\"parent\":Boolean(1)};");
    }

    private void initProcessExitCallback() {
        JavaVoidCallback callbackProccessExit = new JavaVoidCallback() {
            @Override
            public void invoke(V8Object arg0, V8Array parameters) {
                if (parameters.length() > 0) {
                    Object arg1 = parameters.get(0);
                    log.error("ProccessExit " + arg1);
//					mV8rt.executeVoidScript("return \"\";");
                }
            }
        };
        mV8rt.registerJavaMethod(callbackProccessExit, "proccessExit");
        mV8rt.executeVoidScript("var process = {\"exit\": function () { proccessExit(); } };");
    }

    private void initLogCallback() {
        JavaVoidCallback callbackLog = new JavaVoidCallback() {
            @Override
            public void invoke(V8Object arg0, V8Array parameters) {
                if (parameters.length() > 0) {
                    Object arg1 = parameters.get(0);
                    log.debug("JSLOG " + arg1);


                }
            }
        };
        mV8rt.registerJavaMethod(callbackLog, "log");
        mV8rt.executeVoidScript("var console = {\"log\":log, \"error\":log};");
    }

    private void initCurrentTemp() {
        mCurrentTemp = new V8Object(mV8rt);
        setCurrentTemp(30.0, 0.1);
        mCurrentTemp.add("temp", "absolute");

        mV8rt.add(PARAM_currentTemp, mCurrentTemp);
    }

    public void setCurrentTemp(double tempBasalDurationInMinutes, double tempBasalRateAbsolute) {
        mCurrentTemp.add("duration", tempBasalDurationInMinutes);
        mCurrentTemp.add("rate", tempBasalRateAbsolute);
    }

    private void initIobData() {
        mIobData = new V8Object(mV8rt);
        setIobData(new IobParam(0.0, 0.0, 0.0, 0.0, 0.0, 0.0));

        mV8rt.add(PARAM_iobData, mIobData);
    }

    //public void setIobData(double netIob, double netActivity, double bolusIob) {
    public void setIobData(IobParam iobParam) {
        mIobData.add("iob", iobParam.iob); //netIob
        mIobData.add("activity", iobParam.activity); //netActivity
        mIobData.add("bolusiob", iobParam.bolussnooze); // backward compatibility with master
        mIobData.add("bolussnooze", iobParam.bolussnooze); //bolusIob
        mIobData.add("basaliob", iobParam.basaliob);
        mIobData.add("netbasalinsulin", iobParam.netbasalinsulin);
        mIobData.add("hightempinsulin", iobParam.hightempinsulin);
    }

    private void initGlucoseStatus() {
        mGlucoseStatus = new V8Object(mV8rt);

        setGlucoseStatus(0.0, 0.0, 0.0);

        mV8rt.add(PARAM_glucoseStatus, mGlucoseStatus);
    }

    public void setGlucoseStatus(double glocoseValue, double glucoseDelta, double glucoseAvgDelta15m) {
        mGlucoseStatus.add("delta", glucoseDelta);
        mGlucoseStatus.add("glucose", glocoseValue);
        mGlucoseStatus.add("avgdelta", glucoseAvgDelta15m);

    }

    private int toMgdl (Double value, String units) {
        if (units.equals("mg/dL")) return value.intValue();
        else return (int) (value * 18);
    }

    private void initProfile() {
        mProfile = new V8Object(mV8rt);
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String units = SP.getString("ns_units", "mg/dL");

        NSProfile nsProfile = MainApp.getNSProfile();
        String maxBgDefault = "180";
        String minBgDefault = "100";
        if (!units.equals("mg/dL")) {
            maxBgDefault = "10";
            minBgDefault = "5";
        }

        mProfile.add("max_iob", Double.parseDouble(SP.getString("max_iob", "1.5")));
        mProfile.add("carbs_hr", nsProfile.getCarbAbsorbtionRate());
        mProfile.add("dia", nsProfile.getDia());
        mProfile.add("type", "current");
        setProfile_CurrentBasal(nsProfile.getBasal(nsProfile.minutesFromMidnight()));
        mProfile.add("max_daily_basal", nsProfile.getMaxDailyBasal());
        mProfile.add("max_basal", Double.parseDouble(SP.getString("max_basal", "1")));
        mProfile.add("max_bg", toMgdl(Double.parseDouble(SP.getString("max_bg", maxBgDefault)), units));
        mProfile.add("min_bg", toMgdl(Double.parseDouble(SP.getString("min_bg", minBgDefault)), units));
        mProfile.add("carbratio", nsProfile.getIc(nsProfile.minutesFromMidnight()));
        mProfile.add("sens", toMgdl(nsProfile.getIsf(nsProfile.minutesFromMidnight()).doubleValue(), units) );
        mV8rt.add(PARAM_profile, mProfile);
    }

    public void setProfile_CurrentBasal(double currentBasal) {
        mProfile.add("current_basal", currentBasal);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        try {
            //release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void release() {
        mProfile.release();
        mCurrentTemp.release();
        mIobData.release();
        mGlucoseStatus.release();
        mV8rt.release();
    }

    public String readFile(String filename) throws IOException {
        byte[] bytes = mScriptReader.readFile(filename);
        String string = new String(bytes, "UTF-8");
        if(string.startsWith("#!/usr/bin/env node")) {
            string = string.substring(20);
        }
        return string;
    }

}
