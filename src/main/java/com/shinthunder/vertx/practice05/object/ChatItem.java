package com.shinthunder.vertx.practice05.object;

import java.io.Serializable;
import java.util.UUID;

public class ChatItem implements Serializable {
    @Override
    public String toString() {
        return "\n    ChatItem{" + '\n'//
                + "         action = " + action + '\n'//
                + "         senderEmail = " + senderEmail + '\n'//
                + "         message = " + message + '\n'//
                + "         roomName = " + roomName + '\n'//
                + "         messageId = " + messageId + '\n'//
                + "         roomId = " + roomId + '\n'//
                + "     ";
    }

    private String action;
    private String senderEmail;
    private String message;
    private String roomName;
    private String messageId;  // UUID 형태의 고유한 메시지 ID
    private int roomId;
    private int userId;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

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

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}