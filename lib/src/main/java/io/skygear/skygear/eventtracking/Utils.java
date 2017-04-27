package io.skygear.skygear.eventtracking;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.LocaleList;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

public class Utils {

    private Utils() {

    }

    public static String getAppId(Context context) {
        return context.getApplicationContext().getPackageName();
    }

    public static String getAppVersion(Context context) {
        try {
            PackageManager pm = context.getApplicationContext().getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(getAppId(context), 0);
            return packageInfo.versionName;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getAppBuildNumber(Context context) {
        try {
            PackageManager pm = context.getApplicationContext().getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(getAppId(context), 0);
            int versionCode = packageInfo.versionCode;
            return Integer.toString(versionCode);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getDeviceId(Context context) {
        return Settings.Secure.getString(context.getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static String getDeviceManufacturer() {
        return Build.MANUFACTURER;
    }

    public static String getDeviceModel() {
        return Build.MODEL;
    }

    public static String getDeviceOS() {
        return "android";
    }

    public static String getDeviceOSVersion() {
        return Build.VERSION.RELEASE;
    }

    public static String getDeviceCarrier(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getNetworkOperatorName();
    }

    public static ArrayList<String> getDeviceLocales(Context context) {
        ArrayList<String> output = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 24) {
            LocaleList localeList = context.getApplicationContext().getResources().getConfiguration().getLocales();
            for (int i = 0; i < localeList.size(); ++i) {
                Locale locale = localeList.get(i);
                output.add(toBCP47LanguageTag(locale));
            }
        } else {
            Locale locale = context.getApplicationContext().getResources().getConfiguration().locale;
            output.add(toBCP47LanguageTag(locale));
        }
        return output;
    }

    public static String getDeviceLocale(Context context) {
        ArrayList<String> tags = getDeviceLocales(context);
        if (tags.size() >= 1) {
            return tags.get(0);
        }
        return null;
    }

    public static String getDeviceTimeZone() {
        return TimeZone.getDefault().getID();
    }

    private static String toBCP47LanguageTag(Locale loc) {
        if (Build.VERSION.SDK_INT >= 21) {
            return loc.toLanguageTag();
        }
        return toBCP47LanguageTagCompat(loc);
    }

    /*
     * Copy from https://github.com/apache/cordova-plugin-globalization/blob/83f6cce89128cc569985681a05b92e1ef516fd0c/src/android/Globalization.java
     */
    private static String toBCP47LanguageTagCompat(Locale loc) {
        final char SEP = '-';       // we will use a dash as per BCP 47
        String language = loc.getLanguage();
        String region = loc.getCountry();
        String variant = loc.getVariant();

        // special case for Norwegian Nynorsk since "NY" cannot be a variant as per BCP 47
        // this goes before the string matching since "NY" wont pass the variant checks
        if( language.equals("no") && region.equals("NO") && variant.equals("NY")){
            language = "nn";
            region = "NO";
            variant = "";
        }

        if( language.isEmpty() || !language.matches("\\p{Alpha}{2,8}")){
            language = "und";       // Follow the Locale#toLanguageTag() implementation
            // which says to return "und" for Undetermined
        }else if(language.equals("iw")){
            language = "he";        // correct deprecated "Hebrew"
        }else if(language.equals("in")){
            language = "id";        // correct deprecated "Indonesian"
        }else if(language.equals("ji")){
            language = "yi";        // correct deprecated "Yiddish"
        }

        // ensure valid country code, if not well formed, it's omitted
        if (!region.matches("\\p{Alpha}{2}|\\p{Digit}{3}")) {
            region = "";
        }

        // variant subtags that begin with a letter must be at least 5 characters long
        if (!variant.matches("\\p{Alnum}{5,8}|\\p{Digit}\\p{Alnum}{3}")) {
            variant = "";
        }

        StringBuilder bcp47Tag = new StringBuilder(language);
        if (!region.isEmpty()) {
            bcp47Tag.append(SEP).append(region);
        }
        if (!variant.isEmpty()) {
            bcp47Tag.append(SEP).append(variant);
        }

        return bcp47Tag.toString();
    }

}
