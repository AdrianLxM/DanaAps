package info.nightscout.danar.comm;

import com.squareup.otto.Bus;

import info.nightscout.danar.db.Treatment;
import info.nightscout.danar.event.BolusingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

public class MsgBolusProgress extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgBolusProgress.class);
    public static final DecimalFormat bolusNumberFormat = new DecimalFormat("0.0");
    private static Bus bus = null;

    private static Treatment t;
    private static double amount;

    public int progress = -1;

    public MsgBolusProgress() {
        super("CMD_PUMP_THIS_REMAINDER_MEAL_INS");
        SetCommand(SerialParam.CTRL_CMD_STATUS);
        SetSubCommand(SerialParam.CTRL_SUB_STATUS_BOLUS_PROGRESS);
    }

    public MsgBolusProgress(String cmdName) {
        super(cmdName);
    }


    public MsgBolusProgress(Bus bus, double amount, Treatment t) {
        this();
        this.amount = amount;
        this.t = t;
        this.bus = bus;
    }

    public void handleMessage(byte[] bytes) {
        progress = DanaRMessages.byteArrayToInt(bytes, 0, 2);
//        bolusUI.bolusDeliveredAmountSoFar = progress/100d;
//        bolusUI.bolusDelivering();
        Double done = (amount * 100 - progress) / 100d;
        t.insulin = done;
        BolusingEvent bolusingEvent = BolusingEvent.getInstance();
        bolusingEvent.sStatus = "Delivering " + bolusNumberFormat.format(done) + "U";
        bolusingEvent.t = t;
        log.debug("remaining: " + progress + " delivered: " + done);

        bus.post(bolusingEvent);
    }


}
