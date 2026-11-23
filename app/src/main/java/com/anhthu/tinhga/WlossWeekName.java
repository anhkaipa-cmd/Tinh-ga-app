package com.anhthu.tinhga;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Week label for an incubation date: Monday to Sunday, local time. */
public final class WlossWeekName {
    private WlossWeekName() {}

    public static String forIncubationDate(long dateMillis) {
        Calendar monday=Calendar.getInstance();
        monday.setTimeInMillis(dateMillis);
        int dayOfWeek=monday.get(Calendar.DAY_OF_WEEK);
        int daysSinceMonday=(dayOfWeek+5)%7;
        monday.add(Calendar.DAY_OF_MONTH,-daysSinceMonday);

        Calendar sunday=(Calendar)monday.clone();
        sunday.add(Calendar.DAY_OF_MONTH,6);

        SimpleDateFormat format=new SimpleDateFormat("dd-MM",Locale.US);
        return "wloss "+format.format(new Date(monday.getTimeInMillis()))
            +"-"+format.format(new Date(sunday.getTimeInMillis()))+".xlsx";
    }
}
