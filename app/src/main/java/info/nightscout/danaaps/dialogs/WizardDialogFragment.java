package info.nightscout.danaaps.dialogs;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;

import info.nightscout.client.data.NSProfile;
import info.nightscout.danaaps.MainActivity;
import info.nightscout.danaaps.MainApp;
import info.nightscout.danaaps.R;
import info.nightscout.danaaps.calc.Iob;
import info.nightscout.danaaps.utils.ToastUtils;
import info.nightscout.danar.db.Treatment;

public class WizardDialogFragment extends DialogFragment implements OnClickListener {

    Button wizardDialogDeliverButton;
    Communicator communicator;
    TextView correctionInput;
    TextView carbsInput;
    TextView bgInput;
    TextView bg, bgInsulin, bgUnits;
    CheckBox bgCheckbox;
    TextView carbs, carbsInsulin;
    TextView iob, iobInsulin;
    CheckBox iobCheckbox;
    TextView correctionInsulin;
    TextView total, totalInsulin;

    public static final DecimalFormat numberFormat = new DecimalFormat("0.00");
    public static final DecimalFormat mmolFormat = new DecimalFormat("0.0");
    public static final DecimalFormat intFormat = new DecimalFormat("0");

    Double calculatedCarbs = 0d;
    Double calculatedTotalInsulin = 0d;

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {}
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start,int before, int count) {
            calculateInsulin();
        }
    };

    @Override
    public void onAttach(Activity activity) {

        super.onAttach(activity);

        if (activity instanceof Communicator) {
            communicator = (Communicator) getActivity();

        } else {
            throw new ClassCastException(activity.toString()
                    + " must implemenet WizardDialogFragment.Communicator");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.wizard_fragment, null, false);

        wizardDialogDeliverButton = (Button) view.findViewById(R.id.wizardDialogDeliverButton);
        wizardDialogDeliverButton.setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        correctionInput = (TextView)view.findViewById(R.id.wizardCorrectionInput);
        carbsInput = (TextView)view.findViewById(R.id.wizardCarbsInput);
        bgInput = (TextView)view.findViewById(R.id.wizardBGInput);

        correctionInput.addTextChangedListener(textWatcher);
        carbsInput.addTextChangedListener(textWatcher);
        bgInput.addTextChangedListener(textWatcher);

        bg = (TextView)view.findViewById(R.id.wizardBG);
        bgInsulin = (TextView)view.findViewById(R.id.wizardBGInsulin);
        bgUnits = (TextView)view.findViewById(R.id.wizardBGUnits);
        bgCheckbox = (CheckBox) view.findViewById(R.id.wizardBGCheckbox);
        carbs = (TextView)view.findViewById(R.id.wizardCarbs);
        carbsInsulin = (TextView)view.findViewById(R.id.wizardCarbsInsulin);
        iob = (TextView)view.findViewById(R.id.wizardIOB);
        iobInsulin = (TextView)view.findViewById(R.id.wizardIOBInsulin);
        iobCheckbox = (CheckBox) view.findViewById(R.id.wizardIOBCheckbox);
        correctionInsulin = (TextView)view.findViewById(R.id.wizardCorrectionInsulin);
        total = (TextView)view.findViewById(R.id.wizardTotal);
        totalInsulin = (TextView)view.findViewById(R.id.wizardTotalInsulin);

        initDialog();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.wizardDialogDeliverButton:
                if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d){
                    dismiss();
                    communicator.treatmentDialogDeliver(calculatedTotalInsulin, calculatedCarbs);
                }
                break;
            case R.id.wizardBGCheckbox:
            case R.id.wizardIOBCheckbox:
                calculateInsulin();
                break;
        }

    }

    private void initDialog() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String units = SP.getString("ns_units", "mg/dL");

        if (MainApp.getNSProfile() == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), "No profile loaded from NS yet");
            dismiss();
            return;
        }

        // Set BG if not old
        bgUnits.setText(units);


        if (MainActivity.lastBGTime != null && new Date().getTime() - MainActivity.lastBGTime.getTime() < 11 * 60 * 1000) {
            Double sens = MainApp.getNSProfile().getIsf(MainApp.getNSProfile().secondsFromMidnight());
            Double targetBGLow  = MainApp.getNSProfile().getTargetLow(MainApp.getNSProfile().secondsFromMidnight());
            Double targetBGHigh  = MainApp.getNSProfile().getTargetHigh(MainApp.getNSProfile().secondsFromMidnight());
            Double bgDiff;
            if (MainActivity.lastBG <= targetBGLow) {
                bgDiff = fromMgdl(MainActivity.lastBG, units) - targetBGLow;
            } else {
                bgDiff = fromMgdl(MainActivity.lastBG, units) - targetBGHigh;
            }

            bg.setText(fromMgdltoString(MainActivity.lastBG, units) + " ISF: " + intFormat.format(sens));
            bgInsulin.setText(numberFormat.format(bgDiff / sens) + "U");
            bgInput.setText(fromMgdltoString(MainActivity.lastBG, units));
        } else {
            bg.setText("");
            bgInsulin.setText("");
            bgInput.setText("");
        }

        // IOB calculation
        List<Treatment> treatmentList = MainActivity.loadTreatments();
        Iob bolusIob = MainActivity.getIobFromTreatments(treatmentList);
        Iob basalIob = MainActivity.getIobFromTempBasals(MainActivity.loadTempBasalsDB());
        bolusIob.plus(basalIob);
        iobInsulin.setText("-" + numberFormat.format(bolusIob.iobContrib) + "U");

        totalInsulin.setText("");
        wizardDialogDeliverButton.setVisibility(Button.GONE);

    }

    private void calculateInsulin() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        Double maxbolus = Double.parseDouble(SP.getString("safety_maxbolus", "3"));
        Double maxcarbs = Double.parseDouble(SP.getString("safety_maxcarbs", "48"));
        String units = SP.getString("ns_units", "mg/dL");

        NSProfile profile = MainApp.instance().getNSProfile();

        // Entered values
        String i_bg = this.bgInput.getText().toString();
        String i_carbs = this.carbsInput.getText().toString();
        String i_correction = this.correctionInput.getText().toString();
        Double c_bg = 0d;
        try { c_bg = Double.parseDouble(i_bg.equals("") ? "0" : i_bg); } catch (Exception e) {}
        Double c_carbs = 0d;
        try { c_carbs = Double.parseDouble(i_carbs.equals("") ? "0" : i_carbs); } catch (Exception e) {}
        c_carbs = ((Long)Math.round(c_carbs)).doubleValue();
        Double c_correction = 0d;
        try { c_correction = Double.parseDouble(i_correction.equals("") ? "0" : i_correction);  } catch (Exception e) {}
        if(c_correction > maxbolus) {
            this.correctionInput.setText("");
            wizardDialogDeliverButton.setVisibility(Button.GONE);
            return;
        }
        if(c_carbs > maxcarbs) {
            this.carbsInput.setText("");
            wizardDialogDeliverButton.setVisibility(Button.GONE);
            return;
        }


        // Insulin from BG
        Double sens = profile.getIsf(MainApp.getNSProfile().secondsFromMidnight());
        Double targetBGLow  = profile.getTargetLow(MainApp.getNSProfile().secondsFromMidnight());
        Double targetBGHigh  = profile.getTargetHigh(MainApp.getNSProfile().secondsFromMidnight());
        Double bgDiff;
        if (c_bg <= targetBGLow) {
            bgDiff = c_bg - targetBGLow;
        } else {
            bgDiff = c_bg - targetBGHigh;
        }
        Double insulinFromBG = (bgCheckbox.isChecked() && c_bg != 0d) ? bgDiff /sens : 0d;
        bg.setText(c_bg + " ISF: " + intFormat.format(sens));
        bgInsulin.setText(numberFormat.format(insulinFromBG) + "U");

        // Insuling from carbs
        Double ic = profile.getIc(MainApp.getNSProfile().secondsFromMidnight());
        Double insulinFromCarbs = c_carbs / ic;
        carbs.setText(intFormat.format(c_carbs) + "g IC: " + intFormat.format(ic));
        carbsInsulin.setText(numberFormat.format(insulinFromCarbs) + "U");

        // Insulin from IOB
        List<Treatment> treatmentList = MainActivity.loadTreatments();
        Iob bolusIob = MainActivity.getIobFromTreatments(treatmentList);
        Iob basalIob = MainActivity.getIobFromTempBasals(MainActivity.loadTempBasalsDB());
        bolusIob.plus(basalIob);
        Double insulingFromIOB = iobCheckbox.isChecked() ? bolusIob.iobContrib : 0d;
        iobInsulin.setText("-" + numberFormat.format(insulingFromIOB) + "U");

        // Insulin from correction
        Double insulinFromCorrection = c_correction;
        correctionInsulin.setText(numberFormat.format(insulinFromCorrection) + "U");

        // Total
        calculatedTotalInsulin = insulinFromBG + insulinFromCarbs - insulingFromIOB + insulinFromCorrection;

        if (calculatedTotalInsulin < 0) {
            Double carbsEquivalent = -calculatedTotalInsulin * ic;
            total.setText("Missing " + intFormat.format(carbsEquivalent) + "g");
            calculatedTotalInsulin = 0d;
            totalInsulin.setText("");
        } else {
            calculatedTotalInsulin = roundTo(calculatedTotalInsulin, 0.05d);
            total.setText("");
            totalInsulin.setText(numberFormat.format(calculatedTotalInsulin) + "U");
        }

        calculatedCarbs = c_carbs;

        if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d) {
            String insulinText = calculatedTotalInsulin > 0d ? (numberFormat.format(calculatedTotalInsulin) + "U") : "";
            String carbsText = calculatedCarbs > 0d ? (intFormat.format(calculatedCarbs) + "g") : "";
            wizardDialogDeliverButton.setText("SEND " + insulinText + " " + carbsText);
            wizardDialogDeliverButton.setVisibility(Button.VISIBLE);
        } else {
            wizardDialogDeliverButton.setVisibility(Button.GONE);
        }
    }

    private String fromMgdltoString (Double value, String units) {
        if (units.equals("mg/dL")) return Integer.toString(value.intValue());
        else return mmolFormat.format(value / 18);
    }

    private double fromMgdl(Double value, String units) {
        if (units.equals("mg/dL")) return value;
        else return value / 18;
    }

    private Double roundTo(Double x, Double step) {
        if (x != 0d) {
            return Math.round(x / step) * step;
        }
        return 0d;
    }

    public interface Communicator {
        void treatmentDialogDeliver(Double insulin, Double carbs);
    }

}