package com.shinthunder.vertx.practice09.Object;

import java.io.Serializable;

public class ChatRoomActiveStatus implements Serializable {
    @Override
    public String toString() {
        return "\n           ChatRoomActiveStatus{" + '\n'
                + "                roomId = " + roomId + '\n'//
                + "                userId = " + userId + '\n'//
                + "                isWatching = " + isWatching + '\n'//
                + "                lastWatchingAt = " + lastWatchingAt + '\n'//
                + "            }";
    }

    private int roomId;
    private int userId;
    private int isWatching;
    private String lastWatchingAt; // timestamp

    public ChatRoomActiveStatus() {
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getIsWatching() {
        return isWatching;
    }

    public void setIsWatching(int isWatching) {
        this.isWatching = isWatching;
    }

    public String getLastWatchingAt() {
        return lastWatchingAt;
    }

    public void setLastWatchingAt(String lastWatchingAt) {
        this.lastWatchingAt = lastWatchingAt;
    }
}
