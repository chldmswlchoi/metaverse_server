// TODO : 230821 1520 HTTP로 이미지 업로드/다운로드 코드 가져오기 완료
package com.shinthunder.vertx.practice00_eunji_t1.server;

import com.hazelcast.config.Config;
import com.shinthunder.vertx.practice00_eunji_t1.object.ChatItem;
import com.shinthunder.vertx.practice00_eunji_t1.object.ChatRoom;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatServer_test extends AbstractVerticle {
    // -------------------------- CONSTANTS --------------------------
    private static final Logger logger = LoggerFactory.getLogger(ChatServer_test.class);
    private static final int WEBSOCKET_PORT = 8080;
    private static final int NUM_OF_INSTANCES = 2;
    private static final String BROADCAST_MESSAGE_ADDRESS = "broadcast.message.address";

    // -------------------------- MEMBER VARIABLES --------------------------
    private final Set<String> processedMessageIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<ServerWebSocket> clients = new HashSet<>();
    private AsyncMap<String, String> userToSocketMap;  // <userName, socketAddress>
    private AsyncMap<Integer, ChatRoom> rooms;
    private AsyncMap<String, Integer> roomNameToIdMap;
    private AtomicInteger roomCounter = new AtomicInteger(0);

    // -------------------------- WEBSOCKET HANDLER METHODS --------------------------
    private void handleClientAction(ServerWebSocket socket, ChatItem chatItem) {
        switch (chatItem.getAction()) {
            case "create":
                createRoom(chatItem.getRoomName(), socket, ar -> {
                    if (ar.succeeded()) {
                        logger.info("Success to create room");
                    } else {
                        commonErrorHandler("createRoom", ar.cause());
                    }
                });
                break;
            case "connect":
                connect(socket, chatItem, ar -> {
                    if (ar.succeeded()) {
                        userToSocketMap.put(chatItem.getSenderName(), socket.remoteAddress().toString());
                        logger.info("Success to connect user and socket");
                    } else {
                        commonErrorHandler("connect", ar.cause());
                    }
                });

                break;
            case "join":
                joinRoom(chatItem.getRoomName(), chatItem.getSenderName(), ar -> {
                    if (ar.succeeded()) {
//                        userToSocketMap.put(chatItem.getSenderName(), socket.remoteAddress().toString());
                    } else {
                        commonErrorHandler("joinRoom", ar.cause());
                    }
                });
                break;
            case "leave":
                leaveRoom(chatItem.getRoomName(), chatItem.getSenderName(), ar -> {
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
            default:
                logger.warn("Unknown action: {}", chatItem.getAction());
        }
    }

    private void connect(ServerWebSocket socket, ChatItem chatItem, Handler<AsyncResult<Void>> resultHandler) {
        String userNameToConnect = chatItem.getSenderName();

        // Check if the username is already in the map
        userToSocketMap.get(userNameToConnect, res -> {
            if (res.succeeded()) {
                if (res.result() != null) {
                    // The username is already connected
                    logger.warn("User {} is already connected.", userNameToConnect);
                    resultHandler.handle(Future.failedFuture("User already connected"));
                    return;
                }

                // Username not found in the map, add the new user and socket mapping
                userToSocketMap.put(userNameToConnect, socket.remoteAddress().toString(), putResult -> {
                    if (putResult.succeeded()) {
                        logger.info("Success to connect user and socket");
                        resultHandler.handle(Future.succeededFuture());
                    } else {
                        // Error while adding to the map
                        logger.error("Error while connecting user and socket", putResult.cause());
                        resultHandler.handle(Future.failedFuture(putResult.cause()));
                    }
                });
            } else {
                // Error while checking the map
                logger.error("Error while checking if user is already connected", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void createRoom(String roomName, ServerWebSocket socket, Handler<AsyncResult<Void>> resultHandler) {
        roomNameToIdMap.get(roomName)
                .compose(existingRoomId -> {
                    if (existingRoomId == null) {
                        int roomId = roomCounter.incrementAndGet();
                        return CompositeFuture.all(
                                roomNameToIdMap.put(roomName, roomId),
                                rooms.put(roomId, new ChatRoom(roomId, roomName))
                        ).map((Void) null);
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

    private void joinRoom(String roomName, String userName, Handler<AsyncResult<Void>> resultHandler) {
        roomNameToIdMap.get(roomName, res -> {
            if (res.succeeded()) {
                Integer roomId = res.result();
                if (roomId == null) {
                    logger.warn("Room {} does not exist. Can't join.", roomName);
                    return;
                }

                rooms.get(roomId, roomRes -> {
                    if (roomRes.succeeded()) {
                        ChatRoom chatRoom = roomRes.result();
                        chatRoom.addUser(userName);
                        rooms.put(roomId, chatRoom, putRes -> {
                            if (putRes.succeeded()) {
                                logger.info("User {} joined room {}", userName, roomName);
                            } else {
                                logger.error("Error updating room after joining:", putRes.cause());
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
        resultHandler.handle(Future.failedFuture("Failed to join room"));

    }

    private void leaveRoom(String roomName, String userName, Handler<AsyncResult<Void>> resultHandler) {
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
                        chatRoom.removeUser(userName);
                        rooms.put(roomId, chatRoom, putRes -> {
                            if (putRes.succeeded()) {
                                logger.info("User {} left room {}", userName, roomName);
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

    private void broadcastMessageInRoom(ChatItem chatItem, Handler<AsyncResult<Void>> resultHandler) {
        if (processedMessageIds.contains(chatItem.getMessageId())) {
            // 중복 메시지이므로 무시
            logger.warn("Duplicated message received with ID: {}", chatItem.getMessageId());
            return;
        }

        // 메시지 ID를 처리된 ID 목록에 추가
        processedMessageIds.add(chatItem.getMessageId());
        roomNameToIdMap.get(chatItem.getRoomName(), res -> {
            if (res.succeeded()) {
                Integer roomId = res.result();
                if (roomId == null) {
                    logger.warn("Room {} does not exist. Can't broadcast message.", chatItem.getRoomName());
                    return;
                }
                rooms.get(roomId, roomRes -> {
                    if (roomRes.succeeded()) {
                        ChatRoom chatRoom = roomRes.result();
                        Buffer buffer = Json.encodeToBuffer(chatItem);
                        for (String userName : chatRoom.getUsers()) {
                            System.out.println("        >> userName : " + userName);
                            userToSocketMap.get(userName, socketRes -> {
                                if (socketRes.succeeded()) {
                                    String socketAddress = socketRes.result();
                                    ServerWebSocket clientSocket = getClientSocketByAddress(socketAddress);
                                    if (clientSocket != null) {
                                        try {
                                            clientSocket.writeTextMessage(buffer.toString());
                                        } catch (Exception e) {
                                            logger.error("Failed to send message to user {}: {}", userName, e.getMessage());
                                        }
                                    } else {
                                        // 해당 소켓이 현재 Verticle 인스턴스에 없을 경우 이벤트 버스를 통해 메시지 전달
                                        logger.error("해당 소켓이 현재 Verticle 인스턴스에 없을 경우 이벤트 버스를 통해 메시지 전달");
                                        vertx.eventBus().publish(BROADCAST_MESSAGE_ADDRESS, buffer);
                                    }
                                }
                            });
                        }
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
        resultHandler.handle(Future.failedFuture("Failed to broadcast message"));
    }

    private void disconnectClient(ServerWebSocket socket, Handler<AsyncResult<Void>> resultHandler) {
        String userNameToRemove = null;
        for (Map.Entry<String, String> entry : userToSocketMap.entries().result().entrySet()) {
            if (entry.getValue().equals(socket.remoteAddress().host())) {
                userNameToRemove = entry.getKey();
                break;
            }
        }
        if (userNameToRemove != null) {
            userToSocketMap.remove(userNameToRemove);
            for (ChatRoom room : rooms.entries().result().values()) {
                room.removeUser(userNameToRemove);
            }
        }
        clients.remove(socket);
        logger.info("Client disconnected: {}", socket.remoteAddress().host());
        // At the end of successful operations
        resultHandler.handle(Future.succeededFuture());

        // In case of any failure
        resultHandler.handle(Future.failedFuture("Failed to disconnect client"));
    }

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
        vertx.eventBus().consumer(BROADCAST_MESSAGE_ADDRESS, message -> {
            logger.info("Received message via event bus");
            Buffer buffer = (Buffer) message.body();
            ChatItem chatItem = Json.decodeValue(buffer, ChatItem.class);

            // 직접 broadcastMessageInRoom 메서드를 호출
            broadcastMessageInRoom(chatItem, ar -> {
                if (!ar.succeeded()) {
                    commonErrorHandler("broadcastMessageInRoom", ar.cause());
                } else {
                    logger.info("Success to broadcastMessageInRoom");
                }
            });
        });
    }

    public void webSocketHandler(ServerWebSocket socket) {
        logger.info("Client connected: {}", socket.remoteAddress().host());
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

    // -------------------------- Main METHODS --------------------------
    public static void main(String[] args) {
        setupVertxCluster();
    }

    private static void setupVertxCluster() {
        VertxOptions options = configureVertxOptions();
        Vertx.clusteredVertx(options).onComplete(res -> {
            if (res.succeeded()) {
                res.result().deployVerticle(ChatServer_test.class.getName(), new DeploymentOptions().setInstances(NUM_OF_INSTANCES));
            } else {
                logger.error("Cluster up failed: ", res.cause());
            }
        });
    }

    private static VertxOptions configureVertxOptions() {
        Config hazelcastConfig = new Config();
        hazelcastConfig.setClusterName("my-cluster-wow");
//        hazelcastConfig.setCPSubsystemConfig(new CPSubsystemConfig().setCPMemberCount(3)); // 여기 주석을 쳐야
        ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
        return new VertxOptions().setClusterManager(mgr);
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
