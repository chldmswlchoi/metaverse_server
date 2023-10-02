// TODO : 230818 12:20 대략 이쯤부터 클러스터 변경 작업 시작했음
// TODO : 230820 15:25 클러스터 설정 빼고 이미지 다루는 작업 하는 중
package com.shinthunder.vertx.practice01.server;

import io.vertx.core.AbstractVerticle;

public class ChatServer_V1 extends AbstractVerticle {
//    // -------------------------- SHARED DATA --------------------------
//    private AsyncMap<String, ServerWebSocket> userToSocketMap;
//    private AsyncMap<Integer, String> rooms;
//    private AsyncMap<String, Integer> roomNameToIdMap;
//    private Set<ServerWebSocket> clients = new HashSet<>();
//
//    // -------------------------- CONSTANTS --------------------------
//    private static final Logger logger = LoggerFactory.getLogger(ChatServer_V1.class);
//    private static final int WEBSOCKET_PORT = 8080;
//    private static final int numOfInstances = 2;
//
//    // -------------------------- MEMBER VARIABLES --------------------------
//    private AtomicInteger roomCounter = new AtomicInteger(0);
//
//    // -------------------------- CLIENT HANDLER METHODS --------------------------
//
//    private void handleClientAction(ServerWebSocket socket, ChatItem chatItem, Buffer buffer) {
//        switch (chatItem.getAction()) {
//            case "create":
//                createRoom(chatItem.getRoomName(), socket, ar -> {
//                    if (ar.succeeded()) {
//                        joinRoom(chatItem.getRoomName(), socket);
//                    } else {
//                        logger.error("Failed to create room", ar.cause());
//                    }
//                });
//                break;
//            case "join":
//                joinRoom(chatItem.getRoomName(), socket);
//                break;
//            case "leave":
//                leaveRoom(chatItem.getRoomName(), socket);
//                break;
//            case "message":
//                broadcastMessageInRoom(chatItem);
//                break;
//            case "disconnect":
//                disconnectClient(socket);
//                break;
//            default:
//                logger.warn("Unknown action: {}", chatItem.getAction());
//        }
//    }
//
//    public void webSocketHandler(ServerWebSocket socket) {
//        logger.info("Client connected: {}", socket.remoteAddress().host());
//        clients.add(socket);
//
//        socket.handler(buffer -> {
//            try {
//                logger.info("Received raw message: {}", buffer.toString());
//                ChatItem chatItem = Json.decodeValue(buffer, ChatItem.class);
//
//                if (chatItem.isNameSet()) {
//                    userToSocketMap.put(chatItem.getSenderName(), socket);
//                    logger.info("Name set for {}: {}", socket.remoteAddress().host(), chatItem.getSenderName());
//                } else {
//                    handleClientAction(socket, chatItem, buffer);
//                }
//            } catch (Exception e) {
//                logger.error("Failed to process message from {}: {}", socket.remoteAddress().host(), e.getMessage(), e);
//            }
//        });
//
//        socket.exceptionHandler(e -> {
//            logger.error("Error occurred with client {}: {}", socket.remoteAddress().host(), e.getMessage());
//        });
//        socket.closeHandler(v -> {
//            userToSocketMap.values().result().remove(socket);
//            clients.remove(socket);
//            logger.info("Client disconnected: {}", socket.remoteAddress().host());
//        });
//    }
//
//    private void createRoom(String roomName, ServerWebSocket socket, Handler<AsyncResult<Void>> resultHandler) {
//        roomNameToIdMap.get(roomName)
//                .compose(existingRoomId -> {
//                    if (existingRoomId == null) {
//                        int roomId = roomCounter.incrementAndGet();
//                        return CompositeFuture.all(
//                                roomNameToIdMap.put(roomName, roomId),
//                                rooms.put(roomId, new ChatRoom(roomId, roomName).toJson())
//                        ).map((Void) null);
//                    } else {
//                        return Future.failedFuture("Room already exists");
//                    }
//                })
//                .onSuccess(v -> {
//                    logger.info("Room {} created", roomName);
//                    resultHandler.handle(Future.succeededFuture());
//                })
//                .onFailure(err -> {
//                    commonErrorHandler("createRoom", err);
//                    resultHandler.handle(Future.failedFuture(err));
//                });
//    }
//
//    private void commonErrorHandler(String methodName, Throwable cause) {
//        logger.error("Error in method {}: {}", methodName, cause.getMessage(), cause);
//    }
//
//
//
//    private void joinRoom(String roomName, ServerWebSocket socket) {
//        Integer roomId = roomNameToIdMap.get(roomName).result();
//        if (roomId == null || !rooms.entries().result().containsKey(roomId)) {
//            logger.warn("Room {} does not exist. Can't join.", roomName);
//            return;
//        }
//        ChatRoom.fromJson(rooms.get(roomId).result()).addUserSocket(socket, new ChatItem());
//        logger.info("Client {} joined room {}", socket.remoteAddress().host(), roomName);
//    }
//
//    private void leaveRoom(String roomName, ServerWebSocket socket) {
//        Integer roomId = roomNameToIdMap.entries().result().get(roomName);
//        if (roomId == null || !rooms.entries().result().containsKey(roomId)) {
//            logger.warn("Room {} does not exist. Can't leave.", roomName);
//            return;
//        }
//        ChatRoom.fromJson(rooms.get(roomId).result()).removeUserSocket(socket);
//        logger.info("Client {} left room {}", socket.remoteAddress().host(), roomName);
//    }
//
//    private void broadcastMessageInRoom(ChatItem chatItem) {
//        Integer roomId = roomNameToIdMap.entries().result().get(chatItem.getRoomName());
//        if (roomId == null || !rooms.entries().result().containsKey(roomId)) {
//            logger.warn("Room {} does not exist. Can't broadcast message.", chatItem.getRoomName());
//            return;
//        }
//        ChatRoom chatRoom = ChatRoom.fromJson(rooms.entries().result().get(roomId));
////        for (ServerWebSocket client : chatRoom.getUsersSockets().entries().result().keySet()) {
//        for (ServerWebSocket client : chatRoom.getUsersSockets().keySet()) {
//            try {
//                Buffer buffer = Json.encodeToBuffer(chatItem);
//                client.writeTextMessage(buffer.toString(), ar -> {
//                    if (ar.failed()) {
//                        logger.error("Error sending message to client {}: {}", client.remoteAddress().host(), ar.cause().getMessage());
//                    } else {
//                        logger.info("Message sent to client {}: {}", client.remoteAddress().host(), chatItem.getMessage());
//                    }
//                });
//            } catch (Exception e) {
//                logger.error("Failed to send message to client {}: {}", client.remoteAddress().host(), e.getMessage());
//            }
//        }
//    }
//
//    private void disconnectClient(ServerWebSocket socket) {
//        userToSocketMap.values().result().remove(socket);
//        for (String roomClients : ChatRoom.fromJson(rooms.entries().result()).values()) {
//            roomClients.removeUserSocket(socket);
//        }
//        clients.remove(socket);
//        logger.info("Client disconnected: {}", socket.remoteAddress().host());
//    }
//
//    // -------------------------- START and Main METHODS --------------------------
//
//    private static VertxOptions setVertxConfig() {
//        Config hazelcastConfig = new Config();
//        hazelcastConfig.setClusterName("my-cluster-wow");
//        hazelcastConfig.setCPSubsystemConfig(new CPSubsystemConfig().setCPMemberCount(3));
//        ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
//        return new VertxOptions().setClusterManager(mgr);
//    }
//
//    public static void main(String[] args) {
//        VertxOptions options = setVertxConfig();
//        Vertx.clusteredVertx(options).onComplete(res -> {
//            if (res.succeeded()) {
//                Vertx vertx = res.result();
//                vertx.deployVerticle(ChatServer_V1.class.getName(), new DeploymentOptions().setInstances(numOfInstances));
//            } else {
//                logger.error("Cluster up failed: ", res.cause());
//            }
//        });
//    }
//
//
//    @Override
//    public void start() {
//        vertx.sharedData().<String, ServerWebSocket>getAsyncMap("userToSocketMap", res -> {
//            if (res.succeeded()) {
//                userToSocketMap = res.result();
//            } else {
//                logger.error("Error initializing userToSocketMapAsync:", res.cause());
//            }
//        });
//
//        vertx.sharedData().<Integer, ChatRoom>getAsyncMap("rooms", res -> {
//            if (res.succeeded()) {
//                rooms = res.result();
//            } else {
//                logger.error("Error initializing roomsAsync:", res.cause());
//            }
//        });
//
//        vertx.sharedData().<String, Integer>getAsyncMap("roomNameToIdMap", res -> {
//            if (res.succeeded()) {
//                roomNameToIdMap = res.result();
//            } else {
//                logger.error("Error initializing roomNameToIdMapAsync:", res.cause());
//            }
//        });
//        vertx.createHttpServer()
//                .webSocketHandler(this::webSocketHandler)
//                .exceptionHandler(e -> logger.error("Error occurred with server: {}", e.getMessage()))
//                .listen(WEBSOCKET_PORT, res -> {
//                    if (res.succeeded()) {
//                        logger.info("Server is now listening on port {}", WEBSOCKET_PORT);
//                    } else {
//                        logger.error("Failed to bind on port PORT: {}", res.cause().getMessage());
//                    }
//                });
//    }
}