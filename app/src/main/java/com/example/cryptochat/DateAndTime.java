package com.example.cryptochat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class DateAndTime {

    private HashMap<Integer, String> months = new HashMap<>();
    private Calendar calendar;

    public DateAndTime() {
        months.put(1, "Jan");
        months.put(2, "Feb");
        months.put(3, "Mar");
        months.put(4, "Apr");
        months.put(5, "May");
        months.put(6, "Jun");
        months.put(7, "Jul");
        months.put(8, "Aug");
        months.put(9, "Sep");
        months.put(10, "Oct");
        months.put(11, "Nov");
        months.put(12, "Dec");
        calendar = Calendar.getInstance();
    }

    public String getTime() {
        Calendar calendar = Calendar.getInstance(); // Get the current time dynamically
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    public String getTimeWithSeconds() {
        Calendar calendar = Calendar.getInstance(); // Get the current time dynamically
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    public String getDATE() {
        Calendar calendar = Calendar.getInstance(); // Get the current time dynamically
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yy", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

//    public int TimeDifference(String t1, String t2) {
//        String[] timeParts1 = t1.split(":");
//        String[] timeParts2 = t2.split(":");
//
//        int hour1 = Integer.parseInt(timeParts1[0]);
//        int minute1 = Integer.parseInt(timeParts1[1]);
//
//        int hour2 = Integer.parseInt(timeParts2[0]);
//        int minute2 = Integer.parseInt(timeParts2[1]);
//
//        return Math.abs((hour1 - hour2) * 60 + (minute1 - minute2));
//    }
}