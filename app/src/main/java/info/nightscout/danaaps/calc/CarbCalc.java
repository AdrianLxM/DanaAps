package info.nightscout.danaaps.calc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import info.nightscout.client.data.NSProfile;
import info.nightscout.danaaps.MainApp;
import info.nightscout.danar.db.Treatment;

/**
 * Created by mike on 08.03.2016.
 */
public class CarbCalc {
    private static Logger log = LoggerFactory.getLogger(IobCalc.class);

    public static class Meal {
        public double carbs = 0d;
        public double boluses = 0d;
        public double mealCOB = 0d;
    }

    NSProfile profile;
    List<Treatment> treatments;

    public CarbCalc(NSProfile profile, List<Treatment> treatments) {
        this.profile = profile;
        this.treatments = treatments;
    }

    public Meal invoke(Date time) {
        Meal meal = new Meal();
        long carbDelay = 15 * 60 * 1000;
        //TODO: make this configurable
        long firstCarbTime = time.getTime();

        Iterator<Treatment> treatmentIterator = treatments.iterator();
        while(treatmentIterator.hasNext()) {
            Treatment treatment = treatmentIterator.next();
            long now = time.getTime();
            long dia_ago = now - (new Double(profile.getDia()*60*60*1000)).longValue();
            long t = treatment.created_at.getTime();
            if(t > dia_ago && t <= now) {
                if (treatment.carbs >= 1) {
                    if (t < firstCarbTime) {
                        firstCarbTime = t;
                    }
                    meal.carbs += treatment.carbs;
                }
                if (treatment.insulin >= 0.1) {
                    meal.boluses += treatment.insulin;
                }
            }
        }
        long now = new Date().getTime();
        long hours = (now-firstCarbTime-carbDelay)/(60*60*1000);
        double decayed = profile.getCarbAbsorbtionRate()*hours;
        //console.error(hours, decayed);
        meal.mealCOB = Math.max(0, meal.carbs - decayed);
        //console.error(mealCOB);

        return meal;
    }
}
