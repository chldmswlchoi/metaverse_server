package com.shinthunder.vertx.practice09.Handler;

import com.shinthunder.vertx.practice09.Object.ChatItem;
import com.shinthunder.vertx.practice09.Object.ChatRoom;
import com.shinthunder.vertx.practice09.Object.ChatRoomActiveStatus;
import com.shinthunder.vertx.practice09.Object.User;
import com.shinthunder.vertx.practice09.Utility.Util;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.shinthunder.vertx.practice09.Server.MainServer.*;


public class WebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private final Vertx vertx;
    private final Set<ServerWebSocket> clients = new HashSet<>();
    private AsyncMap<String, String> userEmailToSocketMap;  // <userEmail, socketAddress>
    private final String thisVerticleID;
    private final Util util;

    // -------------------------- WEBSOCKET HANDLER METHODS (DBVerticle <-

    /**
     * CREATE_JOIN_WATCH_INVITE_NOTICE 관련 메소드
     */
    public void createRoomThenJoinWatchInviteNotice(ServerWebSocket socket, ChatItem chatItem) {
        String roomName = chatItem.getRoomName();
        String userEmail = chatItem.getSenderEmail();
        Integer inviterId = chatItem.getUserId();
        Integer inviteeId = Integer.parseInt(chatItem.getMessage());
        Integer relatedId = chatItem.getRoomId(); // TODO : 여기 중요함!
        Integer talentId = chatItem.getMessageId(); // TODO : 여기 중요함!
        System.out.println("==================================================");
        System.out.println("    createRoomThenJoinWatchInviteNotice()");
        System.out.println("        roomName : " + roomName);
        System.out.println("        userEmail : " + userEmail);
        System.out.println("        inviterId : " + inviterId);
        System.out.println("        inviteeId : " + inviteeId);
        System.out.println("        relatedId : " + relatedId);
        System.out.println("        talentId : " + talentId);
        System.out.println("==================================================");
        chatItem.setRoomId(-1); // 임시값으로 썼을 뿐이라서, -1로 초기화
        chatItem.setMessageId(-1);// 임시값으로 썼을 뿐이라서, -1로 초기화
        String relatedType; // TODO : 여기 중요함!
        switch (chatItem.getAction()) {
            case CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_TALENT_REQ:
                relatedType = TALENT_REQ;
                break;
            case CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_TALENT_SELL:
                relatedType = TALENT_SELL;
                break;
            case CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_GENERAL:
            default:
                relatedType = GENERAL;
                relatedId = -1;
                break;
        }
        System.out.println("000000000000000000000000000000000");
        _createRoom(roomName, inviterId, relatedType, relatedId, talentId)
                .compose(roomId -> _joinRoom(roomId, roomName, userEmail, inviterId).map(v -> roomId))
                .compose(roomId -> _watchRoom(roomId, roomName, userEmail, inviterId).map(v -> roomId))
                .compose(roomId -> _inviteUserToRoom(roomId, roomName, userEmail, inviteeId).map(v -> roomId))
                .onSuccess(roomId -> _sendCreateRoomThenJoinWatchInviteNoticeResult(socket, inviterId, userEmail, inviteeId, roomName, roomId, CREATE_JOIN_WATCH_INVITE_NOTICE_SUCCESS))
                .onFailure(err -> {
                    logger.error("00 An error occurred: {}", err.getMessage());
                    _sendCreateRoomThenJoinWatchInviteNoticeResult(socket, inviterId, userEmail, inviteeId, roomName, null, CREATE_JOIN_WATCH_INVITE_NOTICE_FAILED);
                });
    }

    private Future<Integer> _createRoom(String roomName, Integer userId, String relatedType, Integer relatedId, Integer talentId) {// 임시값으로 썼을 뿐이라서, -1로 초기화
        Promise<Integer> promise = Promise.promise();
        util.insertChatRoom(relatedType, relatedId, talentId, insertResult -> {
            if (insertResult.succeeded()) promise.complete(insertResult.result());
            else promise.fail(insertResult.cause());
        });
        return promise.future();
    }

    private Future<Object> _joinRoom(Integer roomId, String roomName, String userEmail, Integer userId) {
        System.out.println("    _joinRoom roomId : " + roomId);
        JsonObject payload = new JsonObject()
                .put("action", "selectChatRoomParticipantWhereUserId")
                .put("userId", userId);

        return vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payload).compose(participantReply -> {
            JsonObject dbResponse = (JsonObject) participantReply.body();
            if (SUCCESS.equals(dbResponse.getString(STATUS))) {
                List<JsonObject> rooms = dbResponse.getJsonArray("message").getList();
                boolean isParticipant = rooms.stream().anyMatch(room -> room.getString("room_id").equals(roomId.toString()));
                if (isParticipant) {
                    return Future.failedFuture("User already a participant");
                } else {
                    JsonObject payloadInsertChatRoomParticipant = new JsonObject()
                            .put("action", "insertChatRoomParticipant")
                            .put("roomId", roomId)
                            .put("userId", userId);
                    return vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoomParticipant).mapEmpty();
                }
            } else {
                return Future.failedFuture(dbResponse.getString("message"));
            }
        }).compose(v -> {
            logger.info("User {} joined room {}", userEmail, roomName);
            JsonObject payloadInsertChatRoomActiveStatus = new JsonObject()
                    .put("action", "insertChatRoomActiveStatus")
                    .put("roomId", roomId)
                    .put("userId", userId)
                    .put("isWatching", 0);

            return vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoomActiveStatus).compose(reply -> {
                JsonObject dbResponse = (JsonObject) reply.body();
                if (SUCCESS.equals(dbResponse.getString(STATUS))) {
                    logger.info("User {} active status inserted for room {}", userEmail, roomName);
                    return Future.succeededFuture();
                } else {
                    logger.error("Error inserting active status for user in room: {}", dbResponse.getString("message"));
                    return Future.failedFuture("Error inserting active status for user");
                }
            });
        }).recover(err -> {
            if ("User already a participant".equals(err.getMessage())) {
                logger.warn("User {} is already a participant of room {}", userEmail, roomName);
                return Future.failedFuture(err);
            } else if ("Participant not found".equals(err.getMessage())) {
                JsonObject payloadInsertChatRoomParticipant = new JsonObject()
                        .put("action", "insertChatRoomParticipant")
                        .put("roomId", roomId)
                        .put("userId", userId);

                return vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoomParticipant).compose(participantReply -> {
                    JsonObject dbResponse = (JsonObject) participantReply.body();
                    if (SUCCESS.equals(dbResponse.getString(STATUS))) {
                        logger.info("User {} inserted as participant of room {}", userEmail, roomName);
                        JsonObject payloadInsertChatRoomActiveStatus = new JsonObject()
                                .put("action", "insertChatRoomActiveStatus")
                                .put("roomId", roomId)
                                .put("userId", userId)
                                .put("isWatching", 0);
                        return vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoomActiveStatus).mapEmpty();
                    } else {
                        return Future.failedFuture(dbResponse.getString("message"));
                    }
                });
            } else {
                logger.error("Error joining room: {}", err.getMessage());
                return Future.failedFuture(err);
            }
        });
    }

    private Future<Object> _watchRoom(Integer roomId, String roomName, String userEmail, Integer userId) {
        System.out.println("    _watchRoom roomId : " + roomId);
        Promise<Object> promise = Promise.promise();

        util.updateChatRoomActiveStatus(userId, 1, dbResponse -> {
            if (SUCCESS.equals(dbResponse.result().getString(STATUS))) {
                logger.info("User {} is now watching room {}", userEmail, roomName);
                promise.complete();
            } else {
                String errorMsg = "Error updating active status: " + dbResponse.result().getString("message");
                logger.error("111 An error occurred: {}", errorMsg);
                promise.fail(errorMsg);
            }
        });
        return promise.future();
    }

    private Future<Void> _inviteUserToRoom(Integer roomId, String roomName, String userEmail, Integer inviteeId) {
        System.out.println("    _inviteUserToRoom roomId : " + roomId);
        Promise<Void> promise = Promise.promise();
        util.insertChatRoomParticipant(roomId, inviteeId, insertResult -> {
            if (insertResult.succeeded()) {
                JsonObject dbResponse = insertResult.result();
                if (SUCCESS.equals(dbResponse.getString(STATUS))) {
                    logger.info("User {} successfully invited {} to room {}", userEmail, inviteeId, roomName);
                    util.insertChatRoomActiveStatus(roomId, inviteeId, 0, activeStatusResult -> {
                        if (activeStatusResult.succeeded()) promise.complete();
                        else promise.fail(activeStatusResult.cause());
                    });
                } else promise.fail("Error while inserting participant: " + dbResponse.getString("message"));
            } else promise.fail(insertResult.cause());
        });
        return promise.future();
    }

    private void _sendCreateRoomThenJoinWatchInviteNoticeResult(ServerWebSocket socket, Integer inviterId, String inviterEmail, Integer inviteeId, String roomName, Integer roomId, String action) {
        System.out.println("    _sendCreateRoomThenJoinWatchInviteNoticeResult roomId : " + roomId + " : RESULT : " + action);
        ChatItem notice = new ChatItem();
        notice.setAction(action);
        notice.setUserId(inviterId);
        notice.setSenderEmail(inviterEmail);
        notice.setMessage(String.format("%s successfully invited %s to room %s", inviterEmail, inviteeId, roomName));
        notice.setRoomId(roomId);
        notice.setRoomName(roomName);

        // Use your existing method to broadcast the notice
        System.out.println("    _sendCreateRoomThenJoinWatchInviteNoticeResult notice : " + notice);
//        broadcastMessageInRoom(notice);
        util.insertChatItem(notice, roomId, dbResponse -> {
            if (ERROR.equals((dbResponse.getString(STATUS))))
                logger.error("Failed to insert chat item: error : {}", dbResponse.getString("message"));
            else {
                notice.setMessageId(dbResponse.getInteger("insertedId"));
                notice.setTimestamp(dbResponse.getString("timestamp"));
                _fetchChatRoomInfoAndSendMessageToRoom(notice);
            }
        });
    }

    public void _fetchChatRoomInfoAndSendMessageToRoom(ChatItem chatItem) {
        Integer roomId = chatItem.getRoomId();
        Integer userId = chatItem.getUserId();
        String action = chatItem.getAction();
        _fetchChatRoomInfo(roomId, userId, action, chatRoom -> {
            if (chatRoom != null) {
                if (TALENT_SELL.equals(chatRoom.getRelatedType())) {
                    System.out.println("fetchChatRoomInfoAndSendMessageToRoom : TALENT_SELL으로 타입 변경함 !");
                    chatRoom.setRelatedId(chatRoom.getTalentId());
                }
                System.out.println("fetchChatRoomInfoAndSendMessageToRoom : chatItem.getAction() : " + chatItem.getAction());
                System.out.println("fetchChatRoomInfoAndSendMessageToRoom : chatRoom : " + chatRoom);
                _finalizeChatRoomFetching(chatRoom, chatItem);
            }
        });
    }

    public void _fetchChatRoomInfo(Integer roomId, Integer userId, String action, Handler<ChatRoom> onComplete) {
        _fetchChatRoomById(roomId, chatRoom -> {
            if (chatRoom != null) {
                Promise<Void> promise = Promise.promise(); // 새 Promise 생성
                _fetchChatParticipants(roomId, chatRoom, promise); // Promise를 인자로 전달
                promise.future().onComplete(ar -> {  // Promise가 완료되면 이 핸들러 실행
                    if (ar.succeeded()) {
                        _fetchChatItems(roomId, chatRoom, userId, action, unused -> {
                            onComplete.handle(chatRoom);
                        });
                    } else logger.error("Error fetching chat participants");
                });
            } else {
                logger.error("Error fetching chat room with id: {}", roomId);
                onComplete.handle(null);
            }
        });
    }

    private void _finalizeChatRoomFetching(ChatRoom chatRoom, ChatItem chatItem) {
        util.selectAllEmailsWHERERoomIdWithJoin(chatRoom.getRoomId(), userEmails -> {
            for (String userEmail : userEmails) {
                System.out.println("    send new message to : " + userEmail);
                userEmailToSocketMap.get(userEmail, socketRes -> {
                    if (socketRes.result() != null) {
                        logger.info("user : {} || socketRes.result() : {}", userEmail, socketRes.result());
                        String socketAddress = socketRes.result();
                        ServerWebSocket clientSocket = util.getClientSocketByAddress(socketAddress, clients);
//                        sendMessageToSomeoneWithChatRoom(chatRoom, clientSocket);
                        sendMessage(chatRoom, clientSocket);
                    }
                });
            }
        });
    }

    /**
     * 메시지 송신 관련 메소드
     */
    public void broadcastMessageInRoom(ChatItem chatItem) {
        Integer roomId = chatItem.getRoomId();
        switch (chatItem.getAction()) {
            case WATCH_SUCCESS, WATCH_FAILED, UNWATCH_SUCCESS, UNWATCH_FAILED -> _handleIsWatch(chatItem, roomId);
            case MESSAGE_DUPLICATED -> _handleDuplicatedMessage(chatItem, roomId);
            // case MESSAGE :
            default -> _handleNewMessage(chatItem, roomId);
        }
    }

    private void _handleIsWatch(ChatItem chatItem, Integer roomId) {
        util.selectAllEmailsWHERERoomIdWithJoin(roomId, userEmails -> {
            for (String userEmail : userEmails) {
                System.out.println("    send new message to : " + userEmail);
                _sendToClientSocket(userEmail, chatItem, ADDRESS_BROADCAST_MESSAGE);
            }
        });
    }

    private void _handleDuplicatedMessage(ChatItem chatItem, Integer roomId) {
        util.selectAllEmailsWHERERoomIdWithJoin(roomId, userEmails -> {
            for (String userEmail : userEmails) {
                System.out.println("    send duplicated message to : " + userEmail);
                _sendToClientSocket(userEmail, chatItem, null);
            }
        });
    }

    private void _handleNewMessage(ChatItem chatItem, Integer roomId) {
        util.insertChatItem(chatItem, roomId, dbResponse -> {
            if (ERROR.equals((dbResponse.getString(STATUS)))) {
                logger.error("Failed to insert chat item: error : {}", dbResponse.getString("message"));
                return;
            }
            chatItem.setMessageId(dbResponse.getInteger("insertedId"));
            chatItem.setTimestamp(dbResponse.getString("timestamp"));
            util.selectAllEmailsWHERERoomIdWithJoin(roomId, userEmails -> {
                for (String userEmail : userEmails) {
                    System.out.println("    send new message to : " + userEmail);
                    _sendToClientSocket(userEmail, chatItem, ADDRESS_BROADCAST_MESSAGE);
                }
            });
        });
    }

    private void _sendToClientSocket(String userEmail, ChatItem chatItem, String eventBusAddress) {
        userEmailToSocketMap.get(userEmail, socketRes -> {
            if (socketRes.result() != null) {
                logger.info("user : {} || socketRes.result() : {}", userEmail, socketRes.result());
                String socketAddress = String.valueOf(socketRes.result());
                ServerWebSocket clientSocket = util.getClientSocketByAddress(socketAddress, clients);

                if (clientSocket != null) {
                    util.selectChatRoomActiveStatusByRoomId(chatItem.getRoomId(), activeStatusSet -> {
                        if (activeStatusSet != null && !activeStatusSet.isEmpty()) {
                            // Clear existing statuses
                            chatItem.getChatRoomActiveStatus().clear();
                            chatItem.getChatRoomActiveStatus().addAll(activeStatusSet);
                            for (ChatRoomActiveStatus activeStatus : activeStatusSet) {
                                if (activeStatus.getIsWatching() != 1) {
                                    logger.info("User {} is not actively watching room {}. Message might not be sent.", userEmail, chatItem.getRoomName());
                                    // You might want to rework this logic if there's more than one activeStatus for a room
                                }
                            }
                            try {
                                sendMessage(chatItem, clientSocket);
                                logger.info("Message sent to user via 'socket'. user : {},  ThreadNumber : {}", userEmail, util.extractThreadNumber(Thread.currentThread().getName()));
                            } catch (Exception e) {
                                logger.error("Failed to send message to user {}: {}", userEmail, e.getMessage());
                            }
                        } else logger.error("Failed to fetch active statuses for room {}", chatItem.getRoomId());
                    });
                } else {
                    if (eventBusAddress != null) {
                        JsonObject messageToSend = new JsonObject()
                                .put("origin", thisVerticleID)
                                .put("userEmail", userEmail)
                                .put("chatItem", Json.encodeToBuffer(chatItem));
                        vertx.eventBus().publish(eventBusAddress, messageToSend);
                        logger.info("Message sent to user via 'event bus'. user : {},  ThreadNumber : {}", userEmail, util.extractThreadNumber(Thread.currentThread().getName()));
                    } else if (eventBusAddress == null) {
                        logger.error("No eventBusAddress provided. user : {}", userEmail);
                    }
                }
            } else logger.error("user {}: {} <- Failed to get socket address for ", userEmail, "socket == null");
        });
    }

    public void sendMessage(Object item, ServerWebSocket socket) {
        Buffer buffer = null;
        if (item instanceof ChatItem) {
            buffer = Json.encodeToBuffer(item);
            System.out.println("==================================================");
            System.out.println("==================================================");
            System.out.println("buffer (ChatItem) : \n    " + buffer);
            System.out.println("==================================================");
            System.out.println("==================================================");
        } else if (item instanceof ChatRoom chatRoom) {
            chatRoom.sortChatItems();
            buffer = Json.encodeToBuffer(chatRoom);
            System.out.println("==================================================");
            System.out.println("==================================================");
            System.out.println("buffer (ChatRoom) : \n    " + buffer);
            System.out.println("==================================================");
            System.out.println("==================================================");
        } else if (item instanceof JsonArray) {
            buffer = Json.encodeToBuffer(item);
//            System.out.println("finalizeChatRoomFetching() : buffer : " + buffer);
            System.out.println("==================================================");
            System.out.println("==================================================");
            System.out.println("buffer (JsonArray) : \n    " + buffer);
            System.out.println("==================================================");
            System.out.println("==================================================");
        } else {
            buffer = Json.encodeToBuffer(item);
//            System.out.println("finalizeChatRoomFetching() : buffer : " + buffer);
            System.out.println("==================================================");
            System.out.println("==================================================");
            System.out.println("else");
            System.out.println("buffer (" + item.getClass() + ") : \n    " + buffer);
            System.out.println("==================================================");
            System.out.println("==================================================");

        }
        socket.writeTextMessage(buffer.toString());
    }

    /**
     * SELECT_CHATROOMLIST, CREATE_JOIN_WATCH_INVITE_NOTICE 관련 메소드
     */
    public void fetchChatRoomList(ChatItem chatItem, ServerWebSocket socket) {
        Integer userId = chatItem.getUserId();
        util.selectChatRoomParticipantWhereUserId(userId, roomsResponse -> {
            if (roomsResponse.succeeded()) {
                List<JsonObject> roomIds = roomsResponse.result();
                if (roomIds.isEmpty()) {
                    logger.error("QQQQ User {} is not a participant of any chat room", userId);
                } else _handleChatRoomData(roomIds, chatItem, socket);
            } else logger.error("PPPP Error fetching chat room list: {}", roomsResponse.cause().getMessage());
        });
    }

    private void _handleChatRoomData(List<JsonObject> roomIds, ChatItem chatItem, ServerWebSocket socket) {

        Integer userId = chatItem.getUserId();
        String action = chatItem.getAction();
        List<ChatRoom> chatRoomObjects = new ArrayList<>();

        // 비동기 작업을 추적할 Future 객체를 저장할 리스트
        List<Future> roomFetchFutures = new ArrayList<>();
        for (JsonObject roomIdObj : roomIds) {
            // 각 roomId에 대한 비동기 작업을 완료할 때 결과를 저장할 Promise 객체
            Promise<ChatRoom> roomPromise = Promise.promise();
            roomFetchFutures.add(roomPromise.future());  // Future 리스트에 추가

            Integer roomId = roomIdObj.getInteger("room_id");
            System.out.println("============================== _handleChatRoomData() roomId : " + roomId);
            _fetchChatRoomById(roomId, chatRoom -> {
                if (chatRoom != null) {
                    Promise<Void> promise = Promise.promise(); // 새 Promise 생성
                    _fetchChatParticipants(roomId, chatRoom, promise); // Promise를 인자로 전달
                    promise.future().onComplete(ar -> {  // Promise가 완료되면 이 핸들러 실행
                        if (ar.succeeded()) {
                            _fetchChatItems(roomId, chatRoom, userId, action, unused -> {
//                                System.out.println("chatRoomObjects.size() : " + chatRoomObjects.size());
//                                System.out.println("chatRoomObjects("+atomicInteger.get()+") : " + chatRoomObjects);
                                System.out.println("_7777");
                                chatRoomObjects.add(chatRoom);
                                roomPromise.complete(chatRoom);  // 성공적으로 완료되면 Promise를 완료
                            });
                        } else roomPromise.fail("YYYY Failed to fetch chat participants");  // 실패한 경우에는 실패를 알림
                    });
                } else roomPromise.fail("ZZZZ Failed to fetch chat room");  // 실패한 경우에는 실패를 알림
            });
        }

        // 모든 Future 객체가 완료되면 _finalizeChatRoomFetching 메서드 호출
        CompositeFuture.all(roomFetchFutures).onComplete(ar -> {
            System.out.println("_8888");
            if (ar.succeeded()) _finalizeChatRoomFetching(chatRoomObjects, socket);
            else {
                logger.error("XXX Error fetching chat room data: {}", ar.cause().getMessage());
                chatItem.setMessage("Error fetching chat room data: {" + ar.cause().getMessage() + "} === 신규 유저라 채팅방이 없어요~~");
                sendMessage(chatItem, socket);
            }
        });
    }

    private void _fetchChatRoomById(Integer roomId, Handler<ChatRoom> handler) {
        System.out.println("_2222");
        util.selectChatRoomById(roomId, roomResponse -> {
            if (roomResponse.succeeded()) {
                JsonObject dbResponse = roomResponse.result();
                if (SUCCESS.equals(dbResponse.getString(STATUS))) {
                    JsonObject roomData = dbResponse.getJsonObject("message");
                    ChatRoom chatRoom = new ChatRoom();
                    chatRoom.setRoomId(roomData.getInteger("id"));
                    chatRoom.setRoomName(roomData.getString("name"));
                    chatRoom.setRelatedType(roomData.getString("related_type"));
                    chatRoom.setRelatedId(roomData.getInteger("related_id"));
                    chatRoom.setTalentId(roomData.getInteger("talent_id"));
                    handler.handle(chatRoom);
                } else handler.handle(null);
            } else handler.handle(null);
        });
    }

    private void _fetchChatParticipants(Integer roomId, ChatRoom chatRoom, Promise<Void> promise) {
        System.out.println("_3333");
        util.selectChatRoomParticipantWhereRoomId(roomId, dbResponse -> {
            if (dbResponse.succeeded()) {
                List<JsonObject> participants = dbResponse.result();
                System.out.println("JJJJ participants.size() : " + participants.size());
                for (JsonObject participant : participants) {
                    Integer participantId = participant.getInteger("user_id");
                    User user = new User();
                    user.setUserId(participantId);
                    util.selectUserById(participantId, userResponse -> {
                        if (userResponse.succeeded()) {
                            JsonObject userData = userResponse.result();
                            if (SUCCESS.equals(userData.getString(STATUS))) {
                                JsonObject userDetail = userData.getJsonObject("message");
                                user.setUserEmail(userDetail.getString("user_email"));
                                user.setUserNickname(userDetail.getString("user_nick"));
                                // Now add the user with complete details to the chatRoom
                                chatRoom.addUser(user);
                            } else
                                logger.error("IIII Error fetching user details for userId {}: {}", participantId, userData.getString("message"));
                        }
                    });
                }
                promise.complete();  // 작업이 완료되면 Promise를 완료 상태로 만듦
            } else logger.error("Error fetching participants for room {}: {}", roomId, dbResponse.cause().getMessage());
        });
    }

    private void _fetchChatItems(Integer roomId, ChatRoom chatRoom, Integer userId, String action, Handler<String> onComplete) {
        System.out.println("_4444");
        System.out.println("roomId : " + roomId);
        System.out.println("chatRoom : " + chatRoom);
        System.out.println("userId : " + userId);
        System.out.println("action : " + action);

        /** `List<Future> futures = new ArrayList<>();`
         *      Future 객체를 비동기 연산에 대해 저장할 리스트입니다.
         *      이 리스트는 모든 비동기 작업이 (예: ChatRoomActiveStatus를 가져오고 설정하는 것)
         *      완료되기 전까지 다음 단계로 넘어가지 않게 하기 위해 사용됨 (예: chatRoom 객체를 출력하기 전에).
         *
         *      Future 리스트를 사용하면 모든 비동기 작업이
         *      성공적으로 완료되었는지에 대해 집합적으로 대기할 수 있는 방법을 제공함.
         *
         *      아래 주석에서 1,2,3,4,5가 이 코드가 적용된 부분임
         */
        List<Future> futures = new ArrayList<>();
        util.selectChatItemByRoomId(roomId, chatItemResponse -> {
            if (chatItemResponse.succeeded()) {
                List<JsonObject> chatItems = chatItemResponse.result();
                System.out.println("LLLL chatItems.size() : " + chatItems.size());
//                System.out.println("==================================================");
//                System.out.println("chatItems : " + chatItems);
                int i = 0;
                for (JsonObject m_chatItem : chatItems) {
                    Promise<ChatItem> promise = Promise.promise();  // 2. 각 아이템에 대한 Promise 생성
                    futures.add(promise.future());  // 3. Future 리스트에 추가
                    ChatItem item = new ChatItem();
                    item.setAction(m_chatItem.getString("action"));
                    item.setMessageId(m_chatItem.getInteger("id"));
                    item.setUserId(m_chatItem.getInteger("sender_id"));
                    item.setRoomId(m_chatItem.getInteger("room_id"));
                    item.setMessage(m_chatItem.getString("message"));
                    item.setTimestamp(m_chatItem.getString("timestamp"));

                    switch (action) {
                        case SELECT_CHATROOM:
//                            item.setAction(SELECT_CHATROOM);
                            chatRoom.addChatItem(item);
                            promise.complete(item);  // 4. 비동기 작업 완료 알림
                            break;
                        case CREATE_JOIN_WATCH_INVITE_NOTICE_SUCCESS:
                        case CREATE_JOIN_WATCH_INVITE_NOTICE_FAILED:
                        case SELECT_CHATROOMLIST:
                        default:
                            util.selectChatRoomActiveStatusByRoomId(roomId, activeStatusSet -> {
                                if (activeStatusSet != null) {
                                    item.setChatRoomActiveStatus(activeStatusSet);
//                                    System.out.println("item : " + item);
                                    chatRoom.addChatItem(item);
//                                    System.out.println("1 chatRoom : " + chatRoom);
                                    promise.complete(item);  // 4. 비동기 작업 완료 알림
                                } else promise.fail(ERROR);
                            });
                            break;
                    }
                    //                    System.out.println("chatRoom(" + i + ") : " + chatRoom);
//                    i++;
//                    System.out.println("==================================================");
                }
//                System.out.println("chatRoom : " + chatRoom);
                CompositeFuture.all(futures).onComplete(ar -> {  // 5. 모든 비동기 작업이 완료되면
                    if (ar.succeeded()) {
//                            System.out.println("2 chatRoom : " + chatRoom);
                        onComplete.handle("complete !!!! ");
                    } else {
                        logger.error("ZZZZ Error fetching chat items for room {}: {}", roomId, ar.cause().getMessage());
                        onComplete.handle(null);
                    }

                });
//                onComplete.handle(null);
            } else {
                logger.error("Error fetching chat items for room {}: {}", roomId, chatItemResponse.cause());
                onComplete.handle(null);
            }
        });
    }

    private void _finalizeChatRoomFetching(List<ChatRoom> chatRoomObjects, ServerWebSocket socket) {
        System.out.println("_9999");
        for (ChatRoom chatRoom : chatRoomObjects) {
            chatRoom.sortChatItems();
        }
//            System.out.println("finalizeChatRoomFetching() : buffer : " + buffer);
        sendMessage(new JsonArray(chatRoomObjects), socket);
    }

    /**
     * --------------------------------------------------
     */
    public void handleClientAction(ServerWebSocket socket, ChatItem chatItem) {
//        logger.debug("raw msg : {}",chatItem);
        System.out.println("raw chatItem : " + chatItem);
        switch (chatItem.getAction()) {
            case CONNECT:
                connect(socket, chatItem);
                break;
            case MESSAGE:
                broadcastMessageInRoom(chatItem);
                break;
            case CREATE_JOIN_WATCH_INVITE_NOTICE:
            case CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_TALENT_REQ:
            case CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_TALENT_SELL:
                createRoomThenJoinWatchInviteNotice(socket, chatItem);
                break;
            case WATCH:
                watchRoom(socket, chatItem);
                break;
            case UNWATCH:
                unwatchRoom(socket, chatItem);
                break;
            case LEAVE:
                leaveRoom(socket, chatItem);
                break;
            case SELECT_CHATROOMLIST:
                fetchChatRoomList(chatItem, socket);
                break;
//            case SELECT_CHATROOM:
//                fetchChatRoomInfoOnly(chatItem, socket);
//                break;
            case PREPARE_IMAGE_UPLOAD:
                prepareImageUpload(chatItem, socket);
                break;
            case IMAGE_UPLOADED:
                imageUploaded(chatItem, socket);
                break;
//            case INVITE:
//                invite(socket, chatItem);
//                break;
//            case CREATE:
//                createRoom(socket, chatItem);
//                break;
//            case JOIN:
//                joinRoom(chatItem.getRoomName(), chatItem.getSenderEmail(), chatItem.getUserId());
//                break;
            default:
                logger.warn("Unknown action: {}", chatItem.getAction());
                break;
        }
    }

    public void connect(ServerWebSocket socket, ChatItem chatItem) {
        String userEmailToConnect = chatItem.getSenderEmail();
        util.selectUserByEmailForConnect(userEmailToConnect, userResponse -> {
            if (userResponse.succeeded()) {
                JsonObject user = userResponse.result();
                userEmailToSocketMap.put(chatItem.getSenderEmail(), socket.remoteAddress().toString());
                if (user != null) {
                    logger.warn("User {} already exists in the database.", userEmailToConnect);
                    System.out.println("=================================\n        로그인된 유저 : 이게 정상 케이스!");
                    chatItem.setAction(CONNECT_SUCCESS);
                    chatItem.setUserId(user.getInteger("user_id"));
                    sendMessage(chatItem, socket);
                } else System.out.println("=================================\n        로그인 안된 유저 : 이게 비정상 케이스!");
            } else {
                chatItem.setAction(CONNECT_FAILED);
                sendMessage(chatItem, socket);
                logger.error("Error while fetching user data from the database", userResponse.cause());
            }
        });
    }

    public void watchRoom(ServerWebSocket socket, ChatItem chatItem) {
        String userEmail = chatItem.getSenderEmail();
        Integer userId = chatItem.getUserId();
        Integer roomId = chatItem.getRoomId();

        util.updateChatRoomActiveStatus(roomId, userId, true, watchResponse -> {
            if (watchResponse.succeeded()) {
                logger.info("User {} is now watching room {}", userEmail, roomId);
                chatItem.setAction(WATCH_SUCCESS);
                broadcastMessageInRoom(chatItem);
            } else {
                logger.error("Error while updating watcher status for room: {}", watchResponse.cause().getMessage());
                chatItem.setAction(WATCH_FAILED);
                broadcastMessageInRoom(chatItem);
            }
        });
    }

    public void unwatchRoom(ServerWebSocket socket, ChatItem chatItem) {
        Integer userId = chatItem.getUserId();
        String userEmail = chatItem.getSenderEmail();
        Integer roomId = chatItem.getRoomId();

        util.updateChatRoomActiveStatus(roomId, userId, false, unwatchResponse -> {
            if (unwatchResponse.succeeded()) {
                chatItem.setAction(UNWATCH_SUCCESS);
                broadcastMessageInRoom(chatItem);
                logger.info("User {} is no longer watching room {}", userEmail, roomId);
            } else {
                chatItem.setAction(UNWATCH_FAILED);
                broadcastMessageInRoom(chatItem);
                logger.error("Error while updating watcher status for room: {}", unwatchResponse.cause().getMessage());
            }
        });
    }

    public void leaveRoom(ServerWebSocket socket, ChatItem chatItem) {
        String roomName = chatItem.getRoomName();
        String userEmail = chatItem.getSenderEmail();
        Integer userId = chatItem.getUserId();
        Integer roomId = chatItem.getRoomId();

        util.deleteChatRoomActiveStatus(roomId, userId, activeStatusResponse -> {
            if (activeStatusResponse.succeeded()) {
                util.deleteChatRoomParticipant(roomId, userId, participantResponse -> {
                    if (participantResponse.succeeded()) {
                        chatItem.setAction(LEAVE_SUCCESS);
                        sendMessage(chatItem, socket);
                        logger.info("User {} left room {}", userEmail, roomName);
                    } else {
                        chatItem.setAction(LEAVE_FAILED);
                        sendMessage(chatItem, socket);
                        logger.error("Error removing user from ChatRoomParticipant: {}", participantResponse.cause().getMessage());
                    }
                });
            } else {
                chatItem.setAction(LEAVE_FAILED);
                sendMessage(chatItem, socket);
                logger.error("Error removing user from ChatRoomActiveStatus: {}", activeStatusResponse.cause().getMessage());
            }
        });
    }

    // -------------------------- GETTER, SETTER --------------------------
    public WebSocketHandler(Vertx vertx, String thisVerticleID) {
        this.vertx = vertx;
        this.thisVerticleID = thisVerticleID;
        util = new Util(this.vertx);
    }

    public void setUserEmailToSocketMap(AsyncMap<String, String> userEmailToSocketMap) {
        this.userEmailToSocketMap = userEmailToSocketMap;
    }

    public void addClients(ServerWebSocket client) {
        clients.add(client);
    }

    public Future<Void> disconnectClient(ServerWebSocket socket) {
        Promise<Void> disconnectPromise = Promise.promise();
        userEmailToSocketMap.entries().onSuccess(map -> {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (entry.getValue().equals(socket.remoteAddress().toString())) {
                    String userEmailToRemove = entry.getKey();
                    if (userEmailToRemove != null) {
                        util.selectUserByEmailForConnect(userEmailToRemove, userResponse -> {
                            if (userResponse.succeeded()) {
                                JsonObject user = userResponse.result();
                                if (user != null) {
                                    util.updateChatRoomActiveStatus(user.getInteger("user_id"), 0, updateResponse -> {
                                        if (updateResponse.succeeded()) {
                                            userEmailToSocketMap.remove(userEmailToRemove);
                                            clients.remove(socket);
                                            logger.info("Client disconnected and status updated: {}", userEmailToRemove);
                                            disconnectPromise.complete();
                                        } else {
                                            logger.error("Error while updating watching status for {}: {}", userEmailToRemove, updateResponse.cause().getMessage());
                                            disconnectPromise.fail(updateResponse.cause());
                                        }
                                    });
                                } else disconnectPromise.fail("User not found");
                            } else {
                                logger.error("Error while handling disconnection for {}: {}", userEmailToRemove, userResponse.cause().getMessage());
                                disconnectPromise.fail(userResponse.cause());
                            }
                        });
                        break; // email을 찾은 후 loop 탈출
                    }
                } else {
                    System.out.println("entry.getValue()              : " + entry.getValue());
                    System.out.println("socket.remoteAddress().host() : " + socket.remoteAddress().host());
                    System.out.println("socket.remoteAddress()        : " + socket.remoteAddress());
                    disconnectPromise.fail("User not found");
                }
            }
        }).onFailure(err -> {
            System.err.println("Error retrieving entries from userToSocketMap: " + err.getMessage());
            disconnectPromise.fail(err);
        });
        return disconnectPromise.future();
    }

    // -------------------------- IMAGE METHODS (ChatVerticle <-> HTTPVerticle) --------------------------
    private void prepareImageUpload(ChatItem chatItem, ServerWebSocket socket) {
        Integer roomId = chatItem.getRoomId();
        JsonObject request = new JsonObject()
                .put("action", PREPARE_IMAGE_UPLOAD)
                .put("roomId", roomId);
        vertx.eventBus().request(ADDRESS_IMAGE_ACTION, request, reply -> {
            JsonObject response = (JsonObject) reply.result().body();
            if (SUCCESS.equals(response.getString(STATUS))) {
                System.out.println("===================== response =============================");
                System.out.println("        " + response);
                chatItem.setAction(PREPARE_IMAGE_UPLOAD_SUCCESS);
                sendMessage(chatItem, socket);
            } else {
                logger.error("Failed to prepare image upload", reply.cause());
                chatItem.setAction(PREPARE_IMAGE_UPLOAD_FAILED);
                sendMessage(chatItem, socket);
            }
        });
    }

    private void imageUploaded(ChatItem chatItem, ServerWebSocket socket) {
        String chatItemJson = Json.encode(chatItem);
        JsonObject request = new JsonObject()
                .put("action", IMAGE_UPLOADED)
                .put("chatItem", chatItemJson);
        System.out.println("!!!! ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        vertx.eventBus().request(ADDRESS_IMAGE_ACTION, request, reply -> {
            JsonObject response = (JsonObject) reply.result().body();
            if (response.getString(STATUS).equals(SUCCESS)) {
                ChatItem imageUploaded = Json.decodeValue(response.getString("message"), ChatItem.class);
                System.out.println("!!!!  00000 ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
                System.out.println("             1111 imageUploaded : " + imageUploaded);
                imageUploaded.setAction(FILE);
                System.out.println("             2222 imageUploaded : " + imageUploaded);
                broadcastMessageInRoom(imageUploaded);
            } else logger.error("Failed to prepare image upload", reply.cause());
        });
    }

    // -------------------------- 사용하지 않지만 필요한 메서드 --------------------------
    //    public void fetchChatRoomInfoOnly(ChatItem chatItem, ServerWebSocket socket) {
//        Integer roomId = chatItem.getRoomId();
//        Integer userId = chatItem.getUserId();
//        String action = chatItem.getAction();
//        _fetchChatRoomInfo(roomId, userId, action, chatRoom -> {
//            if (chatRoom != null) {
//                List<ChatRoom> singleChatRoomList = Collections.singletonList(chatRoom);
//                _finalizeChatRoomFetching(singleChatRoomList, socket);
//            }
//        });
//    }//    public Future<Integer> createRoom(ServerWebSocket socket, ChatItem chatItem) {
    //        String roomName = chatItem.getRoomName();
    //        Integer roomId = chatItem.getRoomId();
    //        Integer userId = chatItem.getUserId();
    //        // Insert new room into the database
    //        JsonObject payloadInsertChatRoom = new JsonObject()
    //                .put("action", "insertChatRoom")
    //                .put("roomName", roomName);
    //        return vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoom).compose(insertReply -> {
    //            JsonObject dbResponseInsertChatRoom = (JsonObject) insertReply.body();
    //            if (SUCCESS.equals(dbResponseInsertChatRoom.getString(STATUS))) {
    //                Integer generatedRoomId = dbResponseInsertChatRoom.getInteger("generatedId");
    //                return Future.succeededFuture(generatedRoomId);
    //            } else {
    //                return Future.failedFuture(dbResponseInsertChatRoom.getString("message"));
    //            }
    //        }).onSuccess(generatedRoomId -> {
    //            logger.error("Room creation successed: roomId : {}", generatedRoomId);
    //        }).onFailure(err -> {
    //            logger.error("Room creation failed: error : {}", err.getMessage());
    //        });
    //    }
    //
    //    public void joinRoom(String roomName, String userEmail, Integer userId) {
    //        JsonObject payload = new JsonObject()
    //                .put("action", "selectChatRoomWhereName")
    //                .put("roomName", roomName);
    //
    //        AtomicReference<Integer> roomId = new AtomicReference<>(0);
    //        vertx.eventBus().request(ADDRESS_DB_ACTION, payload).compose(reply -> {
    //            JsonObject dbResponse = (JsonObject) reply.body();
    //            if (SUCCESS.equals(dbResponse.getString(STATUS))) {
    //                roomId.set(dbResponse.getJsonObject("message").getInteger("id"));
    //                logger.info("==================================================\n   userId: {}", userId);
    //                logger.info("==================================================\n   roomId: {}", roomId);
    //                logger.info("==================================================\n   dbResponse.getJsonObject(\"message\"): {}", dbResponse.getJsonObject("message"));
    //
    //                JsonObject payloadSelectChatRoomParticipantWhereUserId = new JsonObject()
    //                        .put("action", "selectChatRoomParticipantWhereUserId")
    //                        .put("userId", userId);
    //                return vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadSelectChatRoomParticipantWhereUserId).compose(participantReply -> {
    //                    JsonObject dbResponseSelectChatRoomParticipantWhereUserId = (JsonObject) participantReply.body();
    //                    if (SUCCESS.equals(dbResponseSelectChatRoomParticipantWhereUserId.getString(STATUS))) {
    //                        logger.info("==================================================\n   dbResponse SelectChatRoomParticipantWhereUserId: {}", dbResponseSelectChatRoomParticipantWhereUserId);
    //                        List<JsonObject> rooms = dbResponseSelectChatRoomParticipantWhereUserId.getJsonArray("message").getList();
    //
    //                        boolean isParticipant = rooms.stream().anyMatch(room -> room.getString("room_id").equals((roomId.get()).toString()));
    //
    //                        if (isParticipant) {
    //                            return Future.failedFuture("User already a participant");
    //                        } else {
    //                            JsonObject payloadInsertChatRoomParticipant = new JsonObject()
    //                                    .put("action", "insertChatRoomParticipant")
    //                                    .put("roomId", roomId.get())
    //                                    .put("userId", userId);
    //                            return vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoomParticipant);
    //                        }
    //                    } else {
    //                        return Future.failedFuture(dbResponseSelectChatRoomParticipantWhereUserId.getString("message")); // 여기가 아래의 .onFailure(err -> { 로 빠짐
    //                    }
    //                });
    //            } else {
    //                return Future.failedFuture("Room does not exist");
    //            }
    //        }).onSuccess(v -> {
    //            logger.info("User {} joined room {}", userEmail, roomName);
    //            JsonObject payloadInsertChatRoomActiveStatus = new JsonObject()
    //                    .put("action", "insertChatRoomActiveStatus")
    //                    .put("roomId", roomId.get())
    //                    .put("userId", userId)
    //                    .put("isWatching", 0);
    //            vertx.eventBus().request(ADDRESS_DB_ACTION, payloadInsertChatRoomActiveStatus, reply -> {
    //                JsonObject response = (JsonObject) reply.result().body();
    //                if (SUCCESS.equals(response.getString(STATUS))) {
    //                    logger.info("User {} active status inserted for room {}", userEmail, roomName);
    //                } else {
    //                    logger.error("Error inserting active status for user in room: {}", response.getString("message"));
    //                }
    //            });
    //        }).onFailure(err -> {
    //            if ("User already a participant".equals(err.getMessage())) {
    //                logger.warn("User {} is already a participant of room {}", userEmail, roomName);
    //            } else if ("Participant not found".equals(err.getMessage())) {
    //                /** `Participant not found`는 ChatRoomParticipant 테이블에 유저가 없다는 말일 뿐.
    //                 *      User테이블에는 무조건 있을 수 밖에 없으니 여기서 insertChatRoomParticipant 처리를 한다!
    //                 *      */
    //                JsonObject payloadInsertChatRoomParticipant = new JsonObject()
    //                        .put("action", "insertChatRoomParticipant")
    //                        .put("roomId", roomId.get())
    //                        .put("userId", userId);
    //                vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoomParticipant).compose(participantReply -> {
    //                    JsonObject dbResponse = (JsonObject) participantReply.body();
    //                    if (SUCCESS.equals(dbResponse.getString(STATUS))) {
    //                        logger.info("User {} inserted as participant of room {}", userEmail, roomName);
    //                        JsonObject payloadInsertChatRoomActiveStatus = new JsonObject()
    //                                .put("action", "insertChatRoomActiveStatus")
    //                                .put("roomId", roomId.get())
    //                                .put("userId", userId)
    //                                .put("isWatching", 0);
    //                        return vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoomActiveStatus);
    //                    } else {
    //                        return Future.failedFuture(dbResponse.getString("message"));
    //                    }
    //                }).onSuccess(v -> {
    //                    logger.info("User {} joined room {}", userEmail, roomName);
    //                }).onFailure(error -> {
    //                    logger.error("Error inserting participant: {}", error.getMessage());
    //                });
    //            } else {
    //                logger.error("Error joining room: {}", err.getMessage());
    //            }
    //        });
    //    }

    //    public void invite(ServerWebSocket socket, ChatItem chatItem) {
    //        String inviterEmail = chatItem.getSenderEmail();
    //        Integer inviterId = chatItem.getUserId();  // Getting inviterId from chatItem
    //        Integer inviteeId = Integer.parseInt(chatItem.getMessage());  // 초대받을 사람의 ID
    //        Integer roomId = chatItem.getRoomId();
    //
    //        // 초대받을 사람을 Room에 추가
    //        JsonObject payloadInsertChatRoomParticipant = new JsonObject()
    //                .put("action", "insertChatRoomParticipant")
    //                .put("roomId", roomId)
    //                .put("userId", inviteeId);
    //        vertx.eventBus().request(ADDRESS_DB_ACTION, payloadInsertChatRoomParticipant, reply -> {
    //            JsonObject dbResponse = (JsonObject) reply.result().body();
    //            if (SUCCESS.equals(dbResponse.getString(STATUS))) {
    //                logger.info("User {} successfully invited {} to room {}", inviterEmail, inviteeId, roomId);
    //                // Send a notice message
    //                sendInvitationNotice(inviterId, inviterEmail, inviteeId, roomId);
    //            } else {
    //                logger.error("Error while inviting user to room: {}", dbResponse.getString(ERROR));
    //            }
    //        });
    //    }
    //
    //    private void sendInvitationNotice(Integer inviterId, String inviterEmail, Integer inviteeId, Integer roomId) {
    //        ChatItem notice = new ChatItem();
    //        notice.setAction(MESSAGE_NOTICE);
    //        notice.setUserId(inviterId);
    //        notice.setSenderEmail(inviterEmail);
    //        notice.setMessage(String.format("%s successfully invited %s to room %s", inviterEmail, inviteeId, roomId));
    //        notice.setRoomId(roomId);
    //
    //        // Use your existing method to broadcast the notice
    //        broadcastMessageInRoom(notice);
    //    }
}