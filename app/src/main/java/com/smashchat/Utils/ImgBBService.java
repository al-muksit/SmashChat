package com.smashchat.Utils;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.smashchat.BuildConfig;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ImgBBService handles uploading profile images to ImgBB via their API.
 */
public class ImgBBService {

    private static final String TAG = "ImgBBService";
    private static final String UPLOAD_URL = "https://api.imgbb.com/1/upload";
    private static final OkHttpClient client = new OkHttpClient();

    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onFailure(String error);
    }

    /**
     * Uploads a bitmap to ImgBB and returns the direct image URL.
     */
    public static void uploadImage(Bitmap bitmap, UploadCallback callback) {
        String apiKey = BuildConfig.IMGBB_API_KEY;
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onFailure("API Key is missing");
            return;
        }

        // Compress Bitmap to Base64
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
        byte[] imageBytes = outputStream.toByteArray();
        String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

        RequestBody formBody = new FormBody.Builder()
                .add("key", apiKey)
                .add("image", base64Image)
                .build();

        Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Upload failed", e);
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure("Server error: " + response.code());
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JSONObject jsonObject = new JSONObject(responseBody);
                    if (jsonObject.getBoolean("success")) {
                        String imageUrl = jsonObject.getJSONObject("data").getString("url");
                        callback.onSuccess(imageUrl);
                    } else {
                        callback.onFailure("Upload unsuccessful");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parsing error", e);
                    callback.onFailure("Parsing error");
                }
            }
        });
    }
}
