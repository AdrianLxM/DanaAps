package info.nightscout.danar.comm;

public class MsgPCCommStart extends DanaRMessage {
    public MsgPCCommStart() {
        super("CMD_CONNECT");
        SetCommand(SerialParam.CTRL_CMD_COMM);
        SetSubCommand(SerialParam.CTRL_SUB_COMM_CONNECT);
    }
    public MsgPCCommStart(String cmdName) {
        super(cmdName);
    }
}
