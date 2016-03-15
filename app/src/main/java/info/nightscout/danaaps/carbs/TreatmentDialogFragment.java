package info.nightscout.danaaps.carbs;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import info.nightscout.danaaps.MainApp;
import info.nightscout.danaaps.R;

public class TreatmentDialogFragment extends DialogFragment implements OnClickListener {

    Button treatmentDialogDeliverButton;
    Communicator communicator;
    TextView insulin;
    TextView carbs;
    @Override
    public void onAttach(Activity activity) {

        super.onAttach(activity);

        if (activity instanceof Communicator) {
            communicator = (Communicator) getActivity();

        } else {
            throw new ClassCastException(activity.toString()
                    + " must implemenet TreatmentDialogFragment.Communicator");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatment_fragment, null, false);

        treatmentDialogDeliverButton = (Button) view.findViewById(R.id.treatmentDialogDeliverButton);

        treatmentDialogDeliverButton.setOnClickListener(this);
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        insulin = (TextView)view.findViewById(R.id.insulinAmount);
        carbs = (TextView)view.findViewById(R.id.carbsAmount);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.treatmentDialogDeliverButton:
                SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
                Double maxbolus = (double) SP.getFloat("safety_maxbolus", 3);
                Double maxcarbs = (double) SP.getFloat("safety_maxcarbs", 48);


                String insulinText = this.insulin.getText().toString();
                String carbsText = this.carbs.getText().toString();
                Double insulin = Double.parseDouble(!insulinText.equals("") ? this.insulin.getText().toString() : "0");
                Double carbs = Double.parseDouble(!carbsText.equals("") ? this.carbs.getText().toString() : "0");
                if(insulin > maxbolus) {
                    this.insulin.setText("");
                } else if(carbs > maxcarbs) {
                    this.carbs.setText("");
                } else if (insulin > 0d || carbs > 0d){
                    dismiss();
                    communicator.treatmentDialogDeliver(insulin, carbs);
                }
                break;
        }

    }

    public interface Communicator {
        void treatmentDialogDeliver(Double insulin, Double carbs);
    }

}