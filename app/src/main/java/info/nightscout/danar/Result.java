package info.nightscout.danar;

/**
 * Created by mike on 08.02.2016.
 */
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
