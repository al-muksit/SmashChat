package com.smashchat.Models;

/**
 * Model class representing a User in the application.
 */
public class Users {
    private String profilePic;
    private String userName;
    private String email;
    private String password;
    private String userId;
    private String lastMessage;

    /**
     * Full constructor for a user.
     */
    public Users(String profilePic, String userName, String email, String password, String userId, String lastMessage) {
        this.profilePic = profilePic;
        this.userName = userName;
        this.email = email;
        this.password = password;
        this.userId = userId;
        this.lastMessage = lastMessage;
    }

    /**
     * Default constructor required for calls to DataSnapshot.getValue(Users.class)
     */
    public Users() {
    }

    /**
     * Constructor used during Sign-up.
     */
    public Users(String userName, String email, String password) {
        this.userName = userName;
        this.email = email;
        this.password = password;
    }

    // Getters and Setters

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
}
