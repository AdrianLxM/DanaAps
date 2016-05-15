package info.nightscout.danar;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.*;
import android.preference.PreferenceManager;
import android.util.Log;

import android.widget.Toast;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.squareup.otto.Bus;

import info.nightscout.client.broadcasts.Intents;
import info.nightscout.client.data.NSProfile;
import info.nightscout.danaaps.MainApp;
import info.nightscout.danaaps.VirtualPump;
import info.nightscout.danaaps.tempBasal.Basal;
import info.nightscout.danar.alarm.ServiceAlarm;
import info.nightscout.danar.comm.*;
import info.nightscout.danar.db.TempBasal;
import info.nightscout.danar.db.Treatment;
import info.nightscout.danar.event.BolusingEvent;
import info.nightscout.danar.event.ConnectionStatusEvent;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.danar.event.StatusEvent;
import info.nightscout.utils.DateUtil;

public class DanaConnection {
    private static Logger log = LoggerFactory.getLogger(DanaConnection.class);

    SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
    public String devName = SP.getString("danar_bt_name", "");

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    private final Bus mBus;
    private SerialEngine mSerialEngine;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private BluetoothSocket mRfcommSocket;
    private BluetoothDevice mDevice;
    private boolean connectionEnabled = false;
    PowerManager.WakeLock mWakeLock;
    private Treatment bolusingTreatment = null;

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledDisconnection = null;


    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    public DanaConnection(BluetoothDevice bDevice, Bus bus) {
        MainApp.setDanaConnection(this);

        mHandlerThread = new HandlerThread(DanaConnection.class.getSimpleName());
        mHandlerThread.start();

        this.mHandler = new Handler(mHandlerThread.getLooper());

        getSelectedPump();
        this.mBus = bus;
        createRfCommSocket();

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DanaConnection");
    }

    private void createRfCommSocket() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (isVirtualPump()) {

        } else if(mBluetoothAdapter!=null) {
            Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();

            for (BluetoothDevice device : devices) {
                String dName = device.getName();
                if (devName.equals(dName)) {
                    device.getAddress();
                    mDevice = device;

                    try {
                        mRfcommSocket = mDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                    } catch (IOException e) {
                        log.error("err", e);
                    }

                    break;
                }
            }


            registerBTconnectionBroadcastReceiver();
        } else {
            Toast.makeText(MainApp.instance().getApplicationContext(), "No BT adapter", Toast.LENGTH_LONG).show();
        }
        if(mDevice==null && !isVirtualPump()) {
            Toast.makeText(MainApp.instance().getApplicationContext(), "No device found", Toast.LENGTH_LONG).show();
        }
    }

    private void registerBTconnectionBroadcastReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String action = intent.getAction();
                Log.d("ConnectionBroadcast ", "Device  " + action + " " + device.getName());//Device has disconnected
                if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    Log.d("ConnectionBroadcast", "Device has disconnected " + device.getName());//Device has disconnected
                    if(mDevice.getName().equals(device.getName())) {
                        if(mRfcommSocket!=null) {

                            try {mInputStream.close();} catch (Exception e)  {log.debug(e.getMessage());}
                            try {mOutputStream.close();} catch (Exception e) {log.debug(e.getMessage());}
                            try {mRfcommSocket.close(); } catch (Exception e) {log.debug(e.getMessage());}


                        }
                        connectionEnabled = false;
                        mBus.post(new ConnectionStatusEvent(false,false, 0));
                        //connectionCheckAsync();
//                        MainApp.setDanaConnection(null);
                    }
                }

            }
        };
        MainApp.instance().getApplicationContext().registerReceiver(receiver,new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED));
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    }

    public synchronized void connectIfNotConnected(String callerName) {
        if (isVirtualPump()) {
            mBus.post(new ConnectionStatusEvent(false, true, 0));
            return;
        }
//        log.debug("connectIfNotConnected caller:"+callerName);
        mWakeLock.acquire();
        long startTime = System.currentTimeMillis();
        short connectionAttemptCount = 0;
        if(!(isConnected())) {
            long timeToConnectTimeSoFar = 0;
            while (!(isConnected())) {
                timeToConnectTimeSoFar = (System.currentTimeMillis() - startTime) / 1000;
                mBus.post(new ConnectionStatusEvent(true,false, connectionAttemptCount));
                connectionCheck(callerName);
                log.debug("connectIfNotConnected waiting " + timeToConnectTimeSoFar + "s attempts:" + connectionAttemptCount + " caller:"+callerName);
                connectionAttemptCount++;

                if(timeToConnectTimeSoFar/60>15 || connectionAttemptCount >180) {
                    Intent alarmServiceIntent = new Intent(MainApp.instance().getApplicationContext(), ServiceAlarm.class);
                    alarmServiceIntent.putExtra("alarmText","Connection error");
                    MainApp.instance().getApplicationContext().startService(alarmServiceIntent);
                }
                waitMsec(1000);
            }
            log.debug("connectIfNotConnected took " + timeToConnectTimeSoFar + "s attempts:" + connectionAttemptCount);
        } else {
            mBus.post(new ConnectionStatusEvent(false, true, 0));
        }
        mWakeLock.release();
    }

    private synchronized void  connectionCheck(String callerName) {
        if(mDevice==null) {
            Toast.makeText(MainApp.instance().getApplicationContext(), "No device found", Toast.LENGTH_SHORT).show();
            return;
        }

        if(mRfcommSocket == null) {
            try {
                mRfcommSocket = mDevice.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException e) {
                log.error("err", e);
            }
            if(mRfcommSocket==null) {
                log.warn("connectionCheck() mRfcommSocket is null ");
                return;
            }
        }
        if( !mRfcommSocket.isConnected()) {
//            log.debug("not connected");
            try {
                mRfcommSocket.connect();
                log.debug( "connected");

                mOutputStream = mRfcommSocket.getOutputStream();
                mInputStream =  mRfcommSocket.getInputStream();
                if(mSerialEngine!=null) {
                    mSerialEngine.stopIt();
                };
                mSerialEngine = new SerialEngine(mInputStream,mOutputStream,mRfcommSocket);
                mBus.post(new ConnectionStatusEvent(false,true, 0));

            } catch (IOException e) {
                log.warn( "connectionCheck() ConnectionStatusEvent attempt failed: " + e.getMessage());
                mRfcommSocket = null;
                //connectionCheckAsync("connectionCheck retry");
            }
        }


        if(isConnected()) {
            mBus.post(new ConnectionStatusEvent(false,true, 0));
            pingStatus();
        }
    }

    public void scheduleDisconnection() {

        class DisconnectRunnable implements Runnable {
            public void run(){
                disconnect();
                scheduledDisconnection = null;
            };
        }

        // prepare task for execution in 5 sec
        // cancel waiting task to prevent sending multiple disconnections
        if (scheduledDisconnection != null)
            scheduledDisconnection.cancel(false);
        Runnable task = new DisconnectRunnable();
        scheduledDisconnection = worker.schedule(task, 3, TimeUnit.SECONDS);
    }

    public void disconnect() {
        if( mRfcommSocket.isConnected()) {
            try {
                mInputStream.close();
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
            try {
                mOutputStream.close();
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
            try {
                mRfcommSocket.close();
            } catch (Exception e) {
                log.debug(e.getMessage());
            }
            log.debug("disconnecting");
        } else {
            log.debug("already disconnected");
        }
    }

    public boolean isConnected() {
        if (isVirtualPump()) {
            return true;
        }
        return mRfcommSocket!=null && mRfcommSocket.isConnected();
    }

    private void pingKeepAlive() {
        try {
            StatusEvent statusEvent = StatusEvent.getInstance();
            if(new Date().getTime() - statusEvent.timeLastSync.getTime() > 240_000) {
                pingStatus();
            } else {
                mSerialEngine.sendMessage(new MsgDummy());
            }
        } catch (Exception e) {
            log.error("err", e);
        }

    }
    private void pingStatus() {
        try {
            StatusEvent statusEvent = StatusEvent.getInstance();
            if (isVirtualPump()) {
                VirtualPump vp = VirtualPump.getInstance();
                statusEvent.remainUnits = vp.remainUnits;
                statusEvent.remainBattery = vp.remainBattery;
                statusEvent.currentBasal = vp.nsProfile == null ? 0.2 : vp.nsProfile.getBasal(vp.nsProfile.minutesFromMidnight());
                statusEvent.last_bolus_amount = vp.last_bolus_amount;
                statusEvent.last_bolus_time = vp.last_bolus_time;
                statusEvent.time = new Date();
                statusEvent.tempBasalInProgress = vp.tempbasal != null;
                statusEvent.tempBasalRatio = vp.tempbasal != null ? vp.tempbasal.percent : -1;
                statusEvent.tempBasalRemainMin = vp.tempbasal != null ? vp.tempbasal.getRemainingMinutes() : 0;
                statusEvent.tempBasalStart = vp.tempbasal != null ? vp.tempbasal.timeStart : new Date(0,0,0);

            } else {
                MsgStatus statusMsg = new MsgStatus();
                MsgStatusBasic statusBasicMsg = new MsgStatusBasic();
                MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal();
                MsgStatusTime statusTimeMsg = new MsgStatusTime();
                MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended();

                mSerialEngine.sendMessage(statusMsg);
                mSerialEngine.sendMessage(statusBasicMsg);
                mSerialEngine.sendMessage(tempStatusMsg);
                mSerialEngine.sendMessage(statusTimeMsg);
                mSerialEngine.sendMessage(exStatusMsg);

                if (!statusMsg.received) {
                    mSerialEngine.sendMessage(statusMsg);
                }
                if (!statusBasicMsg.received) {
                    mSerialEngine.sendMessage(statusBasicMsg);
                }
                if (!tempStatusMsg.received) {
                    // Load of status of current basal rate failed, give one more try
                    mSerialEngine.sendMessage(tempStatusMsg);
                }
                if (!statusTimeMsg.received) {
                    mSerialEngine.sendMessage(statusTimeMsg);
                }
                if (!exStatusMsg.received) {
                    // Load of status of current extended bolus failed, give one more try
                    mSerialEngine.sendMessage(exStatusMsg);
                }

                // Check we have really current status of pump
                if (!statusMsg.received || !statusBasicMsg.received || !tempStatusMsg.received || !statusTimeMsg.received || !exStatusMsg.received) {
                    waitMsec(10 * 1000);
                    log.debug("pingStatus failed");
                    connectIfNotConnected("pingStatus fail");
                    pingStatus();
                    return;
                }
            }


            statusEvent.timeLastSync = statusEvent.time;

            if(statusEvent.tempBasalInProgress) {
                try {

                    Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();

                    TempBasal tempBasal = new TempBasal();
                    tempBasal.duration = statusEvent.tempBasalTotalSec / 60;
                    tempBasal.percent = statusEvent.tempBasalRatio;
                    tempBasal.timeStart = statusEvent.tempBasalStart;
                    tempBasal.timeEnd = null;
                    tempBasal.baseRatio = (int) (statusEvent.currentBasal*100);
                    tempBasal.tempRatio = (int) (statusEvent.currentBasal*100 * statusEvent.tempBasalRatio/100d);
                    tempBasal.isExtended = false;
                    log.debug("TempBasal in progress record "+tempBasal);
                    daoTempBasals.createOrUpdate(tempBasal);
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                try {
                    Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();
                    TempBasal tempBasalLast = getTempBasalLast(daoTempBasals, false);
                    if (tempBasalLast != null) {
                        log.debug("tempBasalLast " + tempBasalLast);
                        if (tempBasalLast.timeEnd == null || tempBasalLast.timeEnd.getTime() > new Date().getTime()) {
                            tempBasalLast.timeEnd = new Date();
                            if (tempBasalLast.timeEnd.getTime() > tempBasalLast.getPlannedTimeEnd().getTime()) {
                                tempBasalLast.timeEnd = tempBasalLast.getPlannedTimeEnd();
                            }
                            log.debug("tempBasalLast updated to " + tempBasalLast);
                            daoTempBasals.update(tempBasalLast);
                        }
                    }
                }catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            }

            if(statusEvent.statusBolusExtendedInProgress) {
                try {

                    Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();

                    TempBasal tempBasal = new TempBasal();
                    tempBasal.duration = statusEvent.statusBolusExtendedDurationInMinutes;
                    tempBasal.percent = (int) ((statusEvent.statusBolusExtendedAbsoluteRate + statusEvent.currentBasal) / statusEvent.currentBasal * 100);
                    tempBasal.timeStart = statusEvent.statusBolusExtendedStart;
                    tempBasal.timeEnd = null;
                    tempBasal.baseRatio = (int) (statusEvent.currentBasal*100);
                    tempBasal.tempRatio = (int) (statusEvent.statusBolusExtendedAbsoluteRate * 100 + statusEvent.currentBasal*100);
                    tempBasal.isExtended = true;
                    log.debug("TempBasal Extended in progress record "+tempBasal);
                    daoTempBasals.createOrUpdate(tempBasal);
                } catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                try {
                    Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();
                    TempBasal tempBasalLast = getTempBasalLast(daoTempBasals, true);
                    if (tempBasalLast != null) {
                        log.debug("tempBasalLast Extended " + tempBasalLast);
                        if (tempBasalLast.timeEnd == null || tempBasalLast.timeEnd.getTime() > new Date().getTime()) {
                            tempBasalLast.timeEnd = new Date();
                            if (tempBasalLast.timeEnd.getTime() > tempBasalLast.getPlannedTimeEnd().getTime()) {
                                tempBasalLast.timeEnd = tempBasalLast.getPlannedTimeEnd();
                            }
                            log.debug("tempBasalLast Extended updated to " + tempBasalLast);
                            daoTempBasals.update(tempBasalLast);
                        }
                    }
                }catch (SQLException e) {
                    log.error(e.getMessage(), e);
                }
            }

            synchronized (this) {
                this.notify();
            }
            mBus.post(statusEvent);

        } catch (Exception e) {
            log.error("err",e);
        }
    }

    public void tempBasal(int percent, int durationInHours) throws Exception {
        if (isVirtualPump()) {
            VirtualPump vp = VirtualPump.getInstance();
            vp.tempbasal = new TempBasal();
            vp.tempbasal.duration = durationInHours * 60;
            vp.tempbasal.percent = percent;
            vp.tempbasal.timeStart = new Date();
        } else {
           MsgTempBasalStart msg = new MsgTempBasalStart(percent, durationInHours);
            mSerialEngine.sendMessage(msg);
        }
        uploadTempBasalStart(percent, durationInHours * 60);
        pingStatus();
    }

    public void tempBasalOff(boolean upload) throws Exception {
        if (isVirtualPump()) {
            VirtualPump.getInstance().tempbasal = null;
        }

        StatusEvent statusEvent = StatusEvent.getInstance();
        if(statusEvent.tempBasalInProgress) {
            try {
                Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();

                Date timeStart = statusEvent.tempBasalStart;
                TempBasal tempBasal = new TempBasal();
                tempBasal.timeStart = timeStart;

                tempBasal = daoTempBasals.queryForSameId(tempBasal);
                if (tempBasal == null) {
                    log.warn("tempBasal.timeStart not found " + timeStart);
                    tempBasal = getTempBasalLast(daoTempBasals, false);
                    log.warn("tempBasal.timeStart found (hope a good one)" + tempBasal.timeStart);
                }
                tempBasal.timeEnd = new Date();
                daoTempBasals.update(tempBasal);
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
            }

            if (upload)
                uploadTempBasalEnd();
            if (!isVirtualPump()) {
                connectIfNotConnected("tempOff");
                MsgTempBasalStop msg = new MsgTempBasalStop();
                mSerialEngine.sendMessage(msg);
            }
        }

        pingStatus();
    }

    public void extendedOff(boolean upload)  {
        if (isVirtualPump()) {
            VirtualPump.getInstance().tempbasal = null;
        }

        StatusEvent statusEvent = StatusEvent.getInstance();
        if(statusEvent.statusBolusExtendedInProgress) {
            try {
                Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();

                Date timeStart = statusEvent.statusBolusExtendedStart;
                TempBasal tempBasal = new TempBasal();
                tempBasal.timeStart = timeStart;

                tempBasal = daoTempBasals.queryForSameId(tempBasal);
                if (tempBasal == null) {
                    log.warn("extended tempBasal.timeStart not found " + timeStart);
                    tempBasal = getTempBasalLast(daoTempBasals, false);
                    log.warn("extended tempBasal.timeStart found (hope a good one)" + tempBasal.timeStart);
                }
                tempBasal.timeEnd = new Date();
                daoTempBasals.update(tempBasal);
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
            }

            if (upload)
                uploadTempBasalEnd();
            if (!isVirtualPump()) {
                connectIfNotConnected("extendedOff");
                MsgExtendedBolusStop msg = new MsgExtendedBolusStop();
                mSerialEngine.sendMessage(msg);
            }
        }

        pingStatus();
    }

    private TempBasal getTempBasalLast(Dao<TempBasal, Long> daoTempBasals, boolean isExtended) throws SQLException {
        TempBasal tempBasal;QueryBuilder<TempBasal, Long> queryBuilder = daoTempBasals.queryBuilder();
        queryBuilder.orderBy("timeIndex",false);
        Where where = queryBuilder.where();
        where.eq("isExtended", isExtended);
        queryBuilder.limit(1l);
        PreparedQuery<TempBasal> preparedQuery = queryBuilder.prepare();
        tempBasal = daoTempBasals.queryForFirst(preparedQuery);

        if (tempBasal != null)
            log.info("tempBasal.timeStart found last in DB " + tempBasal.timeStart);

        return tempBasal;
    }

    public void stop() {
        try {mInputStream.close();} catch (Exception e)  {log.debug(e.getMessage());}
        try {mOutputStream.close();} catch (Exception e) {log.debug(e.getMessage());}
        try {mRfcommSocket.close();} catch (Exception e) {log.debug(e.getMessage());}
        if(mSerialEngine!=null) mSerialEngine.stopIt();
    }

    public Result setExtendedTempBasal(final Basal basal) {
        final SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        final Result result = new Result();
        final StatusEvent ev = StatusEvent.getInstance();
        final boolean useExtendedBoluses = SP.getBoolean("safety_useextended", false);

        log.debug("setExtendedTempBasal: " + basal.ratePercent + "% " + basal.rate + "u/h " + basal.duration + " minutes " + " useExtendedBoluses=" + useExtendedBoluses);

        final boolean doTempOff = basal.ratePercent == 100;
        final boolean doLowTemp = basal.ratePercent < 100;
        final boolean doHighTemp = basal.ratePercent > 100 && !useExtendedBoluses;
        final boolean doExtendedTemp = basal.ratePercent > 100 && useExtendedBoluses;

        if (isVirtualPump()) {
            VirtualPump vp = VirtualPump.getInstance();
            result.result = true;
            result.percent = basal.ratePercent;
            result.absolute = basal.rate;
            if (basal.ratePercent == 100) {
                vp.tempbasal = null;
                result.enacted = true;
            } else if (vp.tempbasal == null || vp.tempbasal.percent != basal.ratePercent) {
                vp.tempbasal = new TempBasal();
                vp.tempbasal.duration = 60;
                vp.tempbasal.percent = basal.ratePercent;
                vp.tempbasal.timeStart = new Date();
                result.enacted = true;
                result.duration = 60;
            } else {
                result.enacted = false;
                result.duration = vp.tempbasal.getRemainingMinutes();
            }

            return result;
        }

        Date now = new Date();
        if (now.getTime() - ev.timeLastSync.getTime() > 30 * 60 * 60 * 1000) {
            log.debug("lastsync: " + ev.timeLastSync + " diff: " + (now.getTime() - ev.timeLastSync.getTime()));
            connectIfNotConnected("settempsync");
            pingStatus();
        }

        if (doTempOff) {
            // If extended in progress
            if (ev.statusBolusExtendedInProgress && useExtendedBoluses) {
                result.enacted = true;
                connectIfNotConnected("extendedstop");
                MsgExtendedBolusStop msgStop = new MsgExtendedBolusStop();
                mSerialEngine.sendMessage(msgStop);
                if (msgStop.failed || !msgStop.received) {
                    result.result = false;
                    result.comment = "Failed to stop previous extended bolus";
                    return result;
                }
            }
            // If temp in progress
            if (ev.tempBasalInProgress) {
                result.enacted = true;
                connectIfNotConnected("tempstop");
                MsgTempBasalStop msgStop = new MsgTempBasalStop();
                mSerialEngine.sendMessage(msgStop);
                if (msgStop.failed || !msgStop.received) {
                    result.result = false;
                    result.comment = "Failed to stop previous temp basal";
                    return result;
                }
            }
            result.result = true;
            result.percent = 100;
            if (isConnected()) pingStatus();
            uploadTempBasalEnd();
            log.debug("setExtendedTempBasal: doTempOff OK");
            return result;
        }

        // Get max basal in settings
        Double maxBasal = Double.parseDouble(SP.getString("max_basal","1"));
        Integer maxBasalPct = 400;
        Integer maxBasalFromDaily = 3;
        // Check percentRate but absolute rate too, because we know real current basal in pump
        Double origRate = basal.rate;
        if (basal.rate > maxBasal) {
            basal.rate = maxBasal;
            basal.ratePercent = (int) (Math.floor(basal.rate / ev.currentBasal * 100 / 10) * 10);
            log.debug("setExtendedTempBasal: limiting by maxBasal to " + basal.rate + "U " + basal.ratePercent + "%");
        }
        if (basal.ratePercent > maxBasalPct) {
            basal.ratePercent = maxBasalPct;
            basal.rate = Math.floor(ev.currentBasal * basal.ratePercent / 100 * 100) / 100;
            log.debug("setExtendedTempBasal: limiting by maxBasalPct to " + basal.rate + "U " + basal.ratePercent + "%");
        }
        if (MainApp.getNSProfile() != null && basal.rate > MainApp.getNSProfile().getMaxDailyBasal() * maxBasalFromDaily) {
            basal.rate = MainApp.getNSProfile().getMaxDailyBasal() * maxBasalFromDaily;
            basal.ratePercent = (int) (Math.floor(basal.rate / ev.currentBasal * 100 / 10) * 10);
            log.debug("setExtendedTempBasal: limiting by maxBasalFromDaily to " + basal.rate + "U " + basal.ratePercent + "%");
        }

        log.debug("setExtendedTempBasal: MAX processed to:" + basal.ratePercent + "% " + basal.rate + "u/h " + basal.duration + " minutes");

        if (doLowTemp || doHighTemp) {
            if (basal.ratePercent > 200) {
                basal.ratePercent = 200;
                basal.rate = ev.currentBasal * 2;
            }
            // Check if some temp is already in progress
            if (ev.tempBasalInProgress) {
                // Correct basal already set ?
                if (ev.tempBasalRatio == basal.ratePercent) {
                    result.result = true;
                    result.percent = basal.ratePercent;
                    result.absolute = basal.rate;
                    result.enacted = false;
                    result.duration = ev.tempBasalRemainMin;
                    log.debug("setExtendedTempBasal: correct basal already set");
                    return result;
                } else {
                    connectIfNotConnected("tempstop");
                    MsgTempBasalStop msgStop = new MsgTempBasalStop();
                    mSerialEngine.sendMessage(msgStop);
                    log.debug("setExtendedTempBasal: stopping basal");
                    // Check for proper result
                    if (msgStop.failed || !msgStop.received) {
                        result.result = false;
                        result.comment = "Failed to stop previous temp basal";
                        pingStatus();
                        log.debug("setExtendedTempBasal: failed to stop previous basal");
                        return result;
                    }
                }
            }
            // Convert duration from minutes to hours
            Integer duration = (int) Math.ceil(basal.duration / 60.0);
            connectIfNotConnected("tempstart");
            MsgTempBasalStart msgTempStart = new MsgTempBasalStart(basal.ratePercent, duration);
            mSerialEngine.sendMessage(msgTempStart);
            log.debug("setExtendedTempBasal: setting temp "+ basal.ratePercent + "% for " + duration + " hours");
            if (msgTempStart.failed || !msgTempStart.received) {
                result.result = false;
                result.comment = "Failed to set temp basal";
                pingStatus();
                log.debug("setExtendedTempBasal: failed to set temp basal");
                return result;
            }
            result.result = true;
            result.percent = basal.ratePercent;
            result.absolute = basal.rate;
            result.enacted = true;
            result.duration = 60;
            uploadTempBasalStart(result.percent, result.duration);
            log.debug("setExtendedTempBasal: basal set ok");
            pingStatus();
            return result;
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (ev.tempBasalInProgress) {
                connectIfNotConnected("tempstop");
                MsgTempBasalStop msgStop = new MsgTempBasalStop();
                mSerialEngine.sendMessage(msgStop);
                log.debug("setExtendedTempBasal: stopping basal");
                // Check for proper result
                if (msgStop.failed || !msgStop.received) {
                    result.result = false;
                    result.comment = "Failed to stop previous temp basal";
                    pingStatus();
                    log.debug("setExtendedTempBasal: failed to stop previous basal");
                    return result;
                }
            }

            // Calculate # of halfHours from minutes
            Integer halfHours = (int) Math.floor(basal.duration/30);
            // We keep current basal running so need to sub current basal
            Double rate = basal.rate - ev.currentBasal;
            // What is current rate of extended bolusing in u/h?
            Double rateInProgress = ev.statusBolusExtendedInProgress ? ev.statusBolusExtendedPlannedAmount / ev.statusBolusExtendedDurationInMinutes * 60 : 0d;
            log.debug("setExtendedTempBasal: extended in progess: "+ ev.statusBolusExtendedInProgress + " amount: " + ev.statusBolusExtendedPlannedAmount +  "u duration: " + ev.statusBolusExtendedDurationInMinutes + "min");
            log.debug("setExtendedTempBasal: current rate: "+ ev.currentBasal + "u/h  rate to set: " + rate +  "u/h extended rate in progress: " + rateInProgress + "u/h");

            // Compare with extended rate in progress
            if (Math.abs(rateInProgress - rate) < 0.02D) { // Allow some rounding diff
                // correct extended already set
                result.result = true;
                result.percent = basal.ratePercent;
                result.absolute = basal.rate;
                result.enacted = false;
                result.duration = ev.statusBolusExtendedDurationInMinutes - ev.statusBolusExtendedDurationSoFarInMinutes;
                log.debug("setExtendedTempBasal: correct extended basal already set");
                return result;
            }

            // Now set new extended, no need to to stop previous (if running) because it's replaced
            connectIfNotConnected("extendedstart");
            MsgExtendedBolusStart msgStartExt = new MsgExtendedBolusStart(rate / 2 * halfHours, (byte) (halfHours & 0xFF));
            mSerialEngine.sendMessage(msgStartExt);
            log.debug("setExtendedTempBasal: setting extended: " + (rate / 2 * halfHours) + "u  halfhours: " + (halfHours & 0xFF));
            if (msgStartExt.failed || !msgStartExt.received) {
                result.result = false;
                result.comment = "Failed to set extended bolus";
                pingStatus();
                log.debug("setExtendedTempBasal: failed to set extended");
                return result;
            }
            result.result = true;
            result.percent = basal.ratePercent;
            result.absolute = basal.rate;
            result.enacted = true;
            result.duration = 30;
            uploadTempBasalStart(result.percent, result.duration);
            log.debug("setExtendedTempBasal: extended set ok");
            pingStatus();
            return result;
        }
        return result;
    }

/*
        public Result bolusFromHAPP(final double amount, final String _id) {
            log.debug("bolusFromHAPP: bolus start " + amount + " _id: " + _id);
            final SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            final StatusEvent ev = StatusEvent.getInstance();
            final Result result = new Result();
            final BolusingEvent bolusingEvent = BolusingEvent.getInstance();

            Thread bolus = new Thread() {
                public void run() {
                    connectIfNotConnected("bolus");
                    // Request last bolus status
                    MsgStatus msgStatus = new MsgStatus();
                    mSerialEngine.sendMessage(msgStatus);
                    if (!msgStatus.received) {
                        // If failed give on more try
                        mSerialEngine.sendMessage(msgStatus);
                    }
                    // Check if we have latest data
                    if (!msgStatus.received) {
                        log.debug("bolusFromHAPP: Failed to read bolus status");
                        result.result = false;
                        result.comment = "Failed to read bolus status";
                        return;
                    }
                    Integer maxBolusRate = SP.getInt("safety_maxbolusrate", 60);
                    Double minsFromLastBolus = ((new java.util.Date()).getTime() - ev.last_bolus_time.getTime()) / 1000d / 60;
                    if ( minsFromLastBolus < maxBolusRate) {
                        log.debug("bolusFromHAPP: Max bolus ratio exceeded maxBolusRate: " + maxBolusRate + " minsFromLastBolus: " + minsFromLastBolus);
                        result.result = false;
                        result.comment = "Max bolus ratio exceeded";
                        return;
                    }
                    MsgBolusStart msg = new MsgBolusStart(amount, _id);
                    MsgBolusProgress progress = new MsgBolusProgress(mBus, amount, _id);
                    MsgBolusStop stop = new MsgBolusStop(mBus, _id);

                    mSerialEngine.expectMessage(progress);
                    mSerialEngine.expectMessage(stop);

                    bolusingEvent.sStatus = "Starting";
                    mBus.post(bolusingEvent);

                    mSerialEngine.sendMessage(msg);
                    while (!stop.stopped) {
                        mSerialEngine.expectMessage(progress);
                    }

                    bolusingEvent.sStatus = "Delivered " + amount + "U";
                    mBus.post(bolusingEvent);

                    if (progress.progress != 0) {
                        log.debug("bolusFromHAPP: Failed to send bolus");
                        result.result = false;
                        result.comment = "Failed to send bolus";
                    } else {
                        log.debug("bolusFromHAPP: Result OK");
                        result.result = true;
                    }
                };
            };
            bolus.start();
            try {
                bolus.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            pingStatus();
            bolusingEvent.sStatus = "";
            mBus.post(bolusingEvent);
            return result;
        }
    */
    public void extendedBolus(final double amount, final byte durationInHalfHours) throws Exception {
        Thread temp = new Thread() {
            public void run() {
                MsgExtendedBolusStart msg = new MsgExtendedBolusStart(amount, durationInHalfHours);
                mSerialEngine.sendMessage(msg);

                pingStatus();
            }
        };
        temp.start();
    }

    public void extendedBolusStop() throws Exception {
        Thread temp = new Thread() {
            public void run() {
                MsgExtendedBolusStop msg = new MsgExtendedBolusStop();
                mSerialEngine.sendMessage(msg);

                pingStatus();
            }
        };
        temp.start();
    }

    public void bolus(Double amount, Treatment t)  {
        if (isVirtualPump()) {
            VirtualPump vp = VirtualPump.getInstance();
            vp.last_bolus_amount = amount;
            vp.last_bolus_time = new Date();
        } else {
            bolusingTreatment = t;
            MsgBolusStart msg = new MsgBolusStart(amount);
            MsgBolusProgress progress = new MsgBolusProgress(MainApp.bus(), amount, t);
            MsgBolusStop stop = new MsgBolusStop(MainApp.bus(), amount, t);

            mSerialEngine.expectMessage(progress);
            mSerialEngine.expectMessage(stop);

            mSerialEngine.sendMessage(msg);
            while (!stop.stopped) {
                waitMsec(50);
            }
        }
        bolusingTreatment = null;
        pingStatus();
    }

    public void bolusStop()  {
        Treatment lastBolusingTreatment = bolusingTreatment;
        log.debug("bolusStop >>>>> @ " + (bolusingTreatment == null ? "" : bolusingTreatment.insulin));
        if (isVirtualPump()) {
        } else {
            MsgBolusStop stop = new MsgBolusStop();
            stop.forced = true;
            mSerialEngine.sendMessage(stop);
            while (!stop.stopped) {
                mSerialEngine.sendMessage(stop);
                waitMsec(200);
            }
            // and update ns status to last amount
            waitMsec(60000);
            BolusingEvent be = BolusingEvent.getInstance();
            be.sStatus = "";
            mBus.post(be);
        }
    }

    public void carbsEntry(int amount) {
        if (isVirtualPump()) {
        } else {
            Calendar time = Calendar.getInstance();
            MsgCarbsEntry msg = new MsgCarbsEntry(time, amount);
            mSerialEngine.sendMessage(msg);
            //pingStatus();
        }
    }

    public void updateBasalsInPump() {
        if (isVirtualPump()) {
            VirtualPump.getInstance().nsProfile = MainApp.getNSProfile();
        } else {
            Thread setBasal = new Thread() {
                @Override
                public void run() {
                    SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
                    final String syncBasal = SP.getString("ns_sync_profile", "None");
                    if (syncBasal.equals("NS -> Pump") && MainApp.getNSProfile() != null) {
                        double[] basal = buildDanaRProfileRecord(MainApp.getNSProfile());
                        connectIfNotConnected("updateBasalsInPump");
                        MsgSetBasalProfile msgSet = new MsgSetBasalProfile((byte) 0, basal);
                        mSerialEngine.sendMessage(msgSet);
                        MsgSetActivateBasalProfile msgActivate = new MsgSetActivateBasalProfile((byte) 0);
                        mSerialEngine.sendMessage(msgActivate);
                        pingStatus();
                    }
                }
            };
            setBasal.start();
        }
    }

    public double[] buildDanaRProfileRecord (NSProfile nsProfile) {
        double [] record = new double[24];
        for (Integer hour = 0; hour < 24; hour ++) {
            double value = nsProfile.getBasal(hour * 60 * 60);
            log.debug("NS basal value for " + hour + ":00 is " + value);
            record[hour] = value;
        }
        return record;
    }

    public static void uploadTempBasalStart(int percent, double durationInMinutes) {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", "Temp Basal");
            data.put("duration", durationInMinutes);
            data.put("percent", percent - 100);
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", "DanaAps");
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            if (q.size() < 1) {
                log.error("DBADD No receivers");
            } else log.debug("DBADD dbAdd " + q.size() + " receivers " + data.toString());
        } catch (JSONException e) {
        }
    }

    public static void uploadTempBasalEnd() {
        try {
            Context context = MainApp.instance().getApplicationContext();
            JSONObject data = new JSONObject();
            data.put("eventType", "Temp Basal");
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", "DanaAps");
            Bundle bundle = new Bundle();
            bundle.putString("action", "dbAdd");
            bundle.putString("collection", "treatments");
            bundle.putString("data", data.toString());
            Intent intent = new Intent(Intents.ACTION_DATABASE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            List<ResolveInfo> q = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            if (q.size() < 1) {
                log.error("DBADD No receivers");
            } else log.debug("DBADD dbAdd " + q.size() + " receivers " + data.toString());
        } catch (JSONException e) {
        }
    }

    private void getSelectedPump(){
        devName = SP.getString("danar_bt_name", "VirtualPump");
        StatusEvent.getInstance().connectedPump = devName;
        MainApp.bus().post(StatusEvent.getInstance());
    }

    public void preferenceChange() {
        getSelectedPump();
        createRfCommSocket();
    }

    public boolean isVirtualPump() {
        return devName.equals("VirtualPump");
    }

    public void waitMsec(long msecs) {
        try {
            Thread.sleep(msecs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
