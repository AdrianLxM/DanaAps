package info.nightscout.danaaps;

import android.content.Context;
import android.widget.Toast;

import java.util.Date;
import java.util.GregorianCalendar;

import info.nightscout.danaaps.utils.ToastUtils;

/**
 * Created by mike on 04.03.2016.
 */
public class AppExpire {
    static private GregorianCalendar date = new GregorianCalendar(2016, 9, 1);

    public static boolean isExpired(Context context) {
        boolean expiring = true;
        if  (new Date().getTime() < date.getTime().getTime()) {
            expiring = false;
        }
        if (expiring)
            ToastUtils.showToastInUiThread(context, "Application expired");

        return expiring;
    }
}
