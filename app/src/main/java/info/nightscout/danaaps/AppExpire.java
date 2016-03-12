package info.nightscout.danaaps;

import android.widget.Toast;

import java.util.Date;

/**
 * Created by mike on 04.03.2016.
 */
public class AppExpire {
    static final Date date = new Date(116,5,1);

    public static boolean isExpired() {
        boolean expiring =  (new Date()).getTime() > date.getTime();
        if (expiring)
            Toast.makeText(MainApp.instance().getApplicationContext(), "Application expired", Toast.LENGTH_LONG).show();

        return expiring;
    }
}
