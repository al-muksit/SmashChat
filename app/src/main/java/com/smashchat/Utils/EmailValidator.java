package com.smashchat.Utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class EmailValidator {

    private static final String API_KEY = "3cbe741011d34d5db3b21ebefdb22d54";
    private static final String API_URL = "https://emailvalidation.abstractapi.com/v1/?api_key=" + API_KEY + "&email=";

    /**
     * Checks if the email is syntactically valid and actually exists in the real world.
     */
    public static void validateEmail(String email, ValidationListener listener) {
        if (email == null || email.isEmpty()) {
            listener.onResult(false, "Email cannot be empty");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            listener.onResult(false, "Invalid email format");
            return;
        }

        // Deep "Real World" existence check using Abstract API
        OkHttpClient client = new OkHttpClient();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        
        Request request = new Request.Builder()
                .url(API_URL + email)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // If API fails (e.g. no internet), we fallback to syntax-only validation
                mainHandler.post(() -> listener.onResult(true, "Syntax valid (API offline)"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseData = response.body().string();
                        JSONObject json = new JSONObject(responseData);
                        
                        // "deliverability" can be DELIVERABLE, UNDELIVERABLE, or UNKNOWN
                        String deliverability = json.optString("deliverability", "UNKNOWN");

                        mainHandler.post(() -> {
                            if (deliverability.equals("UNDELIVERABLE")) {
                                listener.onResult(false, "This email address does not exist. Please enter a real email.");
                            } else {
                                // We allow DELIVERABLE and UNKNOWN (to avoid blocking users due to API limitations)
                                listener.onResult(true, "Email is valid");
                            }
                        });

                    } catch (JSONException e) {
                        mainHandler.post(() -> listener.onResult(true, "Syntax valid (JSON Error)"));
                    }
                } else {
                    // API error fallback
                    mainHandler.post(() -> listener.onResult(true, "Syntax valid (API Error)"));
                }
                response.close();
            }
        });
    }

    public interface ValidationListener {
        void onResult(boolean isValid, String message);
    }
}
