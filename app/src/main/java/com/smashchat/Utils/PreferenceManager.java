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
    private static final String KEY_DARK_MODE = "darkMode_v2"; // Using new key for int mode
    private static final String KEY_ACTIVE_STATUS = "activeStatus";
    private static final String KEY_CHAT_HISTORY = "chatHistory";
    public static final int THEME_OFF = 0;
    public static final int THEME_ON = 1;
    public static final int THEME_SYSTEM = 2;

    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public PreferenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void setDarkModeTheme(int mode) {
        editor.putInt(KEY_DARK_MODE, mode);
        editor.apply();
    }

    public int getDarkModeTheme() {
        return sharedPreferences.getInt(KEY_DARK_MODE, THEME_SYSTEM); // Default to System
    }

    public boolean isDarkMode() {
        int mode = getDarkModeTheme();
        if (mode == THEME_SYSTEM) {
            return false; // This is used for legacy checks, actual theme applied via AppCompatDelegate
        }
        return mode == THEME_ON;
    }

    public void setActiveStatusEnabled(boolean enabled) {
        editor.putBoolean(KEY_ACTIVE_STATUS, enabled);
        editor.apply();
    }

    public boolean isActiveStatusEnabled() {
        return sharedPreferences.getBoolean(KEY_ACTIVE_STATUS, true); // Default to ON
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

    public void saveChatHistory(java.util.ArrayList<com.smashchat.Models.Users> users) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(users);
        editor.putString(KEY_CHAT_HISTORY, json);
        editor.apply();
    }

    public java.util.ArrayList<com.smashchat.Models.Users> getChatHistory() {
        String json = sharedPreferences.getString(KEY_CHAT_HISTORY, null);
        if (json == null) return new java.util.ArrayList<>();
        
        com.google.gson.Gson gson = new com.google.gson.Gson();
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.ArrayList<com.smashchat.Models.Users>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void clear() {
        editor.clear().apply();
    }

    public void setDarkMode(boolean isChecked) {

    }
}
