package info.nightscout.danar.comm;

import com.squareup.otto.Bus;

import info.nightscout.danar.db.Treatment;
import info.nightscout.danar.event.BolusingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsgBolusStop extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgBolusStop.class);
    private static Treatment t;
    private static Double amount;
    private static Bus bus = null;

    public static boolean stopped = false;
    public static boolean forced = false;

    public MsgBolusStop() {
        super("CMD_MEALINS_STOP");
        SetCommand(SerialParam.CTRL_CMD_BOLUS);
        SetSubCommand(SerialParam.CTRL_SUB_BOLUS_STOP);
        stopped = false;
    }

    public MsgBolusStop(String cmdName) {
        super(cmdName);
        stopped = false;
    }

    public MsgBolusStop(Bus bus, Double amount, Treatment t) {
        this();
        this.bus = bus;
        this.t = t;
        this.amount = amount;
        forced = false;
    }


    public void handleMessage(byte[] bytes) {

        stopped = true;
        if (!forced)
            t.insulin = amount;
        else {
            BolusingEvent bolusingEvent = BolusingEvent.getInstance();
            bolusingEvent.sStatus = "Stopped";
            bus.post(bolusingEvent);
        }
    }


}
