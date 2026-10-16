package in.atulpatare.ranobem.tracking;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Start and finish dates are plain calendar days, stored as yyyymmdd ints with 0 for no date, so
 * they never shift with the time zone and map straight onto a tracker's year, month and day.
 */
public final class TrackDates {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private TrackDates() {
    }

    public static int today() {
        Calendar now = Calendar.getInstance();
        return of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
    }

    public static int of(int year, int month, int day) {
        if (year <= 0) return 0;
        return year * 10000 + Math.max(month, 0) * 100 + Math.max(day, 0);
    }

    public static int year(int date) {
        return date / 10000;
    }

    public static int month(int date) {
        return date / 100 % 100;
    }

    public static int day(int date) {
        return date % 100;
    }

    // the date picker works in UTC midnights
    public static long toUtcMillis(int date) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.clear();
        calendar.set(year(date), Math.max(month(date), 1) - 1, Math.max(day(date), 1));
        return calendar.getTimeInMillis();
    }

    public static int fromUtcMillis(long millis) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.setTimeInMillis(millis);
        return of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    public static String format(int date) {
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM);
        format.setTimeZone(UTC);
        return format.format(toUtcMillis(date));
    }
}
