package com.smashchat.Models;

/**
 * Model class representing a single message in a chat.
 */
public class Messages {
    private String uId;
    private String message;
    private long timestamp;
    private int type; // 0 for text, 1 for smile emoji

    public Messages(String uId, String message, long timestamp) {
        this.uId = uId;
        this.message = message;
        this.timestamp = timestamp;
        this.type = 0;
    }

    public Messages(String uId, String message) {
        this.uId = uId;
        this.message = message;
        this.type = 0;
    }

    public Messages(String uId, String message, long timestamp, int type) {
        this.uId = uId;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
    }

    public Messages() {
    }

    public String getuId() {
        return uId;
    }

    public void setuId(String uId) {
        this.uId = uId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
