package info.nightscout.danar.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import info.nightscout.danaaps.calc.Iob;
import info.nightscout.danaaps.calc.IobCalc;

import java.util.Date;

@DatabaseTable(tableName = "Bolus")
public class Bolus {
    public long getTimeIndex() {
        return (long) Math.ceil(timeStart.getTime() / 60000d );
    }

    public void setTimeIndex(long timeIndex) {
        this.timeIndex = timeIndex;
    }

    @DatabaseField(id = true, useGetSet = true)
    public long timeIndex;


    @DatabaseField
    public Date timeStart;

    @DatabaseField
    public double amount;

    public Iob calcIobOpenAPS() {
        IobCalc calc = new IobCalc(timeStart,amount,new Date());
        calc.setBolusDiaTimesTwo();
        Iob iob = calc.invoke();

        return iob;
    }
    public Iob calcIob() {
        IobCalc calc = new IobCalc(timeStart,amount,new Date());
        Iob iob = calc.invoke();

        return iob;
    }

    public long getMsAgo() {
        return new Date().getTime() - timeStart.getTime();
    }
}
