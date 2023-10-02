package com.shinthunder.vertx.practice00_eunji_t1.object;

import java.io.Serializable;
import java.util.UUID;

public class ChatItem  implements Serializable {
    private String action;
    private String senderName;
    private String message;
    private String roomName;
    private String messageId;  // UUID 형태의 고유한 메시지 ID
    private int roomId;

    public ChatItem() {
        this.messageId = UUID.randomUUID().toString();
    }

    public String getMessageId() {
        return messageId;
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}