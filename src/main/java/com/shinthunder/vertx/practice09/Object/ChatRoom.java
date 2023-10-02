package com.shinthunder.vertx.practice09.Object;

import java.io.Serializable;
import java.util.*;

import static com.shinthunder.vertx.practice09.Server.MainServer.GENERAL;

/**
 * 이 클래스에 작성한 메소드들 절대 아무것도 지우지 말 것.
 * 이유 :
 * <p>
 * 주의: ChatRoom 객체를 JSON 표현으로 변환할 때
 * JSON 직렬화 라이브러리(예: Jackson, Gson, 또는 Vert.x의 JSON 처리 메커니즘)에 의해
 * 이 메소드들이 자동으로 호출됩니다. 이로 인해 이 객체를 사용하는 다른 코드에서 명시적으로 이 메소드들을 호출되지 않아도
 * 내부적으로 여기 서술된 메소드들이 JSON 출력에 포함됩니다.
 */
public class ChatRoom implements Serializable {
    private int roomId;
    private String roomName;


    //    private Set<ChatItem> chatItems = new HashSet<>();
    //    private Set<String> users = new HashSet<>();
    private Set<User> users = new HashSet<>();
    private List<ChatItem> chatItems = new ArrayList<>();
    private String relatedType = GENERAL;
    private int relatedId;
    private int talentId;

    @Override
    public String toString() {
        return "\n    ChatRoom{" + '\n' //
                + "         roomId = " + roomId + '\n'//
                + "         roomName = " + roomName + '\n'//
                + "         relatedType = " + relatedType + '\n'//
                + "         relatedId = " + relatedId + '\n'//
                + "         talentId = " + talentId + '\n'//
                + "         chatItems = " + chatItems + '\n'//
                + "         users = " + users + '\n'//
                + "     }\n";
    }

    public void sortChatItems() {
        Collections.sort(chatItems, new Comparator<ChatItem>() {
            @Override
            public int compare(ChatItem o1, ChatItem o2) {
                // timestamp를 기준으로 정렬. 먼저 온 메시지가 앞에 오도록
                return o1.getTimestamp().compareTo(o2.getTimestamp());
            }
        });
    }

    public int getTalentId() {
        return talentId;
    }

    public void setTalentId(int talentId) {
        this.talentId = talentId;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public void setRelatedType(String relatedType) {
        this.relatedType = relatedType;
    }

    public int getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(int relatedId) {
        this.relatedId = relatedId;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    //    public void setChatItems(Set<ChatItem> chatItems) {
//        this.chatItems = chatItems;
//    }
    public void setChatItems(List<ChatItem> chatItems) {
        this.chatItems = chatItems;
    }

//    public void setUsers(Set<String> users) {
//        this.users = users;
//    }

    public List<ChatItem> getChatItems() {
        /**     이 메소드 지우지 마!
         *  역할 : 이 채팅방과 연관된 ChatItems의 집합을 반환합니다.
         *
         *  주의: 이 getter 메서드는 ChatRoom 객체를 JSON 표현으로 변환할 때
         *  JSON 직렬화 라이브러리(예: Jackson, Gson, 또는 Vert.x의 JSON 처리 메커니즘)에 의해
         *  자동으로 호출됩니다. 이로 인해 'chatItems' 필드가 코드에서 명시적으로 호출되지 않아도
         *  JSON 출력에 포함됩니다.
         */
        return chatItems;
    }

    public ChatRoom() { // 비어 보이더라도 어디선가 사용되고 있으니 삭제하지 말것!
    }

    public ChatRoom(int roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }

    public ChatRoom(int roomId, String roomName, List<ChatItem> chatItems, Set<User> users) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.chatItems = chatItems;
        this.users = users;
    }
//public ChatRoom(int roomId, String roomName, Set<ChatItem> chatItems, Set<User> users) {
//        this.roomId = roomId;
//        this.roomName = roomName;
//        this.chatItems = chatItems;
//        this.users = users;
//    }

    public int getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void addChatItem(ChatItem chatItem) {
        chatItems.add(chatItem);
    }

    public Set<User> getUsers() {
        return users;
    }

    public void addUser(User user) {
        users.add(user);
    }

}


//    public String toJson() {
//        JsonObject json = new JsonObject()
//                .put("roomId", roomId)
//                .put("roomName", roomName);
//        // 필요하다면 chatItems, users 등의 정보도 추가
//        return json.encode();
//    }
//
//    public static ChatRoom fromJson(String jsonString) {
//        JsonObject json = new JsonObject(jsonString);
//        ChatRoom room = new ChatRoom(json.getInteger("roomId"), json.getString("roomName"));
//        // 필요하다면 chatItems, users 등의 정보도 로드
//        return room;
//    }