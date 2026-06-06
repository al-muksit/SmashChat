package com.smashchat.Models;

/**
 * Model class representing a User in the application.
 */
public class Users {
    private String profilePic;
    private String userName;
    private String email;
    private String userId;
    private String lastMessage;
    private String phone;
    private String address;
    private String customId;
    private String status;
    private long lastMessageTime;
    private boolean read = true;
    private boolean muted = false;

    /**
     * Full constructor for a user.
     */
    public Users(String profilePic, String userName, String email, String userId, String lastMessage, String phone, String address, String customId, String status) {
        this.profilePic = profilePic;
        this.userName = userName;
        this.email = email;
        this.userId = userId;
        this.lastMessage = lastMessage;
        this.phone = phone;
        this.address = address;
        this.customId = customId;
        this.status = status;
    }

    /**
     * Full constructor for a user including lastMessageTime.
     */
    public Users(String profilePic, String userName, String email, String userId, String lastMessage, String phone, String address, String customId, String status, long lastMessageTime) {
        this.profilePic = profilePic;
        this.userName = userName;
        this.email = email;
        this.userId = userId;
        this.lastMessage = lastMessage;
        this.phone = phone;
        this.address = address;
        this.customId = customId;
        this.status = status;
        this.lastMessageTime = lastMessageTime;
    }

    /**
     * Full constructor for a user including lastMessageTime and read status.
     */
    public Users(String profilePic, String userName, String email, String userId, String lastMessage, String phone, String address, String customId, String status, long lastMessageTime, boolean read) {
        this.profilePic = profilePic;
        this.userName = userName;
        this.email = email;
        this.userId = userId;
        this.lastMessage = lastMessage;
        this.phone = phone;
        this.address = address;
        this.customId = customId;
        this.status = status;
        this.lastMessageTime = lastMessageTime;
        this.read = read;
    }

    /**
     * Default constructor required for calls to DataSnapshot.getValue(Users.class)
     */
    public Users() {
    }

    /**
     * Constructor used during Sign-up.
     */
    public Users(String userName, String email) {
        this.userName = userName;
        this.email = email;
        this.status = "Offline";
        this.lastMessageTime = 0;
        this.read = true;
    }

    // Getters and Setters

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProfilePic() {
        return profilePic;
    }

    public void setProfilePic(String profilePic) {
        this.profilePic = profilePic;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCustomId() {
        return customId;
    }

    public void setCustomId(String customId) {
        this.customId = customId;
    }
}
