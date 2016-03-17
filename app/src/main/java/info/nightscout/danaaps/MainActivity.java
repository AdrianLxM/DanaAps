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

import info.nightscout.client.data.NSProfile;
import info.nightscout.danaaps.calc.CarbCalc;
import info.nightscout.danaaps.calc.Iob;
import info.nightscout.danaaps.carbs.TreatmentDialogFragment;
import info.nightscout.client.receivers.NSClientDataReceiver;
import info.nightscout.danar.DanaConnection;
import info.nightscout.danar.db.DatabaseHelper;
import info.nightscout.danar.db.TempBasal;
import info.nightscout.danar.db.Treatment;
import info.nightscout.danar.event.BolusingEvent;
import info.nightscout.danar.event.ConnectionStatusEvent;
import info.nightscout.danar.event.LowSuspendStatus;

import org.openaps.openAPS.IobParam;
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
    private static DecimalFormat format1digit = new DecimalFormat("0");
    private static DecimalFormat formatNumber1place = new DecimalFormat("0.00");
    private static DateFormat formatDateToJustTime = new SimpleDateFormat("HH:mm");
    public static final DecimalFormat mmolFormat = new DecimalFormat("0.0");

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
    TextView iobActivity;
    TextView basalIobActivity;
    TextView mealAssistCarbs;
    TextView mealAssistBoluses;
    TextView connection;

    private TextView bgTime;
    private TextView bgValue;
    private TextView bgDelta;
    private TextView bgDeltaAvg15m;
    private TextView bgDeltaAvg30m;
    private TextView bgAvg15m;
    private TextView bgAvg30m;

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
    private TextView lowSuspend;
    private TextView lowSuspendProjected;
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
        registerGUIElements();

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

    }

    private void registerGUIElements() {
        iob = (TextView) findViewById(R.id.iob);
        basalIob = (TextView) findViewById(R.id.basal_iob);
        iobActivity = (TextView) findViewById(R.id.iobActivity);
        basalIobActivity = (TextView) findViewById(R.id.basal_iobActivity);
        mealAssistCarbs  = (TextView) findViewById(R.id.mealAssist_carbs);
        mealAssistBoluses  = (TextView) findViewById(R.id.mealAssist_boluses);

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

        bgTime = (TextView) findViewById(R.id.bgTime);
        bgValue = (TextView) findViewById(R.id.bgValue);
        bgDelta = (TextView) findViewById(R.id.bgDelta);
        bgDeltaAvg15m = (TextView) findViewById(R.id.bgDeltaAvg15m);
        bgDeltaAvg30m = (TextView) findViewById(R.id.bgDeltaAvg30m);
        bgAvg15m = (TextView) findViewById(R.id.bgAvg15m);
        bgAvg30m = (TextView) findViewById(R.id.bgAvg30m);

        openApsStatus = (TextView) findViewById(R.id.OpenApsStatus);
        lowSuspend = (TextView) findViewById(R.id.lowSuspend);
        lowSuspendProjected = (TextView) findViewById(R.id.lowSuspendProjected);
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

        Iob iobNum = getIobFromTempBasals(tempBasalList);
        Double sens = MainApp.getNSProfile() != null ? MainApp.getNSProfile().getIsf(MainApp.getNSProfile().minutesFromMidnight()) : 0;
        basalIob.setText(formatNumber1place.format(iobNum.iobContrib));
        basalIobActivity.setText(formatNumber1place.format(iobNum.activityContrib * sens));
    }

    public static Iob getIobFromTempBasals(List<TempBasal> tempBasalList) {
        Iob iob = new Iob();

        Iterator<TempBasal> tempBasalIterator = tempBasalList.iterator();
        while(tempBasalIterator.hasNext()) {
            TempBasal tempBasal = tempBasalIterator.next();
            Iob calcIob = tempBasal.calcIob();
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
        Iob iobNum = getIobFromTreatments(treatmentList);

        iob.setText(formatNumber1place.format(iobNum.iobContrib));
        iobActivity.setText(formatNumber1place.format(iobNum.activityContrib));
    }

    public static Iob getIobOpenAPSFromTreatments(List<Treatment> treatmentList) {
        Iob iob= new Iob();
        Iterator<Treatment> treatmentIterator = treatmentList.iterator();
        while(treatmentIterator.hasNext()) {
            Treatment treatment = treatmentIterator.next();
            Iob calcIob = treatment.calcIobOpenAPS();
            // remove treatments older than 48h
            if(treatment.getMsAgo()>48*60*60_000) {
                treatmentIterator.remove();
            }
            iob = calcIob.plus(iob);
        }
        return iob;
    }

    public static Iob getIobFromTreatments(List<Treatment> treatmentList) {
        Iob iob = new Iob();
        Iterator<Treatment> treatmentIterator = treatmentList.iterator();
        while(treatmentIterator.hasNext()) {
            Treatment treatment = treatmentIterator.next();
            Iob calcIob = treatment.calcIob();
            // remove treatments older than 48h
            if(treatment.getMsAgo()>48*60*60_000) {
                treatmentIterator.remove();
            }
            iob = calcIob.plus(iob);
        }
        return iob;
    }

    private void updateMealAssist() {
        List<Treatment> treatmentList = null;

        treatmentList = loadTreatments();
        CarbCalc.Meal meal = getCarbsFromTreatments(treatmentList);

        mealAssistCarbs.setText(format1digit.format(meal.carbs));
        mealAssistBoluses.setText(formatNumber1place.format(meal.boluses));
    }

    public static CarbCalc.Meal getCarbsFromTreatments(List<Treatment> treatmentList) {
        NSProfile profile = MainApp.getNSProfile();
        if (profile != null) {
            CarbCalc calc = new CarbCalc(profile, treatmentList);
            CarbCalc.Meal meal = calc.invoke(new Date());
            return meal;
        } else return new CarbCalc.Meal();
    }
/*
    public IobParam iobTotal(opts) {
        Date time = new Date();
        var iobCalc = opts.calculate;
        NSProfile profile_data = MainApp.getNSProfile();
        Double iob = 0d;
        Double bolussnooze = 0d;
        Double basaliob = 0d;
        Double activity = 0d;
        Double netbasalinsulin = 0d;
        Double hightempinsulin = 0d;

        IobParam result = new IobParam(0d, 0d, 0d, 0d, 0d, 0d);

        List<Treatment> treatmentList = loadTreatments();

        Iterator<Treatment> treatmentIterator = treatmentList.iterator();
        while(treatmentIterator.hasNext()) {
            Treatment treatment = treatmentIterator.next();
            if(treatment.date <= time.getTime( )) {
                var dia = profile_data.dia;
                Iob tIOB = iobCalc(treatment, time, dia);
                if (tIOB && tIOB.iobContrib) iob += tIOB.iobContrib;
                if (tIOB && tIOB.activityContrib) activity += tIOB.activityContrib;
                // keep track of bolus IOB separately for snoozes, but decay it twice as fast
                if (treatment.insulin >= 0.2 && treatment.started_at) {
                    //use half the dia for double speed bolus snooze
                    var bIOB = iobCalc(treatment, time, dia / 2);
                    //console.log(treatment);
                    //console.log(bIOB);
                    if (bIOB && bIOB.iobContrib) bolussnooze += bIOB.iobContrib;
                } else {
                    var aIOB = iobCalc(treatment, time, dia);
                    if (aIOB && aIOB.iobContrib) basaliob += aIOB.iobContrib;
                    if (treatment.insulin) {
                        now = time.getTime();
                        var dia_ago = now - profile_data.dia*60*60*1000;
                        if(treatment.date > dia_ago && treatment.date <= now) {
                            netbasalinsulin += treatment.insulin;
                            if (treatment.insulin > 0) {
                                hightempinsulin += treatment.insulin;
                            }
                        }
                    }
                }
            }
        }

        result.iob = Math.round( iob * 1000d ) / 1000d;
        result.activity = Math.round( activity * 10000d ) / 10000d;
        result.bolussnooze = Math.round( bolussnooze * 1000d ) / 1000d;
        result.basaliob = Math.round( basaliob * 1000d ) / 1000d;
        result.netbasalinsulin = Math.round( netbasalinsulin * 1000d ) / 1000d;
        result.hightempinsulin = Math.round( hightempinsulin * 1000d ) / 1000d;
        return result;
    }
*/
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
                if (ev.sStatus.equals("")) {
                    linearBolusing.setVisibility(LinearLayout.GONE);
                    log.debug("BolusingEvent: hidding Linearlayout");
                } else {
                    linearBolusing.setVisibility(LinearLayout.VISIBLE);
                    bolusingStatus.setText(ev.sStatus);
                    // Do not send "Delivering X.XU statuses
                    if (ev.sStatus.indexOf("Delivering") < 0)
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
                updateMealAssist();
            }
        });
    }

    private void updateBatteryStatus(StatusEvent ev) {
        batteryStatus.setText("{fa-battery-" + (ev.remainBattery / 25) + "}");
    }

    private void updateOpenAPSStatus() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String units = SP.getString("ns_units", "mg/dL");

        LowSuspendStatus lowSuspendStatus = LowSuspendStatus.getInstance();
        bgTime.setText(formatDateToJustTime.format(lowSuspendStatus.time));
        bgValue.setText(fromMgdltoString(lowSuspendStatus.bg.doubleValue(), units));
        bgDelta.setText(fromMgdltoString(lowSuspendStatus.delta.doubleValue(), units));
        bgDeltaAvg15m.setText(fromMgdltoString(lowSuspendStatus.deltaAvg15m, units));
        bgDeltaAvg30m.setText(fromMgdltoString(lowSuspendStatus.deltaAvg15m, units));
        bgAvg15m.setText(fromMgdltoString(lowSuspendStatus.avg15m, units));
        bgAvg30m.setText(fromMgdltoString(lowSuspendStatus.avg30m, units));
        openApsStatus.setText(lowSuspendStatus.openApsText);
        lowSuspend.setText(lowSuspendStatus.lowSuspenResult.low ? "{fa-exclamation-triangle}" : "{fa-check}");
        lowSuspendProjected.setText(lowSuspendStatus.lowSuspenResult.lowProjected ? "{fa-exclamation-triangle}" : "{fa-check}");
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
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                bolusStopButtonPressed = false;
                BolusingEvent be = BolusingEvent.getInstance();
                if (t.carbs > 0d || t.insulin > 0d) {
                    be.sStatus = "Connecting";
                    MainApp.bus().post(be);
                }

                DanaConnection dc = MainApp.getDanaConnection();
                dc.connectIfNotConnected("treatmentDialogDeliver");
                try {
                    if (t.carbs > 0d)
                        dc.carbsEntry(carbs.intValue());
                    if (t.insulin > 0d) {
                        if (bolusStopButtonPressed) {
                            t.insulin = 0d;
                            MainApp.instance().getDbHelper().getDaoTreatments().update(t);
                        } else {
                            dc.bolus(insulin, t);
                        }
                    }
                    t.setTimeIndex(t.getTimeIndex());
                    t.sendToNSClient();
                    dc.waitMsec(2000);
                    be.sStatus = "";
                    MainApp.bus().post(be);
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

                case R.id.nav_refreshtreatments: {
                    MainApp.getDbHelper().resetTreatments();
                    Intent restartNSClient = new Intent("info.nightscout.client.RESTART");
                    getApplicationContext().sendBroadcast(restartNSClient);
                    break;
                }

                case R.id.nav_backup: {

                    try {
                        File sd = Environment.getExternalStorageDirectory();
                        File data = Environment.getDataDirectory();

                        if (sd.canWrite()) {
                            String currentDBPath = "/data/info.nightscout.danaaps/databases/"+ DatabaseHelper.DATABASE_NAME;
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

    private String fromMgdltoString (Double value, String units) {
        if (units.equals("mg/dL")) return Integer.toString(value.intValue());
        else return mmolFormat.format(value / 18);
    }

}
