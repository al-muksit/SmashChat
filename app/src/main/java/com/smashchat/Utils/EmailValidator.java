package com.smashchat.Utils;

import android.util.Patterns;
import java.util.regex.Pattern;

public class EmailValidator {

    /**
     * Checks if the email is syntactically valid and potentially exists.
     * In a production app, you would integrate an API like ZeroBounce or Abstract API here.
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

        // Placeholder for "Real World" existence check.
        // For now, we simulate a successful check for all syntactically valid emails.
        // You would perform an OkHttp request to an email verification service here.
        
        /* 
        Example with a hypothetical API:
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.emailverification.com/v1/verify?email=" + email + "&api_key=YOUR_KEY")
                .build();
        client.newCall(request).enqueue(...)
        */

        listener.onResult(true, "Email is valid");
    }

    public interface ValidationListener {
        void onResult(boolean isValid, String message);
    }
}
