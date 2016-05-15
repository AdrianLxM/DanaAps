package info.nightscout.danaaps.tempBasal;

public class Basal {
    public Double   rate;                   //Temp Basal Rate for (U/hr) mode
    public Integer  ratePercent;            //Temp Basal Rate for "percent" of normal basal
    public Integer  duration;               //Duration of Temp

    public String log() {
        return "Rate: " + rate + " RatePercent: " + ratePercent + " Duration: " + duration;
    }
}
