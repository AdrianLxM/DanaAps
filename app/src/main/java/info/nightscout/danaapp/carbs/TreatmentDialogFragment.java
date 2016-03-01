package info.nightscout.danaapp.carbs;

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import info.nightscout.danaapp.R;

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

                Double insulin = Double.parseDouble(this.insulin.getText().toString());
                Double carbs = Double.parseDouble(this.carbs.getText().toString());
                if(insulin > 3) {
                    this.insulin.setText("");
                } else if(carbs > 48) {
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