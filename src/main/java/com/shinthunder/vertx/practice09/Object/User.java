package com.shinthunder.vertx.practice09.Object;

public class User {
    private Integer userId;
    private String userEmail;
    private String userNickname;

    @Override
    public String toString() {
        return "\n         User{" + '\n'//
                + "              userId = " + userId + '\n'//
                + "              userEmail = " + userEmail + '\n'//
                + "              userNickname = " + userNickname + '\n'//
                + "          }\n";
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserNickname() {
        return userNickname;
    }

    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }

}
