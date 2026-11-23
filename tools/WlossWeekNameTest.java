import com.anhthu.tinhga.WlossWeekName;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class WlossWeekNameTest {
    private static long day(int yyyy,int mm,int dd) {
        Calendar cal=Calendar.getInstance();
        cal.clear();
        cal.set(yyyy,mm-1,dd,12,0,0);
        return cal.getTimeInMillis();
    }

    private static void check(int year,int month,int date,String expected) {
        String name=WlossWeekName.forIncubationDate(day(year,month,date));
        if(!expected.equals(name)) {
            throw new AssertionError("Expected "+expected+", found "+name);
        }
    }

    public static void main(String[] args) {
        check(2026,11,23,"wloss 23-11-29-11.xlsx");
        check(2026,11,26,"wloss 23-11-29-11.xlsx");
        check(2026,11,27,"wloss 23-11-29-11.xlsx");
        check(2026,11,28,"wloss 23-11-29-11.xlsx");
        check(2026,11,29,"wloss 23-11-29-11.xlsx");
        check(2026,11,30,"wloss 30-11-06-12.xlsx");
        check(2027,1,1,"wloss 28-12-03-01.xlsx");
        System.out.println("PASS WLOSS Monday-Sunday weekly Excel filenames and month/year boundaries");
    }
}
