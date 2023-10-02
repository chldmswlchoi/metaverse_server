package com.shinthunder.vertx.practice00_eunji_t1.object;

import java.io.Serializable;

public class ClientAction implements Serializable {
    private String action;
    private int x;
    private int y;
    private int userId;
    private String nickName;
    private int direction;

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }



    // Getter 및 Setter 메서드

    // 기타 필요한 메서드
}