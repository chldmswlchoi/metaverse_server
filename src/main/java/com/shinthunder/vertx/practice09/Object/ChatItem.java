package com.shinthunder.vertx.practice09.Object;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 이 클래스에 작성한 메소드들 절대 아무것도 지우지 말 것.
 * 이유 :
 * <p>
 * 주의: ChatRoom 객체를 JSON 표현으로 변환할 때
 * JSON 직렬화 라이브러리(예: Jackson, Gson, 또는 Vert.x의 JSON 처리 메커니즘)에 의해
 * 이 메소드들이 자동으로 호출됩니다. 이로 인해 이 객체를 사용하는 다른 코드에서 명시적으로 이 메소드들을 호출되지 않아도
 * 내부적으로 여기 서술된 메소드들이 JSON 출력에 포함됩니다.
 */
public class ChatItem implements Serializable {
    private Set<ChatRoomActiveStatus> chatRoomActiveStatus = new HashSet<>();
    private String action;
    private String senderEmail;
    private String message;
    private String roomName;
    private String timestamp;
    private int messageId;
    private int roomId;
    private int userId;

    @Override
    public String toString() {
        return "\n    ChatItem{" + '\n'//
                + "         action = " + action + '\n'//
                + "         senderEmail = " + senderEmail + '\n'//
                + "         roomName = " + roomName + '\n'//
                + "         message = " + message + '\n'//
                + "         messageId = " + messageId + '\n'//
                + "         roomId = " + roomId + '\n'//
                + "         userId = " + userId + '\n'//
                + "         timestamp = " + timestamp + '\n'//
                + "         chatRoomActiveStatus = " + chatRoomActiveStatus.toString() + '\n'//
                + "     }\n";
    }

    public void setChatRoomActiveStatus(Set<ChatRoomActiveStatus> chatRoomActiveStatus) {
        this.chatRoomActiveStatus = chatRoomActiveStatus;
    }

    public int getMessageId() {
        return messageId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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

    public Set<ChatRoomActiveStatus> getChatRoomActiveStatus() {
        return chatRoomActiveStatus;
    }
}