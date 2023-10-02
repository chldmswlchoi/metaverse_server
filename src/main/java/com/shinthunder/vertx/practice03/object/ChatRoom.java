package com.shinthunder.vertx.practice03.object;

import io.vertx.core.json.JsonObject;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class ChatRoom  implements Serializable {
    private int roomId;
    private String roomName;
    private Set<ChatItem> chatItems = new HashSet<>();
    private Set<String> users = new HashSet<>();

    public String toJson() {
        JsonObject json = new JsonObject()
                .put("roomId", roomId)
                .put("roomName", roomName);
        // 필요하다면 chatItems, users 등의 정보도 추가
        return json.encode();
    }

    public static ChatRoom fromJson(String jsonString) {
        JsonObject json = new JsonObject(jsonString);
        ChatRoom room = new ChatRoom(json.getInteger("roomId"), json.getString("roomName"));
        // 필요하다면 chatItems, users 등의 정보도 로드
        return room;
    }

    public ChatRoom(int roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public Set<ChatItem> getChatItems() {
        return chatItems;
    }

    public void addChatItem(ChatItem chatItem) {
        chatItems.add(chatItem);
    }

    public void removeChatItem(ChatItem chatItem) {
        chatItems.remove(chatItem);
    }

    public Set<String> getUsers() {
        return users;
    }

    public void addUser(String userName) {
        users.add(userName);
    }

    public void removeUser(String userName) {
        users.remove(userName);
    }
}

