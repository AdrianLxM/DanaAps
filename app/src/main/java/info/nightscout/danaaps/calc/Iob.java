package info.nightscout.danaaps.calc;

/**
 * Created by mike on 15.03.2016.
 */
public class Iob {
    public double iobContrib = 0;
    public double activityContrib = 0;

    public Iob plus(Iob iob) {
        iobContrib += iob.iobContrib;
        activityContrib += iob.activityContrib;
        return this;
    }
}
