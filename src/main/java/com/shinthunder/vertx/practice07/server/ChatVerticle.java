package com.shinthunder.vertx.practice07.server;

import com.shinthunder.vertx.practice07.object.ChatItem;
import com.shinthunder.vertx.practice07.object.ChatRoom;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.shinthunder.vertx.practice07.server.MainServer.*;

public class ChatVerticle extends AbstractVerticle {
    // -------------------------- CONSTANTS --------------------------
    private static final Logger logger = LoggerFactory.getLogger(ChatVerticle.class);

    // -------------------------- MEMBER VARIABLES --------------------------
    private final Set<String> processedMessageIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<ServerWebSocket> clients = new HashSet<>();
    private AsyncMap<String, String> userToSocketMap;  // <userEmail, socketAddress>
    private AsyncMap<Integer, ChatRoom> rooms;
    private AsyncMap<String, Integer> roomNameToIdMap;
    JsonObject payload = new JsonObject();
    JsonObject dbResponse = new JsonObject();

    // -------------------------- START METHODS --------------------------
    @Override
    public void start() {
        initializeSharedData();
        configureWebSocketServer();
        setupEventBusMessageHandler();
    }

    private void initializeSharedData() {
        vertx.sharedData().<String, String>getAsyncMap("userToSocketMap", res -> {
            if (res.succeeded()) {
                userToSocketMap = res.result();
            } else {
                logger.error("Error initializing userToSocketMapAsync:", res.cause());
            }
        });

        vertx.sharedData().<Integer, ChatRoom>getAsyncMap("rooms", res -> {
            if (res.succeeded()) {
                rooms = res.result();
            } else {
                logger.error("Error initializing roomsAsync:", res.cause());
            }
        });

        vertx.sharedData().<String, Integer>getAsyncMap("roomNameToIdMap", res -> {
            if (res.succeeded()) {
                roomNameToIdMap = res.result();
                System.out.println("roomNameToIdMap : " + roomNameToIdMap);
                System.out.println("roomNameToIdMap.size() : " + roomNameToIdMap.size());
            } else {
                logger.error("Error initializing roomNameToIdMapAsync:", res.cause());
            }
        });
    }

    private void configureWebSocketServer() {
        vertx.createHttpServer()
                .webSocketHandler(this::webSocketHandler)
                .exceptionHandler(e -> logger.error("Error occurred with server: {}", e.getMessage()))
                .listen(WEBSOCKET_PORT, res -> {
                    if (res.succeeded()) {
                        logger.info("Server is now listening on port {}", WEBSOCKET_PORT);
                    } else {
                        logger.error("Failed to bind on port PORT: {}", res.cause().getMessage());
                    }
                });
    }

    private void setupEventBusMessageHandler() {
        // 이벤트 버스 메시지 수신 핸들러 설정
        vertx.eventBus().consumer(ADDRESS_BROADCAST_MESSAGE, message -> {
            logger.info("Received message via event bus");
            Buffer buffer = (Buffer) message.body();
            ChatItem chatItem = Json.decodeValue(buffer, ChatItem.class);

            // 직접 broadcastMessageInRoom 메서드를 호출
            broadcastMessageInRoom(chatItem, ar -> {
                if (!ar.succeeded()) commonErrorHandler("broadcastMessageInRoom", ar.cause());
                else logger.info("Success to broadcastMessageInRoom");
            });
        });
    }

    public void webSocketHandler(ServerWebSocket socket) {
        logger.info("Client connected: {}", socket.remoteAddress().host());
//        insertUser(USER_NAME !!, socket.remoteAddress().toString());
        clients.add(socket);
        socket.handler(buffer -> {
            try {
                logger.info("Received raw message: {}", buffer.toString());
                ChatItem chatItem = Json.decodeValue(buffer, ChatItem.class);
                handleClientAction(socket, chatItem);
            } catch (Exception e) {
                logger.error("Failed to process message from {}: {}", socket.remoteAddress().host(), e.getMessage(), e);
            }
        });

        socket.exceptionHandler(e -> {
            logger.error("Error occurred with client {}: {}", socket.remoteAddress().host(), e.getMessage());
        });
        socket.closeHandler(v -> {
            userToSocketMap.values().result().remove(socket);  // Remove socket from user mapping
            clients.remove(socket);
            logger.info("Client disconnected: {}", socket.remoteAddress().host());
        });
    }

    // -------------------------- WEBSOCKET HANDLER METHODS --------------------------

    private void connect(ServerWebSocket socket, ChatItem chatItem, Handler<AsyncResult<Void>> resultHandler) {
        String userEmailToConnect = chatItem.getSenderEmail();

        // Create the payload to send to DBVerticle
        payload.clear()
                .put("action", "selectUserByEmail")
                .put("userEmail", userEmailToConnect);

        // Send the message to DBVerticle to check if the user exists
        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, reply -> {
            dbResponse = (JsonObject) reply.result().body();
            if ("success".equals(dbResponse.getString("status"))) {
                JsonObject user = dbResponse.getJsonObject("data");
                if (user != null) {
                    logger.warn("User {} already exists in the database.", userEmailToConnect);
                    System.out.println("==================================================\n        로그인된 유저 : 이게 정상 케이스!");
                    resultHandler.handle(Future.failedFuture("User already exists"));
                    return;
                }

                System.out.println("==================================================\n        로그인 안된 유저 : 이게 비정상 케이스!");
                // If user doesn't exist, then insert the user into the DB
                payload.clear()
                        .put("action", "insertUser")
                        .put("userEmail", userEmailToConnect);

                vertx.eventBus().request(ADDRESS_DB_ACTION, payload, insertReply -> {
                    dbResponse = (JsonObject) reply.result().body();
                    if ("success".equals(dbResponse.getString("status"))) {
                        Integer generatedUserId = dbResponse.getInteger("generatedId");

                        // Existing code to add user to userToSocketMap...
                        userToSocketMap.get(userEmailToConnect, mapRes -> {
                            if (mapRes.succeeded() && mapRes.result() == null) {
                                // User not connected yet, proceed with connection
                                userToSocketMap.put(userEmailToConnect, socket.remoteAddress().toString(), putResult -> {
                                    if (putResult.succeeded()) {
                                        logger.info("Success to connect user and socket");

                                        ChatItem response = new ChatItem();
                                        response.setAction("setUserId");
                                        response.setUserId(generatedUserId);
                                        response.setSenderEmail(userEmailToConnect);
                                        socket.writeTextMessage(Json.encodeToBuffer(response).toString()).onComplete(ar -> {
                                            if (ar.succeeded()) logger.info("Success to send setUserId message");
                                            else logger.error("Failed to send setUserId message", ar.cause());
                                        });
                                        resultHandler.handle(Future.succeededFuture());
                                    } else {
                                        logger.error("Error while connecting user and socket", putResult.cause());
                                        resultHandler.handle(Future.failedFuture(putResult.cause()));
                                    }
                                });
                            } else {
                                logger.warn("User {} is already connected.", userEmailToConnect);
                                resultHandler.handle(Future.failedFuture("User already connected"));
                            }
                        });
                    } else {
                        logger.error("Error while inserting user to the database", insertReply.cause());
                        resultHandler.handle(Future.failedFuture(insertReply.cause()));
                    }
                });
            } else {
                logger.error("Error while fetching user data from the database", reply.cause());
                resultHandler.handle(Future.failedFuture(reply.cause()));
            }
        });
    }

    private void createRoom(String roomName, ServerWebSocket socket, Integer userId, Handler<AsyncResult<Void>> resultHandler) {
        roomNameToIdMap.get(roomName)
                .compose(existingRoomId -> {
                    if (existingRoomId == null) {
                        // Use selectChatRoom by sending a message to DBVerticle to find the room in the database
                        Promise<Void> promise = Promise.promise();
                        payload.clear()
                                .put("action", "selectChatRoom")
                                .put("roomName", roomName);

                        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, reply -> {
                            dbResponse = (JsonObject) reply.result().body();
                            if ("success".equals(dbResponse.getString("status"))) {
                                promise.fail("Room with name " + roomName + " already exists in the database");
                            } else {
                                // If room doesn't exist in the database, insert a new one
                                payload
                                        .put("action", "insertChatRoom")
                                        .put("roomName", roomName);

                                vertx.eventBus().request(ADDRESS_DB_ACTION, payload, insertReply -> {
                                    dbResponse = (JsonObject) insertReply.result().body();
                                    if ("success".equals(dbResponse.getString("status"))) {
                                        Integer generatedRoomId = dbResponse.getInteger("generatedId");
                                        CompositeFuture.all(
                                                roomNameToIdMap.put(roomName, generatedRoomId),
                                                rooms.put(generatedRoomId, new ChatRoom(generatedRoomId, roomName))
                                        ).map((Void) null).onComplete(promise);
                                    } else {
                                        promise.fail(ChatVerticle.this.dbResponse.getString("message"));
                                    }

                                });
                            }
                        });
                        return promise.future();
                    } else {
                        return Future.failedFuture("Room already exists");
                    }
                })
                .onSuccess(v -> {
                    rooms.size().onSuccess(roomSize -> {
                        roomNameToIdMap.size().onSuccess(mapSize -> {
                            logger.info("Room {} created", roomName);
                            System.out.println("==================================================");
                            System.out.println("    Room name : " + roomName);
                            System.out.println("    rooms.size() : " + roomSize);
                            System.out.println("    roomNameToIdMap.size() : " + mapSize);
                            System.out.println("==================================================");
                            resultHandler.handle(Future.succeededFuture());
                        });
                    });
                })
                .onFailure(err -> {
                    commonErrorHandler("createRoom", err);
                    resultHandler.handle(Future.failedFuture(err));
                });
    }

    private void handleClientAction(ServerWebSocket socket, ChatItem chatItem) {
        switch (chatItem.getAction()) {
            case "connect":
                connect(socket, chatItem, ar -> {
                    if (ar.succeeded()) {
//                        logger.info("Success to connect user and socket");
                    } else {
                        commonErrorHandler("connect", ar.cause());
                    }
                });
                break;
            case "create":
                createRoom(chatItem.getRoomName(), socket, chatItem.getUserId(), ar -> {
                    if (ar.succeeded()) {
                        logger.info("Success to create room");
                    } else {
                        commonErrorHandler("createRoom", ar.cause());
                    }
                });
                break;
            case "join":
                joinRoom(chatItem.getRoomName(), chatItem.getSenderEmail(), chatItem.getUserId(), ar -> {
                    if (ar.succeeded()) {
//                        userToSocketMap.put(chatItem.getSenderName(), socket.remoteAddress().toString());
                    } else {
//                        commonErrorHandler("joinRoom", ar.cause());
                    }
                });
                break;
            case "leave":
                leaveRoom(chatItem.getRoomName(), chatItem.getSenderEmail(), chatItem.getUserId(), ar -> {
                    if (!ar.succeeded()) {
                        commonErrorHandler("leaveRoom", ar.cause());
                    }
                });
                break;
            case "message":
                broadcastMessageInRoom(chatItem, ar -> {
                    if (!ar.succeeded()) {
                        commonErrorHandler("broadcastMessageInRoom", ar.cause());
                    }
                });
                break;
            case "disconnect":
                disconnectClient(socket, ar -> {
                    if (!ar.succeeded()) {
                        commonErrorHandler("disconnectClient", ar.cause());
                    }
                });
                break;
            case "selectChatRoomList":
                fetchChatRoomList(chatItem.getUserId(), ar -> {
                    if (ar.succeeded()) {
                        Buffer buffer = Json.encodeToBuffer(ar.result());
                        socket.writeTextMessage(buffer.toString());
                    } else {
                        commonErrorHandler("selectChatRoomList", ar.cause());
                    }
                });
                break;
            case "selectChatRoom":
//                fetchChatRoomInfo(chatItem.getRoomName(), ar -> {
//                    if (ar.succeeded()) {
//                        socket.writeTextMessage(Json.encodeToBuffer(ar.result()).toString());
//                    } else {
//                        commonErrorHandler("selectChatRoom", ar.cause());
//                    }
//                });
                break;
            default:
                logger.warn("Unknown action: {}", chatItem.getAction());
        }
    }

    private void broadcastMessageInRoom(ChatItem chatItem, Handler<AsyncResult<Void>> resultHandler) {
        if (processedMessageIds.contains(chatItem.getMessageId())) {
            logger.warn("Duplicated message received with ID: {}", chatItem.getMessageId());
            resultHandler.handle(Future.failedFuture("Duplicated message received"));
            return;
        }

        roomNameToIdMap.get(chatItem.getRoomName(), roomIdRes -> {
            if (roomIdRes.failed()) {
                logger.error("Error fetching room id:", roomIdRes.cause());
                resultHandler.handle(Future.failedFuture(roomIdRes.cause()));
                return;
            }

            Integer roomId = roomIdRes.result();
            if (roomId == null) {
                logger.warn("Room {} does not exist. Can't broadcast message.", chatItem.getRoomName());
                resultHandler.handle(Future.failedFuture("Room does not exist"));
                return;
            }

            JsonObject messageObject = new JsonObject()
                    .put("room_id", roomId)
                    .put("sender_id", chatItem.getUserId())
                    .put("message", chatItem.getMessage());

            // Call the DBVerticle to insert the chat item
            payload.clear()
                    .put("action", "insertChatItem")
                    .put("chatItem", messageObject);

            vertx.eventBus().request(ADDRESS_DB_ACTION, payload, insertRes -> {
                dbResponse = (JsonObject) insertRes.result().body();
                if ("error".equals((dbResponse.getString("status")))) {
                    logger.error("Failed to insert chat item:", insertRes.cause().getMessage());
                    resultHandler.handle(Future.failedFuture(insertRes.cause()));
                    return;
                }

                processedMessageIds.add(chatItem.getMessageId());
                rooms.get(roomId, roomRes -> {
                    if (roomRes.succeeded()) {
                        ChatRoom chatRoom = roomRes.result();
                        Buffer buffer = Json.encodeToBuffer(chatItem);
                        for (String userEmail : chatRoom.getUsers()) {
                            userToSocketMap.get(userEmail, socketRes -> {
                                if (socketRes.succeeded()) {
                                    String socketAddress = socketRes.result();
                                    ServerWebSocket clientSocket = getClientSocketByAddress(socketAddress);
                                    if (clientSocket != null) {
                                        try {
                                            clientSocket.writeTextMessage(buffer.toString());
                                        } catch (Exception e) {
                                            logger.error("Failed to send message to user {}: {}", userEmail, e.getMessage());
                                        }
                                    } else {
                                        logger.error("해당 소켓이 현재 Verticle 인스턴스에 없을 경우 이벤트 버스를 통해 메시지 전달");
                                        vertx.eventBus().publish(ADDRESS_BROADCAST_MESSAGE, buffer);
                                    }
                                }
                            });
                        }
                        resultHandler.handle(Future.succeededFuture());
                    } else {
                        logger.error("Error fetching room:", roomRes.cause());
                        resultHandler.handle(Future.failedFuture(roomRes.cause()));
                    }
                });
            });
        });
    }

    private void joinRoom(String roomName, String userEmail, Integer userId, Handler<AsyncResult<Void>> resultHandler) {
        roomNameToIdMap.get(roomName, res -> {
            if (res.succeeded()) {
                Integer roomId = res.result();
                if (roomId == null) {
                    logger.warn("Room {} does not exist. Can't join.", roomName);
                    resultHandler.handle(Future.failedFuture("Room does not exist"));
                    return;
                }

                // Use selectChatRoom by sending a message to DBVerticle to find the room in the database
                payload.clear()
                        .put("action", "selectChatRoom")
                        .put("roomName", roomName);
                vertx.eventBus().request(ADDRESS_DB_ACTION, payload, reply -> {
                    dbResponse = (JsonObject) reply.result().body();
                    if ("success".equals(dbResponse.getString("status"))) {
                        // If the room exists in the database, then check if the user is a participant
                        payload.clear()
                                .put("action", "selectChatRoomParticipant")
                                .put("roomId", roomId);
                        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, selectChatRoomParticipantReply -> {
                            dbResponse = (JsonObject) selectChatRoomParticipantReply.result().body();
                            if ("success".equals(dbResponse.getString("status"))) {
                                // User is already a participant
                                resultHandler.handle(Future.failedFuture("User is already a participant"));
                            } else {
                                // Insert a new participant
                                System.out.println("==================================================\n        insertChatRoomParticipant  : userId :" + userId);
                                payload.clear()
                                        .put("action", "insertChatRoomParticipant")
                                        .put("roomId", roomId)
                                        .put("userId", userId);
                                vertx.eventBus().request(ADDRESS_DB_ACTION, payload, insertChatRoomParticipantReply -> {
                                    dbResponse = (JsonObject) selectChatRoomParticipantReply.result().body();
                                    if ("success".equals(dbResponse.getString("status"))) {
                                        // Assuming a successful insert:
                                        rooms.get(roomId, roomRes -> {
                                            if (roomRes.succeeded()) {
                                                ChatRoom chatRoom = roomRes.result();
                                                chatRoom.addUser(userEmail);
                                                rooms.put(roomId, chatRoom, putRes -> {
                                                    if (putRes.succeeded()) {
                                                        logger.info("User {} joined room {}", userEmail, roomName);
                                                        resultHandler.handle(Future.succeededFuture());
                                                    } else {
                                                        logger.error("Error updating room after joining:", putRes.cause());
                                                        resultHandler.handle(Future.failedFuture("Failed to join room"));
                                                    }
                                                });
                                            } else {
                                                logger.error("Error fetching room:", roomRes.cause());
                                                resultHandler.handle(Future.failedFuture(roomRes.cause().getMessage()));
                                            }
                                        });
                                    } else {
                                        System.out.println("error : " + dbResponse.getString("message"));
                                        resultHandler.handle(Future.failedFuture(insertChatRoomParticipantReply.cause()));
                                    }
                                });
                            }
                        });
                    } else {
                        resultHandler.handle(Future.failedFuture(dbResponse.getString("message")));
                    }
                });
            } else {
                logger.error("Error fetching room id:", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause().getMessage()));
            }
        });
    }

    private void fetchChatRoomList(Integer userId, Handler<AsyncResult<JsonArray>> resultHandler) {
        payload.clear()
                .put("action", "selectChatRoomParticipant")
                .put("roomId", userId);
        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, selectChatRoomParticipantReply -> {
            dbResponse = (JsonObject) selectChatRoomParticipantReply.result().body();
            List<JsonObject> roomIds = (dbResponse.getJsonArray("data")).getList();
            if (roomIds.isEmpty()) {
                resultHandler.handle(Future.succeededFuture(new JsonArray()));
                return;
            }

            List<ChatRoom> chatRoomList = new ArrayList<>();
            AtomicInteger count = new AtomicInteger(roomIds.size());
            for (JsonObject roomIdObj : roomIds) {
                Integer roomId = roomIdObj.getInteger("room_id");
                payload = new JsonObject()
                        .put("action", "selectChatRoomWhereId")
                        .put("roomId", roomId);
                vertx.eventBus().request(ADDRESS_DB_ACTION, payload, roomResponse -> {
                    dbResponse = (JsonObject) roomResponse.result().body();
                    if ("success".equals(dbResponse.getString("status"))) {
                        JsonObject chatRoomJson = dbResponse.getJsonObject("data");
                        String roomName = chatRoomJson.getString("name");
                        payload = new JsonObject()
                                .put("action", "selectChatRoomParticipant")
                                .put("roomId", roomId);
                        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, participantsResponse -> {
                            dbResponse = (JsonObject) roomResponse.result().body();
                            if ("success".equals(dbResponse.getString("status"))) {
                                List<JsonObject> participants = dbResponse.getJsonArray("data").getList();
                                Set<String> participantNames = participants.stream()
                                        .map(json -> json.getString("user_id"))
                                        .collect(Collectors.toSet());
                                ChatRoom room = new ChatRoom(roomId, roomName, new HashSet<>(), participantNames);
                                chatRoomList.add(room);
                                if (count.decrementAndGet() == 0) {
                                    resultHandler.handle(Future.succeededFuture(new JsonArray(chatRoomList.stream()
                                            .map(ChatRoom::toJson)
                                            .collect(Collectors.toList()))));
                                }
                            } else {
                                if (count.decrementAndGet() == 0) {
                                    resultHandler.handle(Future.succeededFuture(new JsonArray(chatRoomList.stream()
                                            .map(ChatRoom::toJson)
                                            .collect(Collectors.toList()))));
                                }
                            }
                        });
                    } else {
                        if (count.decrementAndGet() == 0) {
                            resultHandler.handle(Future.succeededFuture(new JsonArray(chatRoomList.stream()
                                    .map(ChatRoom::toJson)
                                    .collect(Collectors.toList()))));
                        }
                    }
                });
            }
        });
    }

    private void disconnectClient(ServerWebSocket socket, Handler<AsyncResult<Void>> resultHandler) {
        String userEmailToRemove = null;
        for (Map.Entry<String, String> entry : userToSocketMap.entries().result().entrySet()) {
            if (entry.getValue().equals(socket.remoteAddress().host())) {
                userEmailToRemove = entry.getKey();
                break;
            }
        }
        if (userEmailToRemove != null) {
            userToSocketMap.remove(userEmailToRemove);
            for (ChatRoom room : rooms.entries().result().values()) {
                room.removeUser(userEmailToRemove);
            }
        }
        clients.remove(socket);
        logger.info("Client disconnected: {}", socket.remoteAddress().host());
        // At the end of successful operations
        resultHandler.handle(Future.succeededFuture());

        // In case of any failure
        resultHandler.handle(Future.failedFuture("Failed to disconnect client"));
    }

    private void leaveRoom(String roomName, String userEmail, Integer userId, Handler<AsyncResult<Void>> resultHandler) {
        roomNameToIdMap.get(roomName, res -> {
            if (res.succeeded()) {
                Integer roomId = res.result();
                if (roomId == null) {
                    logger.warn("Room {} does not exist. Can't leave.", roomName);
                    return;
                }
                rooms.get(roomId, roomRes -> {
                    if (roomRes.succeeded()) {
                        ChatRoom chatRoom = roomRes.result();
                        chatRoom.removeUser(userEmail);
                        rooms.put(roomId, chatRoom, putRes -> {
                            if (putRes.succeeded()) {
                                logger.info("User {} left room {}", userEmail, roomName);
                            } else {
                                logger.error("Error updating room after leaving:", putRes.cause());
                            }
                        });
                    } else {
                        logger.error("Error fetching room:", roomRes.cause());
                    }
                });
            } else {
                logger.error("Error fetching room id:", res.cause());
            }
        });
        // At the end of successful operations
        resultHandler.handle(Future.succeededFuture());

        // In case of any failure
        resultHandler.handle(Future.failedFuture("Failed to leave room"));
    }


    // -------------------------- UTILITY METHODS --------------------------
    private ServerWebSocket getClientSocketByAddress(String socketAddress) {
        logger.info("Looking for socket with address: {}", socketAddress);
        for (ServerWebSocket socket : clients) {
            if (socket.remoteAddress().toString().equals(socketAddress)) {
                logger.info("Found a match for socket address: {}", socketAddress);
                return socket;
            }
        }
        logger.warn("No match found for socket address: {}", socketAddress);
        return null;
    }

    private void commonErrorHandler(String methodName, Throwable cause) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (stackTraceElements.length > 2) {
            // getStackTrace()[0]은 getStackTrace() 메서드 자체에 대한 정보이며,
            // getStackTrace()[1]은 commonErrorHandler() 메서드에 대한 정보입니다.
            // 그러므로 commonErrorHandler()를 호출한 메서드의 정보는 getStackTrace()[2]에 있습니다.
            StackTraceElement callerElement = stackTraceElements[2];

            logger.error("Error in method {} at line {}: {}", methodName, callerElement.getLineNumber(), cause.getMessage(), cause);
        } else {
            logger.error("Error in method {}: {}", methodName, cause.getMessage(), cause);
        }
    }


}