package com.shinthunder.vertx.practice00_eunji_t1.object;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;

import java.util.HashSet;
import java.util.Set;

public class ChatRoom_V0 {
    private int roomId;
    private String roomName;
    private Set<ChatItem> chatItems = new HashSet<>();
    private final Set<ServerWebSocket> usersSockets = new HashSet<>();

    public String toJson() {
        JsonObject json = new JsonObject()
                .put("roomId", roomId)
                .put("roomName", roomName);
        // 필요하다면 chatItems, usersSockets 등의 정보도 추가
        return json.encode();
    }

    public static ChatRoom_V0 fromJson(String jsonString) {
        JsonObject json = new JsonObject(jsonString);
        ChatRoom_V0 room = new ChatRoom_V0(json.getInteger("roomId"), json.getString("roomName"));
        // 필요하다면 chatItems, usersSockets 등의 정보도 로드
        return room;
    }

    public ChatRoom_V0(int roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public Set<ChatItem> getUsers() {
        return chatItems;
    }

    public void addUser(ChatItem chatItem) {
        chatItems.add(chatItem);
    }

    public void removeUser(ChatItem chatItem) {
        chatItems.remove(chatItem);
    }

    public void addUserSocket(ServerWebSocket socket) {
        usersSockets.add(socket);
    }

    public void removeUserSocket(ServerWebSocket socket) {
        usersSockets.remove(socket);
    }

    public Set<ServerWebSocket> getUsersSockets() {
        return usersSockets;
    }
}
