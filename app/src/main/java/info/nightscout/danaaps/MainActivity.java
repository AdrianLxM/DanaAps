package info.nightscout.danaaps;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeModule;
import com.squareup.otto.Subscribe;

import info.nightscout.danaaps.calc.IobCalc;
import info.nightscout.danaaps.carbs.TreatmentDialogFragment;
import info.nightscout.client.receivers.NSClientDataReceiver;
import info.nightscout.danar.DanaConnection;
import info.nightscout.danar.db.DatabaseHelper;
import info.nightscout.danar.db.TempBasal;
import info.nightscout.danar.db.Treatment;
import info.nightscout.danar.event.BolusingEvent;
import info.nightscout.danar.event.ConnectionStatusEvent;
import info.nightscout.danar.event.LowSuspendStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import info.nightscout.danar.event.PreferenceChange;
import info.nightscout.danar.event.StatusEvent;
import info.nightscout.danar.event.StopEvent;

public class MainActivity extends Activity
        implements TreatmentDialogFragment.Communicator {
    private static Logger log = LoggerFactory.getLogger(MainActivity.class);

    private static DecimalFormat format2digits = new DecimalFormat("00");
    private static DecimalFormat formatNumber1place = new DecimalFormat("0.00");
    private static DateFormat formatDateToJustTime = new SimpleDateFormat("HH:mm");

    TextView connectedPump;
    TextView uRemaining;
    TextView batteryStatus;
    TextView tempBasalRatio;
    TextView tempBasalAbs;
    TextView tempBasalRemain;
    TextView tempBasalClock;
    TextView currentBasal;

    TextView extendedBolusAmount;
    TextView extendedBolusSoFar;
    TextView lastBolusAmount;
    TextView bolusingStatus;
    TextView lastBolusTime;
    TextView lastCheck;

    TextView iob;
    TextView basalIob;
    TextView connection;

    LinearLayout linearTemp;
    LinearLayout linearBolusing;
    LinearLayout linearExtended;

    Button tbButton;
    Button bolusStopButton;
    Button carbsButton;
    NavigationView mNavigationView;

    DrawerLayout mDrawerLayout;

    static Handler mHandler;
    static private HandlerThread mHandlerThread;
    private TextView lowSuspendData;
    private TextView lowSuspendStatus;
    private TextView openApsStatus;
    private Switch switchOpenAPS;
    private Switch switchLowSuspend;

    private boolean bolusStopButtonPressed = false;

    private void initNavDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationView = (NavigationView) findViewById(R.id.navigation_view);
        mNavigationView.setNavigationItemSelectedListener(new OnNavigationItemSelectedListener());
    }

    @Override
    protected void onResume() {
        super.onResume();

        log.debug("onResume");

        updateTempBasalIOB();
        updateTreatmentsIOB();

        onStatusEvent(StatusEvent.getInstance());

        updateOpenAPSStatus();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.debug("onCreate");
        Iconify.with(new FontAwesomeModule());


        setContentView(R.layout.activity_main);

        initNavDrawer();

        iob = (TextView) findViewById(R.id.iob);
        basalIob = (TextView) findViewById(R.id.basal_iob);
        updateTreatmentsIOB();
        updateTempBasalIOB();

        if(mHandler==null) {
            mHandlerThread = new HandlerThread(MainActivity.class.getSimpleName() + "Handler");
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                setupAlarmManager();
                log.debug("setupAlarmManager");
            }
        });

        registerBus();
        getApplicationContext().sendBroadcast(new Intent("danaapp.danaapp.ReceiverKeepAlive.action.PING"));

        registerGUIElements();
    }

    private void registerGUIElements() {
        ((TextView) findViewById(R.id.lastconnclock)).setText("{fa-clock-o}");
        ((TextView) findViewById(R.id.lastbolusclock)).setText("{fa-clock-o}");
        uRemaining = (TextView) findViewById(R.id.uRemaining);
        batteryStatus = (TextView) findViewById(R.id.batteryStatus);
        tempBasalRatio = (TextView) findViewById(R.id.tempBasalRatio);
        //((ArrayAdapter)tempBasalRatio.getAdapter()).set
        connectedPump = (TextView) findViewById(R.id.connectedPump);
        tempBasalAbs = (TextView) findViewById(R.id.tempBasalAbs);
        currentBasal = (TextView) findViewById(R.id.currentBasal);
        tempBasalRemain = (TextView) findViewById(R.id.tempBasalRemain);
        tempBasalClock = (TextView) findViewById(R.id.tempBasalclock);

        linearTemp = (LinearLayout) findViewById(R.id.linearTemp);
        linearBolusing = (LinearLayout) findViewById(R.id.linearBolusing);
        linearExtended = (LinearLayout) findViewById(R.id.linearExtended);

        extendedBolusAmount =(TextView) findViewById(R.id.extendedBolusAmount);
        extendedBolusSoFar =(TextView) findViewById(R.id.extendedBolusSoFar);
        lastBolusAmount =   (TextView) findViewById(R.id.lastBolusAmount);
        bolusingStatus =    (TextView) findViewById(R.id.bolusingStatus);
        lastBolusTime =     (TextView) findViewById(R.id.lastBolusTime);
        lastCheck =         (TextView) findViewById(R.id.lastCheck);
        connection =         (TextView) findViewById(R.id.connection);
        connection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                                  @Override
                                  public void run() {
                                      DanaConnection dc = MainApp.getDanaConnection();
                                      if (dc != null)
                                          dc.connectIfNotConnected("connect req from UI");
                                      else
                                          log.error("connect req from UI: dc is null");
                                  }}
                );
            }
        });

        lowSuspendData = (TextView) findViewById(R.id.lowSuspendData);
        openApsStatus = (TextView) findViewById(R.id.OpenApsStatus);
        lowSuspendStatus = (TextView) findViewById(R.id.lowSuspendStatus);
        switchLowSuspend = (Switch) findViewById(R.id.switchLowSuspend);
        switchOpenAPS = (Switch) findViewById(R.id.switchOpenAPS);

        boolean openAPSenabled = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("OpenAPSenabled", false);
        switchOpenAPS.setChecked(openAPSenabled);
        switchOpenAPS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("OpenAPSenabled",isChecked);
                editor.commit();

            }
        });

        boolean LowSuspendenabled = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("LowSuspendEnabled", false);
        switchLowSuspend.setChecked(LowSuspendenabled);
        switchLowSuspend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("LowSuspendEnabled",isChecked);
                editor.commit();

            }
        });

        tbButton = (Button) findViewById(R.id.buttonTB);

        tbButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DanaConnection dc = MainApp.getDanaConnection();
                            if (dc != null) {
                                dc.connectIfNotConnected("tempBasal UI Button");
                                //if ("STOP".equals(tbButton.getText())) {
                                dc.tempBasalOff(true);
                                //} else {
                                //    dc.tempBasal(0, 1);
                                //}
                            } else
                                log.error("tempBasal UI Button: dc==null");
                        } catch (Exception e) {
                            log.error("tempBasal", e);

                        }
                    }
                });
            }
        });

        bolusStopButton = (Button) findViewById(R.id.bolusStopButton);

        bolusStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                try {
                    bolusStopButtonPressed = true;
                    final DanaConnection dc = MainApp.getDanaConnection();
                    if (dc != null) {
                        if (dc.isConnected()) {
                            Thread t = new Thread() {
                                @Override
                                public void run() {
                                    dc.bolusStop();
                                }
                            };
                            t.start();
                        }
                    } else
                        log.error("bolusStopButton UI Button: dc==null");
                } catch (Exception e) {
                    log.error("bolusStopButton", e);
                }
            }
        });

        carbsButton = (Button) findViewById(R.id.treatmentButton);

        carbsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FragmentManager manager = getFragmentManager();
                TreatmentDialogFragment treatmentDialogFragment = new TreatmentDialogFragment();
                treatmentDialogFragment.show(manager, "TreatmentDialog");
            }
        });
    }
    private void updateTempBasalIOB() {
        List<TempBasal> tempBasalList = loadTempBasalsDB();

        IobCalc.Iob iobNum = getIobFromTempBasals(tempBasalList);
        Double sens = MainApp.getNSProfile() != null ? MainApp.getNSProfile().getIsf(MainApp.getNSProfile().minutesFromMidnight()) : 0;
        basalIob.setText(
                "bIOB: " + formatNumber1place.format(iobNum.iobContrib) + " "
                        + formatNumber1place.format(iobNum.activityContrib * sens));
    }

    public static IobCalc.Iob getIobFromTempBasals(List<TempBasal> tempBasalList) {
        IobCalc.Iob iob = new IobCalc.Iob();

        Iterator<TempBasal> tempBasalIterator = tempBasalList.iterator();
        while(tempBasalIterator.hasNext()) {
            TempBasal tempBasal = tempBasalIterator.next();
            IobCalc.Iob calcIob = tempBasal.calcIob();
            if(tempBasal.getMsAgo()>4*60*60_000) {
//                tempBasalIterator.remove();
            }
            iob.plus(calcIob);
        }
        return iob;
    }

    @Nullable
    public static List<TempBasal> loadTempBasalsDB() {
        List<TempBasal> tempBasalList = null;

        try {
            Dao<TempBasal, Long> daoTempBasals = MainApp.getDbHelper().getDaoTempBasals();
            QueryBuilder<TempBasal, Long> queryBuilder = daoTempBasals.queryBuilder();
            queryBuilder.orderBy("timeIndex",false);
            queryBuilder.limit(20l);
            PreparedQuery<TempBasal> preparedQuery = queryBuilder.prepare();
            tempBasalList = daoTempBasals.query(preparedQuery);

        } catch (SQLException e) {
            log.debug(e.getMessage(),e);
        }
        return tempBasalList;
    }

    private void updateTreatmentsIOB() {
        List<Treatment> treatmentList = null;

        treatmentList = loadTreatments();
        IobCalc.Iob iobNum = getIobFromTreatments(treatmentList);

        iob.setText(
                "IOB: " + formatNumber1place.format(iobNum.iobContrib) + " "
                        + formatNumber1place.format(iobNum.activityContrib));
    }

    public static IobCalc.Iob getIobOpenAPSFromTreatments(List<Treatment> treatmentList) {
        IobCalc.Iob iob= new IobCalc.Iob ();
        Iterator<Treatment> treatmentIterator = treatmentList.iterator();
        while(treatmentIterator.hasNext()) {
            Treatment treatment = treatmentIterator.next();
            IobCalc.Iob calcIob = treatment.calcIobOpenAPS();
            // remove treatments older than 48h
            if(treatment.getMsAgo()>48*60*60_000) {
                treatmentIterator.remove();
            }
            iob= calcIob.plus(iob);
        }
        return iob;
    }

    public static IobCalc.Iob getIobFromTreatments(List<Treatment> treatmentList) {
        IobCalc.Iob iob= new IobCalc.Iob ();
        Iterator<Treatment> treatmentIterator = treatmentList.iterator();
        while(treatmentIterator.hasNext()) {
            Treatment treatment = treatmentIterator.next();
            IobCalc.Iob calcIob = treatment.calcIob();
            // remove treatments older than 48h
            if(treatment.getMsAgo()>48*60*60_000) {
                treatmentIterator.remove();
            }
            iob= calcIob.plus(iob);
        }
        return iob;
    }

    public static List<Treatment> loadTreatments() {
        List<Treatment> treatmentList = null;
        try {
            Dao<Treatment, Long> dao = MainApp.getDbHelper().getDaoTreatments();
            QueryBuilder<Treatment, Long> queryBuilder = dao.queryBuilder();
            queryBuilder.orderBy("timeIndex",false);
            queryBuilder.limit(4l);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            treatmentList = dao.query(preparedQuery);
        } catch (SQLException e) {
            log.debug(e.getMessage(),e);
        }
        return treatmentList;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    private void chancelAlarmManager() {
        AlarmManager am = ( AlarmManager ) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent( "info.nightscout.danaapp.ReceiverKeepAlive.action.PING"  );
        PendingIntent pi = PendingIntent.getBroadcast( this, 0, intent, 0 );

        am.cancel(pi);
    }

    private void setupAlarmManager() {
        AlarmManager am = ( AlarmManager ) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent( "info.nightscout.danar.ReceiverKeepAlive.action.PING"  );
        PendingIntent pi = PendingIntent.getBroadcast( this, 0, intent, 0 );

        long interval = 30*60_000L;
        long triggerTime = SystemClock.elapsedRealtime() + interval;

        try {
            pi.send();
        } catch (PendingIntent.CanceledException e) {
        }

        am.cancel(pi);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime(), interval, pi);

        List<ResolveInfo> queryBroadcastReceivers = getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("queryBroadcastReceivers " + queryBroadcastReceivers);

    }

    @Subscribe
    public void onStatusEvent(final BolusingEvent ev) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainApp.instance().lastBolusingEvent = ev.sStatus;
                if (ev.sStatus.equals("")) {
                    linearBolusing.setVisibility(LinearLayout.GONE);
                    log.debug("BolusingEvent: hidding Linearlayout");
                } else {
                    linearBolusing.setVisibility(LinearLayout.VISIBLE);
                    bolusingStatus.setText(ev.sStatus);
                    ev.sendToNSClient();
                }
            }
        });
    }

    @Subscribe
    public void onStatusEvent(final StatusEvent ev) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                lastCheck.setText(formatDateToJustTime.format(ev.timeLastSync));

                connectedPump.setText(ev.connectedPump);
                uRemaining.setText(ev.remainUnits.intValue() + "u");
                updateBatteryStatus(ev);

                currentBasal.setText(formatNumber1place.format(ev.currentBasal) + "u/h");

                lastBolusAmount.setText(formatNumber1place.format(ev.last_bolus_amount) + "u");
                lastBolusTime.setText(formatDateToJustTime.format(ev.last_bolus_time));

                if (ev.statusBolusExtendedInProgress) {
                    linearExtended.setVisibility(LinearLayout.VISIBLE);
                    extendedBolusAmount.setText(formatNumber1place.format(ev.statusBolusExtendedPlannedAmount) + "u");
                    extendedBolusSoFar.setText(ev.statusBolusExtendedDurationSoFarInMinutes + "of" + ev.statusBolusExtendedDurationInMinutes + "min");
                } else {
                    linearExtended.setVisibility(LinearLayout.GONE);
                }

                if (ev.tempBasalRatio != -1) {
                    linearTemp.setVisibility(LinearLayout.VISIBLE);
                    tempBasalRatio.setText(ev.tempBasalRatio + "%");
                    tempBasalAbs.setText(formatNumber1place.format(ev.currentBasal * ev.tempBasalRatio / 100) + "u/h");
                    tempBasalRemain.setText(format2digits.format(ev.tempBasalRemainMin / 60) + ":" + format2digits.format(ev.tempBasalRemainMin % 60));
                    tempBasalClock.setText("{fa-hourglass-o}");
                } else {
                    linearTemp.setVisibility(LinearLayout.GONE);
                }

                updateOpenAPSStatus();
                updateTempBasalIOB();
                updateTreatmentsIOB();
            }
        });
    }

    private void updateBatteryStatus(StatusEvent ev) {
        batteryStatus.setText("{fa-battery-" + (ev.remainBattery / 25) + "}");
    }

    private void updateOpenAPSStatus() {
        LowSuspendStatus lowSuspendStatusRef = LowSuspendStatus.getInstance();
        lowSuspendData.setText(lowSuspendStatusRef.dataText);
        openApsStatus.setText(lowSuspendStatusRef.openApsText);
        lowSuspendStatus.setText(lowSuspendStatusRef.statusText);
    }

    @Override
    public void treatmentDialogDeliver(final Double insulin, final Double carbs) {

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Treatment t = new Treatment();
                t.insulin = insulin;
                t.carbs = carbs;
                t.created_at = new Date();
                try {
                    MainApp.instance().getDbHelper().getDaoTreatments().create(t);
                    t.setTimeIndex(t.getTimeIndex());
                    t.sendToNSClient();
                    bolusStopButtonPressed = false;
                    BolusingEvent be = BolusingEvent.getInstance();
                    be.sStatus = "Connecting";
                    MainApp.bus().post(be);
                    synchronized (t) {
                        t.wait(2000);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                DanaConnection dc = MainApp.getDanaConnection();
                dc.connectIfNotConnected("treatmentDialogDeliver");
                try {
                    if (t.carbs > 0d)
                        dc.carbsEntry(carbs.intValue());
                    if (t.insulin > 0d) {
                        Treatment updated = NSClientDataReceiver.findByTimeIndex(t.getTimeIndex());
                        String _id = "";
                        if (updated._id == null || updated._id.equals("")) {
                            log.debug("treatmentDialogDeliver: updated treatment id not found. Not updating NS site");
                        } else _id = updated._id;
                        if (bolusStopButtonPressed) {
                            updated.insulin = 0d;
                            MainApp.instance().getDbHelper().getDaoTreatments().update(updated);
                        }
                        dc.bolus(insulin, _id);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        });


    }

    @Subscribe
    public void onStatusEvent(final ConnectionStatusEvent c) {
        runOnUiThread(new Runnable() {
                          @Override
                          public void run() {
                              if (c.sConnecting) {
                                  connection.setText("{fa-bluetooth-b spin} " + c.sConnectionAttemptNo);
                              } else {
                                  if (c.sConnected) {
                                      connection.setText("{fa-bluetooth}");
                                  } else {
                                      connection.setText("{fa-bluetooth-b}");
                                  }
                              }
                          }
                      }
        );

    }

    @Subscribe
    public void onStatusEvent(final PreferenceChange pch) {
        if (MainApp.getDanaConnection() != null)
            MainApp.getDanaConnection().preferenceChange();
    }


    private class OnNavigationItemSelectedListener implements NavigationView.OnNavigationItemSelectedListener {
        @Override
        public boolean onNavigationItemSelected(MenuItem menuItem) {
            mDrawerLayout.closeDrawers();
            menuItem.setChecked(true);
            switch (menuItem.getItemId()) {
                case R.id.nav_home:
                break;

                case R.id.nav_preferences: {
                    Intent i = new Intent(getApplicationContext(), PreferencesActivity.class);
                    startActivity(i);
                    break;
                }

                case R.id.nav_resetdb: {
                    MainApp.getDbHelper().resetDatabases();
                    break;
                }

                case R.id.nav_backup: {

                    try {
                        File sd = Environment.getExternalStorageDirectory();
                        File data = Environment.getDataDirectory();

                        if (sd.canWrite()) {
                            String currentDBPath = "/data/info.nightscout.danaapp/databases/"+ DatabaseHelper.DATABASE_NAME;
                            String backupDBPath = DatabaseHelper.DATABASE_NAME;
                            File currentDB = new File(data, currentDBPath);
                            File backupDB = new File(sd, backupDBPath);

                            if (currentDB.exists()) {
                                FileChannel src = new FileInputStream(currentDB).getChannel();
                                FileChannel dst = new FileOutputStream(backupDB).getChannel();
                                dst.transferFrom(src, 0, src.size());
                                src.close();
                                dst.close();
                            }
                        }
                    } catch (Exception e) {
                        log.error("Excpetion "+e.getMessage(),e);
                    }
                    break;
                }


//                case R.id.nav_test_alarm: {
//                    Intent alarmServiceIntent = new Intent(MainApp.instance().getApplicationContext(), ServiceAlarm.class);
//                    alarmServiceIntent.putExtra("alarmText","Connection error");
//                    MainApp.instance().getApplicationContext().startService(alarmServiceIntent);
//                    break;
//                }

                case R.id.nav_exit: {
                    log.debug("Exiting");
                    chancelAlarmManager();

                    MainApp.bus().post(new StopEvent());
                    MainApp.closeDbHelper();

                    finish();
                    System.runFinalization();
                    System.exit(0);

                    break;
                }

            }

            mDrawerLayout.closeDrawers();


            return true;
        }
    }

}
