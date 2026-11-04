package com.swipeclean.app;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(
    name = "PhotoManager",
    permissions = {
        @Permission(alias = "images", strings = { Manifest.permission.READ_MEDIA_IMAGES }),
        @Permission(alias = "storage", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
    }
)
public class PhotoManagerPlugin extends Plugin {

    private String alias() {
        return Build.VERSION.SDK_INT >= 33 ? "images" : "storage";
    }

    /* ---------- permission ---------- */
    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (getPermissionState(alias()) == PermissionState.GRANTED) {
            JSObject o = new JSObject();
            o.put("granted", true);
            call.resolve(o);
        } else {
            requestPermissionForAlias(alias(), call, "permCb");
        }
    }

    @PermissionCallback
    private void permCb(PluginCall call) {
        JSObject o = new JSObject();
        o.put("granted", getPermissionState(alias()) == PermissionState.GRANTED);
        call.resolve(o);
    }

    /* ---------- list photos ---------- */
    @PluginMethod
    public void getPhotos(final PluginCall call) {
        final int limit = call.getInt("limit", 3000);
        new Thread(() -> {
            try {
                ContentResolver cr = getContext().getContentResolver();
                Uri base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                String[] proj = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.MIME_TYPE
                };
                JSArray arr = new JSArray();
                try (Cursor c = cr.query(base, proj, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                    int n = 0;
                    while (c != null && c.moveToNext() && n < limit) {
                        long id = c.getLong(0);
                        long taken = c.getLong(4);
                        long date = taken > 0 ? taken : c.getLong(3) * 1000L;
                        JSObject o = new JSObject();
                        o.put("id", String.valueOf(id));
                        o.put("uri", ContentUris.withAppendedId(base, id).toString());
                        o.put("name", c.getString(1));
                        o.put("size", c.getLong(2));
                        o.put("date", date);
                        o.put("w", c.getInt(5));
                        o.put("h", c.getInt(6));
                        o.put("mime", c.getString(7));
                        arr.put(o);
                        n++;
                    }
                }
                JSObject res = new JSObject();
                res.put("photos", arr);
                call.resolve(res);
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    /* ---------- delete (system confirm dialog, Android 11+) ---------- */
    @PluginMethod
    public void deletePhotos(PluginCall call) {
        try {
            JSArray ids = call.getArray("ids");
            List<Uri> uris = new ArrayList<>();
            if (ids != null) {
                for (int i = 0; i < ids.length(); i++) {
                    long id = Long.parseLong(ids.getString(i));
                    uris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                }
            }
            if (uris.isEmpty()) {
                JSObject o = new JSObject();
                o.put("deleted", true);
                call.resolve(o);
                return;
            }
            PendingIntent pi = MediaStore.createDeleteRequest(getContext().getContentResolver(), uris);
            IntentSenderRequest req = new IntentSenderRequest.Builder(pi.getIntentSender()).build();
            Intent intent = new Intent(ActivityResultContracts.StartIntentSenderForResult.ACTION_INTENT_SENDER_REQUEST)
                .putExtra(ActivityResultContracts.StartIntentSenderForResult.EXTRA_INTENT_SENDER_REQUEST, req);
            startActivityForResult(call, intent, "deleteCb");
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @ActivityCallback
    private void deleteCb(PluginCall call, ActivityResult result) {
        JSObject o = new JSObject();
        o.put("deleted", result.getResultCode() == Activity.RESULT_OK);
        call.resolve(o);
    }

    /* ---------- upload to Google Drive ---------- */
    @PluginMethod
    public void uploadToDrive(final PluginCall call) {
        final String uri = call.getString("uri");
        final String name = call.getString("name", "photo.jpg");
        final String mime = call.getString("mime", "image/jpeg");
        final String token = call.getString("token");
        final String folder = call.getString("folderId", "");
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                String boundary = "----sc" + System.currentTimeMillis();
                URL url = new URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id");
                c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setChunkedStreamingMode(262144);
                c.setConnectTimeout(30000);
                c.setReadTimeout(120000);
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);

                JSONObject meta = new JSONObject();
                meta.put("name", name);
                if (folder != null && !folder.isEmpty()) {
                    meta.put("parents", new JSONArray().put(folder));
                }

                try (OutputStream os = c.getOutputStream()) {
                    String head = "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                        + meta.toString() + "\r\n--" + boundary + "\r\nContent-Type: " + mime + "\r\n\r\n";
                    os.write(head.getBytes("UTF-8"));
                    try (InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(uri))) {
                        if (in == null) throw new Exception("เปิดไฟล์ไม่ได้");
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = in.read(buf)) != -1) os.write(buf, 0, r);
                    }
                    os.write(("\r\n--" + boundary + "--").getBytes("UTF-8"));
                }

                int code = c.getResponseCode();
                String body = readAll(code < 400 ? c.getInputStream() : c.getErrorStream());
                if (code >= 300) {
                    call.reject("Drive " + code + ": " + body);
                } else {
                    call.resolve(new JSObject(body));
                }
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    /* ---------- download from Drive back into gallery ---------- */
    @PluginMethod
    public void downloadFromDrive(final PluginCall call) {
        final String fileId = call.getString("fileId");
        final String name = call.getString("name", "restored.jpg");
        final String mime = call.getString("mime", "image/jpeg");
        final String token = call.getString("token");
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL("https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media");
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(30000);
                c.setReadTimeout(120000);
                c.setRequestProperty("Authorization", "Bearer " + token);
                int code = c.getResponseCode();
                if (code >= 300) {
                    call.reject("Drive " + code + ": " + readAll(c.getErrorStream()));
                    return;
                }
                ContentResolver cr = getContext().getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, mime);
                v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SwipeClean");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri out = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (out == null) throw new Exception("สร้างไฟล์ในเครื่องไม่ได้");
                try (InputStream in = c.getInputStream(); OutputStream os = cr.openOutputStream(out)) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) != -1) os.write(buf, 0, r);
                }
                v.clear();
                v.put(MediaStore.Images.Media.IS_PENDING, 0);
                cr.update(out, v, null, null);
                JSObject res = new JSObject();
                res.put("uri", out.toString());
                res.put("id", String.valueOf(ContentUris.parseId(out)));
                call.resolve(res);
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
