package info.nightscout.danar.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;

import info.nightscout.danaapp.calc.IobCalc;

/**
 * Created by mike on 28.02.2016.
 */
@DatabaseTable(tableName = "Treatments")
public class Treatment {
    public long getTimeIndex() {
        return (long) Math.ceil(created_at.getTime() / 60000d);
    }

    public void setTimeIndex(long timeIndex) {
        this.timeIndex = timeIndex;
    }

    @DatabaseField(id = true, useGetSet = true)
    public long timeIndex;


    @DatabaseField
    public String _id;

    @DatabaseField
    public Date created_at;

    @DatabaseField
    public double insulin;

    @DatabaseField
    public double carbs;

    public String log() {
        return "timeIndex: " + timeIndex + "_id: " + _id + " insulin: " + insulin + " carbs: " + carbs + " created_at: " + created_at.toString();
    }

    public void copyFrom(Treatment t) {
        this._id = t._id;
        this.created_at = t.created_at;
        this.insulin = t.insulin;
        this.carbs = t.carbs;
    }

    public IobCalc.Iob calcIobOpenAPS() {
        IobCalc calc = new IobCalc(created_at,insulin,new Date());
        calc.setBolusDiaTimesTwo();
        IobCalc.Iob iob = calc.invoke();

        return iob;
    }
    public IobCalc.Iob calcIob() {
        IobCalc calc = new IobCalc(created_at,insulin,new Date());
        IobCalc.Iob iob = calc.invoke();

        return iob;
    }

    public long getMsAgo() {
        return new Date().getTime() - created_at.getTime();
    }

}
