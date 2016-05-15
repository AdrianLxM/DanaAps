package info.nightscout.danar;

public class Result extends Object {
    public boolean result = false;
    public boolean enacted = false;
    public String comment = "";
    public int duration = -1;
    public double absolute = -1;
    public int percent = -1;

    public String log() {
        return "Result: " + result + " Enacted: " + enacted + " Comment: " + comment + " Duration: " + duration + " Absolute: " + absolute + " Percent: " + percent;
    }
}
