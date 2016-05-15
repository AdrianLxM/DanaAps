package info.nightscout.danar.comm;

public class MsgPCCommStop extends DanaRMessage {
    public MsgPCCommStop() {
        super("CMD_CMD_DISCONNECT");
        SetCommand(SerialParam.CTRL_CMD_COMM);
        SetSubCommand(SerialParam.CTRL_SUB_COMM_DISCONNECT);
    }
    public MsgPCCommStop(String cmdName) {
        super(cmdName);
    }
}
