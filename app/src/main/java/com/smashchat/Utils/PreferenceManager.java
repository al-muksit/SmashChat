package com.smashchat.Utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * PreferenceManager handles storing and retrieving local user data
 * for faster UI loading and offline access to basic profile info.
 */
public class PreferenceManager {
    private static final String PREF_NAME = "SmashChatPrefs";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_PROFILE_PIC = "profilePic";
    private static final String KEY_EMAIL = "email";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public PreferenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void saveUserData(String name, String email, String profilePic) {
        editor.putString(KEY_USER_NAME, name);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_PROFILE_PIC, profilePic);
        editor.apply();
    }

    public String getUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, "");
    }

    public String getEmail() {
        return sharedPreferences.getString(KEY_EMAIL, "");
    }

    public String getProfilePic() {
        return sharedPreferences.getString(KEY_PROFILE_PIC, "");
    }

    public void clear() {
        editor.clear().apply();
    }
}
