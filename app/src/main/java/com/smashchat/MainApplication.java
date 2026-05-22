package com.smashchat;

import android.app.Application;
import com.google.firebase.database.FirebaseDatabase;

/**
 * MainApplication initializes global settings for the app.
 */
public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Enable disk persistence for Firebase Realtime Database
        // This allows the app to handle data changes even after internet reconnection
        // and keep track of state while offline.
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
