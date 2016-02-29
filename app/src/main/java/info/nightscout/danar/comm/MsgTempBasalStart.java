package info.nightscout.danar.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsgTempBasalStart extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgTempBasalStart.class);

    public MsgTempBasalStart(int percent, int durationInHours) {
        super("CMD_PUMPSET_EXERCISE_S");
        SetCommand(SerialParam.CTRL_CMD_TB);
        SetSubCommand(SerialParam.CTRL_SUB_TB_START);

        SetParamByte((byte) (percent & 255));
        SetParamByte((byte) (durationInHours & 255));

        log.debug("tempBasalMessage percent:"+percent+" duration:"+durationInHours);
    }

    public MsgTempBasalStart(String cmdName) {
        super(cmdName);
    }

    public void handleMessage(byte[] bytes) {
        received = true;
        int result = DanaRMessages.byteArrayToInt(bytes, 0, 1);
        if(result!=1) {
            failed = true;
            log.error("Command response is not OK " + getMessageName());
        }
    }


}
