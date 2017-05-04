package io.skygear.skygear.eventtracking;

import android.content.Context;
import android.net.Uri;
import android.support.v4.util.AtomicFile;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class Writer {
    private static final String LOGTAG = "SETWriter";
    private static final String DEFAULT_FILE_PATH = "skygear_event_tracking.json";
    private static final int DEFAULT_FLUSH_LIMIT = 10;
    private static final long DEFAULT_TIMER_INTERVAL = 30; // in seconds
    private static final int DEFAULT_UPLOAD_LIMIT = 20;
    private static final int DEFAULT_MAX_LENGTH = 1000;

    private final Context mContext;
    private final ExecutorService mExecutor;
    private final SimpleDateFormat mDateFormatter;
    private final Uri mEndpoint;
    private final AtomicFile mFile;
    private final ScheduledExecutorService mTimer;
    private ArrayList<HashMap<String, Object>> mEvents;

    public Writer(Context context, Uri endpoint) {
        mContext = context;
        mExecutor = Executors.newSingleThreadExecutor();
        mTimer = Executors.newSingleThreadScheduledExecutor();
        mDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        mEndpoint = endpoint;
        mFile = new AtomicFile(getFile());
        mEvents = new ArrayList<>();

        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                Writer.this.doRestore();
                Writer.this.dropIfNeeded();
                Writer.this.flushIfHasSomeEvents();
            }
        });

        mTimer.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                mExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        Writer.this.flushIfHasSomeEvents();
                    }
                });
            }
        }, 0L, DEFAULT_TIMER_INTERVAL, TimeUnit.SECONDS);
    }

    private File getFile() {
        File dataDir = mContext.getApplicationContext().getFilesDir();
        File file = new File(dataDir, DEFAULT_FILE_PATH);
        return file;
    }

    public void write(final HashMap<String, Object> event) {
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                Writer.this.doWrite(event);
            }
        });
    }

    private void doRestore() {
        try {
            byte[] bytes = mFile.readFully();
            JSONObject jsonObject = Utils.fromBytesTOJSONObject(bytes);
            JSONArray jsonArray = jsonObject.getJSONArray("events");
            for (int i = 0; i < jsonArray.length(); ++i) {
                JSONObject eventJson = jsonArray.getJSONObject(i);
                HashMap<String, Object> event = fromJSONObject(eventJson);
                if (event != null) {
                    mEvents.add(event);
                }
            }
            Log.d(LOGTAG, "doRestore: stored: " + mEvents.size());
        } catch (Exception e) {
            if (!(e instanceof FileNotFoundException)) {
                Log.e(LOGTAG, "doRestore", e);
            }
        }
    }

    private Date parseDateFromJSONObject(JSONObject jsonObject) {
        try {
            String type = jsonObject.getString("$type");
            if ("date".equals(type)) {
                String rfc3339 = jsonObject.getString("$date");
                Date date = mDateFormatter.parse(rfc3339);
                return date;
            }
        } catch (Exception e) {

        }
        return null;
    }

    private JSONObject serializeDateToJSONObject(Date date) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("$type", "date");
        jsonObject.put("$date", mDateFormatter.format(date));
        return jsonObject;
    }

    private HashMap<String, Object> fromJSONObject(JSONObject jsonObject) throws JSONException {
        HashMap<String, Object> output = new HashMap<>();
        Iterator<String> iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = jsonObject.get(key);
            if (value instanceof Boolean) {
                output.put(key, value);
            } else if (value instanceof Number) {
                output.put(key, ((Number) value).doubleValue());
            } else if (value instanceof String) {
                output.put(key, value);
            } else if (value instanceof JSONObject) {
                Date date = parseDateFromJSONObject((JSONObject) value);
                if (date != null) {
                    output.put(key, date);
                }
            }
        }
        return output;
    }

    private JSONObject toJSONObject(HashMap<String, Object> event) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        for (Map.Entry<String, Object> entry : event.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                jsonObject.put(key, (boolean) value);
            } else if (value instanceof Number) {
                jsonObject.put(key, (double) value);
            } else if (value instanceof String) {
                jsonObject.put(key, (String) value);
            } else if (value instanceof Date) {
                jsonObject.put(key, serializeDateToJSONObject((Date) value));
            }
        }
        return jsonObject;
    }

    private JSONObject serializeEvents(List<HashMap<String, Object>> events) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        for (HashMap<String, Object> event : events) {
            JSONObject eventJSON = toJSONObject(event);
            if (eventJSON != null) {
                jsonArray.put(eventJSON);
            }
        }
        jsonObject.put("events", jsonArray);
        return jsonObject;
    }

    private void doWrite(HashMap<String, Object> event) {
        try {
            addAndDrop(event);
            persist();
            flushIfEnough();
        } catch (Exception e) {
            Log.e(LOGTAG, "doWrite", e);
        }
    }

    private void addAndDrop(HashMap<String, Object> event) {
        mEvents.add(event);
        Log.d(LOGTAG, "addAndDrop:add: " + mEvents.size());
        dropIfNeeded();
    }

    private void dropIfNeeded() {
        if (mEvents.size() > DEFAULT_MAX_LENGTH) {
            int originalSize = mEvents.size();
            int startIndex = originalSize - DEFAULT_MAX_LENGTH;
            int endIndex = originalSize;
            mEvents = new ArrayList<>(mEvents.subList(startIndex, endIndex));
            Log.d(LOGTAG, "drop: " + startIndex);
        }
    }

    private void persist() throws Exception {
        JSONObject jsonObject = serializeEvents(mEvents);
        FileOutputStream outputStream = null;
        try {
            outputStream = mFile.startWrite();
            InputStream inputStream = new ByteArrayInputStream(Utils.fromJSONObjectToBytes(jsonObject));
            Utils.transferTo(inputStream, outputStream);
            mFile.finishWrite(outputStream);
        } catch (Exception e) {
            mFile.failWrite(outputStream);
            throw e;
        }
    }

    private <T> int calculateEndIndex(ArrayList<T> arrayList, int limit) {
        int size = arrayList.size();
        return Math.min(size, limit);
    }

    private void flushIfEnough() throws IOException, JSONException {
        if (mEvents.size() < DEFAULT_FLUSH_LIMIT) {
            return;
        }
        flush();
    }

    private void flushIfHasSomeEvents() {
        if (mEvents.size() <= 0) {
            return;
        }
        try {
            flush();
            Log.d(LOGTAG, "flushIfHasSomeEvents success");
        } catch (Exception e) {
            Log.e(LOGTAG, "flushIfHasSomeEvents error", e);
        }
    }

    private void flush() throws IOException, JSONException {
        int endIndex = calculateEndIndex(mEvents, DEFAULT_UPLOAD_LIMIT);
        List<HashMap<String, Object>> events = mEvents.subList(0, endIndex);
        JSONObject jsonObject = serializeEvents(events);
        try {
            upload(jsonObject);
            mEvents = new ArrayList<>(mEvents.subList(events.size(), mEvents.size()));
        } catch (Exception e) {
            throw e;
        }
    }

    private void upload(JSONObject jsonObject) throws IOException {
        URL javaURL = Utils.fromUriToURL(mEndpoint);
        HttpURLConnection urlConnection = null;
        try {
            byte[] requestBody = Utils.fromJSONObjectToBytes(jsonObject);

            urlConnection = (HttpURLConnection) javaURL.openConnection();
            urlConnection.setUseCaches(false);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setFixedLengthStreamingMode(requestBody.length);

            InputStream requestBodyStream = new ByteArrayInputStream(requestBody);
            OutputStream outputStream = new BufferedOutputStream(urlConnection.getOutputStream());
            Utils.transferTo(requestBodyStream, outputStream);
            outputStream.close();

            InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
            Utils.readToEnd(inputStream);
            inputStream.close();
            int statusCode = urlConnection.getResponseCode();
            Log.d(LOGTAG, "upload: " + statusCode);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
