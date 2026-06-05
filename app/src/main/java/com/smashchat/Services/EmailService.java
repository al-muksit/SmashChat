package com.smashchat.Services;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EmailService {

    private static final String SERVICE_ID = "service_k3ugmyi";
    private static final String TEMPLATE_ID = "template_l0chvtg";
    private static final String PUBLIC_KEY = "pBVW_GDR-jgsQMdLH";
    private static final String API_URL = "https://api.emailjs.com/api/v1.0/email/send";

    public interface EmailCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static void sendVerificationEmail(String toEmail, String code, EmailCallback callback) {
        OkHttpClient client = new OkHttpClient();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            JSONObject json = new JSONObject();
            json.put("service_id", SERVICE_ID);
            json.put("template_id", TEMPLATE_ID);
            json.put("user_id", PUBLIC_KEY);

            JSONObject templateParams = new JSONObject();
            templateParams.put("email", toEmail);
            templateParams.put("passcode", code);
            templateParams.put("time", "5 minutes");
            json.put("template_params", templateParams);

            RequestBody body = RequestBody.create(
                    json.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    mainHandler.post(() -> callback.onFailure(e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        mainHandler.post(callback::onSuccess);
                    } else {
                        String errorMsg = response.body() != null ? response.body().string() : "Unknown error";
                        mainHandler.post(() -> callback.onFailure("Failed: " + response.code() + " " + errorMsg));
                    }
                    response.close();
                }
            });

        } catch (JSONException e) {
            callback.onFailure("JSON Error: " + e.getMessage());
        }
    }
}
