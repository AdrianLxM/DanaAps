package info.nightscout.danaaps;

import java.util.Date;

import info.nightscout.client.data.NSProfile;
import info.nightscout.danar.db.TempBasal;

/**
 * Created by mike on 23.02.2016.
 */
public class VirtualPump {

    public static VirtualPump virtualPump = null;

    public static double remainUnits = 100;
    public static int remainBattery = 50;
    public static NSProfile nsProfile = MainApp.getNSProfile();

    public static Date last_bolus_time = new Date(2016,1,1);
    public static double last_bolus_amount = 0;

    public static TempBasal tempbasal = null;

    public static VirtualPump getInstance() {
        if (virtualPump == null) virtualPump = new VirtualPump();
        return virtualPump;
    }
}
