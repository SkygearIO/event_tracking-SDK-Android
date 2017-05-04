package io.skygear.skygear.eventtracking;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.skygear.skygear.Container;

public class SkygearTracker {

    private final static String DEFAULT_MOUNT_PATH = "/skygear_event_tracking";

    private final Container mContainer;
    private final Map<String, Object> mEnvironmentAttributes;
    private final Writer mWriter;
    private final Uri mEndpoint;

    public SkygearTracker(Container container) {
        this(container, DEFAULT_MOUNT_PATH);
    }

    public SkygearTracker(Container container, String mountPath) {
        mContainer = container;
        mEnvironmentAttributes = new HashMap<>();
        mEndpoint = buildEndpoint(mountPath);
        mWriter = new Writer(getContext(), mEndpoint);
        populateEnvironmentAttributes();
    }

    public void track(String eventName) {
        track(eventName, null);
    }

    public void track(String eventName, Map<String, Object> attributes) {
        if (eventName == null) {
            return;
        }

        HashMap<String, Object> event = new HashMap<>();
        Map<String, Object> sanitizedAttributes = sanitizeUserDefinedAttributes(attributes);
        if (sanitizedAttributes != null) {
            event.putAll(sanitizedAttributes);
        }
        event.putAll(mEnvironmentAttributes);
        event.put("_event_raw", eventName);
        event.put("_user_id", getCurrentUserId());
        Date trackedAt = new Date();
        event.put("_tracked_at", trackedAt);
        mWriter.write(event);
    }

    private Context getContext() {
        return mContainer.getContext();
    }

    private String getCurrentUserId() {
        if (mContainer.getCurrentUser() != null) {
            return mContainer.getCurrentUser().getId();
        }
        return null;
    }

    private Uri buildEndpoint(String mountPath) {
        Uri base = Uri.parse(mContainer.getConfig().getEndpoint());
        return base.buildUpon().encodedPath(mountPath).build();
    }

    private HashMap<String, Object> sanitizeUserDefinedAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }
        HashMap<String, Object> output = new HashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                output.put(key, value);
            } else if (value instanceof Number) {
                Number number = (Number) value;
                output.put(key, number.doubleValue());
            } else if (value instanceof String) {
                output.put(key, value);
            }
        }
        return output;
    }

    private void populateEnvironmentAttributes() {
        Context context = getContext();
        mEnvironmentAttributes.put("_app_id", Utils.getAppId(context));
        mEnvironmentAttributes.put("_app_version", Utils.getAppVersion(context));
        mEnvironmentAttributes.put("_app_build_number", Utils.getAppBuildNumber(context));
        mEnvironmentAttributes.put("_device_id", Utils.getDeviceId(context));
        mEnvironmentAttributes.put("_device_manufacturer", Utils.getDeviceManufacturer());
        mEnvironmentAttributes.put("_device_model", Utils.getDeviceModel());
        mEnvironmentAttributes.put("_device_os", Utils.getDeviceOS());
        mEnvironmentAttributes.put("_device_os_version", Utils.getDeviceOSVersion());
        mEnvironmentAttributes.put("_device_carrier", Utils.getDeviceCarrier(context));
        mEnvironmentAttributes.put("_device_locales", formatBCP47Tags(Utils.getDeviceLocales(context)));
        mEnvironmentAttributes.put("_device_locale", Utils.getDeviceLocale(context));
        mEnvironmentAttributes.put("_device_timezone", Utils.getDeviceTimeZone());
    }

    private String formatBCP47Tags(ArrayList<String> tags) {
        return TextUtils.join(",", tags);
    }

}
