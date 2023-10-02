package com.shinthunder.vertx.practice05.server;
/**
 * 내일 주의사항
 * 테이블에는 유니크키, 뭐 이런거로 서로 엄청 묶어둠
 * 하지만 ChatServer.java에는 기존에 MessageIds, 방 id 등 아무튼 고유의 방식으로 생성하고 있음
 * <p>
 * 따라서 내일 출근하면
 * 1) 유저가 웹사이트 진입 시 'connect'이벤트를 받고, 그 때부터 로직을 다시 짜야함.
 * 2) 소켓 연결이 되자 마자 select로 방 번호 받아오고, 유저식별을 위한 id도 받아와야함
 * 3) 그 데이터를 가지고 다음 기능들이 유기적으로 연결되어야 함
 * <p>
 * <p>
 * <p>
 * 아래 두 테이블에 room_id, user_id가
 * ChatRoom
 * ChatRoomParticipant
 */

import com.hazelcast.config.Config;
import com.shinthunder.vertx.practice05.object.ChatItem;
import com.shinthunder.vertx.practice05.object.ChatRoom;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ChatServer extends AbstractVerticle {
    // -------------------------- CONSTANTS --------------------------
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);
    private static final int NUM_OF_INSTANCES = 2;
    private static final String BROADCAST_MESSAGE_ADDRESS = "broadcast.message.address";
    //    public static final String HOST_SERVER = "0.0.0.0";
    //    public static final String HOST_SERVER = "localhost";
    public static final String HOST_SERVER = "127.0.0.1";
    public static final String ADDRESS_BROADCAST_MESSAGE = "broadcast.message.address";
    public static final String ADDRESS_IMAGE_ACTION = "image.actions";
    public static final String ADDRESS_DB_ACTION = "db.actions";

    public static final int WEBSOCKET_PORT = 50000;
    public static final int HTTP_PORT = 50001;
    public static final Set<String> SUPPORTED_FILE_TYPES = new HashSet<>(Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".txt", ".aac", ".mp3", ".mp4", ".mov", ".webm"));
    public static final String uploadDirectory = "/home/teamnova0/workspace/testbed/shinji/chat-v3-image-upload-directory/";
//    public static final String downloadDirectory = "/home/teamnova0/workspace/testbed/shinji/chat-v3-image-download-directory/";

    /**
     * ---------------------  JDBC  -----------------------------
     */
    public static final String JDBC_USER = "root";
    //    public static final String JDBC_URL = "jdbc:mysql://localhost:3306/linktown";// hawaii
    public static final String JDBC_URL = "jdbc:mysql://localhost:3306/linktownChat";// hawaii
    public static final String JDBC_PASSWORD = "root";// hawaii
    //    public static final String JDBC_URL = "jdbc:mariadb://localhost:3306/linktown";// teamnova0
//    public static final String JDBC_PASSWORD = "teamnova0";// teamnova0

    // -------------------------- MEMBER VARIABLES --------------------------
    private final Set<String> processedMessageIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<ServerWebSocket> clients = new HashSet<>();
    private AsyncMap<String, String> userToSocketMap;  // <userEmail, socketAddress>
    private AsyncMap<Integer, ChatRoom> rooms;
    private AsyncMap<String, Integer> roomNameToIdMap;


    // -------------------------- WEBSOCKET HANDLER METHODS --------------------------
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

    private void connect(ServerWebSocket socket, ChatItem chatItem, Handler<AsyncResult<Void>> resultHandler) {
        String userEmailToConnect = chatItem.getSenderEmail();
        selectUserByEmail(userEmailToConnect, res -> {
            if (res.succeeded()) {
                JsonObject user = res.result();
                if (user != null) {
                    // The user exists in the database
                    logger.warn("User {} already exists in the database.", userEmailToConnect);
                    resultHandler.handle(Future.failedFuture("User already exists"));
                    return;
                }

                // If the user doesn't exist, register the user
                insertUser(userEmailToConnect, registerRes -> {
                    if (registerRes.succeeded()) {
                        // Get the generated user_id
                        Integer generatedUserId = (Integer) registerRes.result();

                        // Existing code to add user to userToSocketMap...
                        userToSocketMap.get(userEmailToConnect, mapRes -> {
                            if (mapRes.succeeded() && mapRes.result() == null) {
                                // User not connected yet, proceed with connection
                                userToSocketMap.put(userEmailToConnect, socket.remoteAddress().toString(), putResult -> {
                                    if (putResult.succeeded()) {
                                        logger.info("Success to connect user and socket");
                                        System.out.println("==================================================\n        setUserId");

                                        ChatItem response = new ChatItem();
                                        response.setAction("setUserId");
                                        response.setUserId(generatedUserId);
                                        response.setSenderEmail(userEmailToConnect);
                                        socket.writeTextMessage(Json.encodeToBuffer(response).toString()).onComplete(ar -> {
                                            if (ar.succeeded()) {
//                                                logger.info("==================================================\n       Success to send setUserId message");
                                            } else {
//                                                logger.error("==================================================\n      Failed to send setUserId message", ar.cause());
                                            }
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
                        logger.error("Error while registering user", registerRes.cause());
                        resultHandler.handle(Future.failedFuture(registerRes.cause()));
                    }
                });
            } else {
                logger.error("Error while selecting user from the database", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void createRoom(String roomName, ServerWebSocket socket, Integer userId, Handler<AsyncResult<Void>> resultHandler) {
        roomNameToIdMap.get(roomName)
                .compose(existingRoomId -> {
                    if (existingRoomId == null) {
                        // selectChatRoom 을 사용하여 DB에서 해당 방 이름을 찾습니다.
                        Promise<Void> promise = Promise.promise();
                        selectChatRoom(roomName, selectResult -> {
                            if (selectResult.succeeded()) {
                                promise.fail("Room with name " + roomName + " already exists in the database");
                            } else {
                                // 방이 데이터베이스에 없으면 새로운 방을 삽입합니다.
                                AtomicInteger roomId = new AtomicInteger();

                                insertChatRoom(roomName, insertResult -> {
                                    if (insertResult.succeeded()) {
                                        roomId.set(insertResult.result());
//                                        UUID uuid = UUID.randomUUID();
                                        CompositeFuture.all(
                                                roomNameToIdMap.put(roomName, roomId.get()),
                                                rooms.put(roomId.get(), new ChatRoom(roomId.get(), roomName))
                                        ).map((Void) null).onComplete(promise);
                                    } else {
                                        promise.fail(insertResult.cause());
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

    private void joinRoom(String roomName, String userEmail, Integer userId, Handler<AsyncResult<Void>> resultHandler) {
        roomNameToIdMap.get(roomName, res -> {
            if (res.succeeded()) {
                Integer roomId = res.result();
                if (roomId == null) {
                    logger.warn("Room {} does not exist. Can't join.", roomName);
                    return;
                }
                // selectChatRoom 을 사용하여 DB에서 해당 방 이름을 찾습니다.
                Promise<Void> promise = Promise.promise();
                selectChatRoom(roomName, selectResult -> {
                    if (selectResult.succeeded()) {
                        // 방이 데이터베이스에 있으면 insertChatRoomParticipant 를 사용하여 새로운 참가자를 삽입합니다.
                        System.out.println("==================================================\n        insertChatRoomParticipant  : userId :" + userId);
                        insertChatRoomParticipant(String.valueOf(roomId), userId, insertResult -> {
                            if (insertResult.succeeded()) {
                                promise.complete();
                            } else {
                                promise.fail(insertResult.cause());
                            }
                        });
                        rooms.get(roomId, roomRes -> {
                            if (roomRes.succeeded()) {
                                ChatRoom chatRoom = roomRes.result();
                                chatRoom.addUser(userEmail);
                                rooms.put(roomId, chatRoom, putRes -> {
                                    if (putRes.succeeded()) {
                                        logger.info("User {} joined room {}", userEmail, roomName);

//                                         At the end of successful operations
//                                        resultHandler.handle(Future.succeededFuture());
                                    } else {
                                        logger.error("Error updating room after joining:", putRes.cause());

//                                         In case of any failure
//                                        resultHandler.handle(Future.failedFuture("Failed to join room"));
                                    }
                                });
                            } else {
                                logger.error("Error fetching room:", roomRes.cause());
                            }
                        });
                    } else {
                        promise.fail("Room with name " + roomName + " already exists in the database");
                    }
                });
            } else {
                logger.error("Error fetching room id:", res.cause());
            }
        });

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

    private void broadcastMessageInRoom(ChatItem chatItem, Handler<AsyncResult<Void>> resultHandler) {
        if (processedMessageIds.contains(chatItem.getMessageId())) {
            // 중복 메시지이므로 무시
            logger.warn("Duplicated message received with ID: {}", chatItem.getMessageId());
            resultHandler.handle(Future.failedFuture("Duplicated message received"));
            return;
        }
        roomNameToIdMap.get(chatItem.getRoomName(), res -> {
            if (res.succeeded()) {
                Integer roomId = res.result();
            } else {
            }
        });

        roomNameToIdMap.get(chatItem.getRoomName(), roomId -> {
            // Save the message to the database
            JsonObject messageObject = new JsonObject()
                    .put("room_id", roomId.result())
                    .put("sender_id", chatItem.getUserId())
                    .put("message", chatItem.getMessage());
            System.out.println("");
            System.out.println("");
            System.out.println("messageObject. : " + messageObject);
            System.out.println("");
            System.out.println("");

//            insertChatItem(messageObject, insertRes -> {
//                if (insertRes.failed()) {
//                    logger.error("Failed to insert chat item: {}", insertRes.cause().getMessage());
//                    resultHandler.handle(Future.failedFuture(insertRes.cause()));
//                    return;
//                }

            // 메시지 ID를 처리된 ID 목록에 추가
            processedMessageIds.add(chatItem.getMessageId());
            roomNameToIdMap.get(chatItem.getRoomName(), res -> {
                if (res.succeeded()) {
                    System.out.println("");
                    System.out.println("");
                    System.out.println("roomId : " + roomId);
                    System.out.println("roomNameToIdMap.size() : " + roomNameToIdMap.size());
                    System.out.println("");
                    System.out.println("");

                    if (roomId == null) {
                        logger.warn("Room {} does not exist. Can't broadcast message.", chatItem.getRoomName());
                        resultHandler.handle(Future.failedFuture("Room does not exist"));
                        return;
                    }
                    rooms.get(roomId.result(), roomRes -> {
                        if (roomRes.succeeded()) {
                            ChatRoom chatRoom = roomRes.result();
                            Buffer buffer = Json.encodeToBuffer(chatItem);
                            for (String userEmail : chatRoom.getUsers()) {
                                System.out.println("        >> userEmail : " + userEmail);
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
                                            // 해당 소켓이 현재 Verticle 인스턴스에 없을 경우 이벤트 버스를 통해 메시지 전달
                                            logger.error("해당 소켓이 현재 Verticle 인스턴스에 없을 경우 이벤트 버스를 통해 메시지 전달");
                                            vertx.eventBus().publish(BROADCAST_MESSAGE_ADDRESS, buffer);
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
                } else {
                    logger.error("Error fetching room id:", res.cause());
                    resultHandler.handle(Future.failedFuture(res.cause()));
                }
            });
        });
//        });
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

    // -------------------------- START METHODS --------------------------
    @Override
    public void start() {
        initializeSharedData();
        configureWebSocketServer();
        setupEventBusMessageHandler();
        setupDB();
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

    // -------------------------- Main METHODS --------------------------
    public static void main(String[] args) {
        setupVertxCluster();
    }

    private static void setupVertxCluster() {
        VertxOptions options = configureVertxOptions();
        Vertx.clusteredVertx(options).onComplete(res -> {
            if (res.succeeded()) {
                res.result().deployVerticle(ChatServer.class.getName(), new DeploymentOptions().setInstances(NUM_OF_INSTANCES));
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

    // -------------------------- DB SETTING --------------------------
    private JDBCClient jdbcClient;

    private void setupDB() {
        JsonObject mysqlConfig = new JsonObject()
                .put("driver_class", "com.mysql.cj.jdbc.Driver")//
                .put("max_pool_size", 30)//
                .put("user", JDBC_USER)//
                .put("url", JDBC_URL) //
                .put("password", JDBC_PASSWORD);
        jdbcClient = JDBCClient.createShared(vertx, mysqlConfig);
    }

    private void selectUserByEmail(String userEmail, Handler<AsyncResult<JsonObject>> handler) {
        String query = "SELECT * FROM User WHERE user_email = ?";
        JsonArray params = new JsonArray().add(userEmail);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    handler.handle(Future.succeededFuture(results.get(0)));
                } else {
                    handler.handle(Future.succeededFuture(null));
                }
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void selectChatRoomWhereId(String roomId, Handler<AsyncResult<JsonObject>> handler) {
        String query = "SELECT * FROM ChatRoom WHERE id = ?";
        JsonArray params = new JsonArray().add(roomId);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    handler.handle(Future.succeededFuture(results.get(0)));
                } else {
                    handler.handle(Future.failedFuture("ChatRoom not found"));
                }
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void selectChatRoom(String roomName, Handler<AsyncResult<JsonObject>> handler) {
        String query = "SELECT * FROM ChatRoom WHERE name = ?";
        JsonArray params = new JsonArray().add(roomName);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    handler.handle(Future.succeededFuture(results.get(0)));
                } else {
                    handler.handle(Future.failedFuture("ChatRoom not found"));
                }
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void selectChatRoomParticipant(Integer roomId, Handler<AsyncResult<JsonObject>> handler) {
        String query = "SELECT * FROM ChatRoomParticipant WHERE room_id = ?";
        JsonArray params = new JsonArray().add(roomId);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    handler.handle(Future.succeededFuture(results.get(0)));
                } else {
                    handler.handle(Future.failedFuture("Participant not found"));
                }
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void selectChatItem(Integer itemId, Handler<AsyncResult<JsonObject>> handler) {
        String query = "SELECT * FROM ChatItem WHERE id = ?";
        JsonArray params = new JsonArray().add(itemId);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    handler.handle(Future.succeededFuture(results.get(0)));
                } else {
                    handler.handle(Future.failedFuture("ChatItem not found"));
                }
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void fetchChatRoomList(Integer userId, Handler<AsyncResult<JsonArray>> resultHandler) {
        // Find all the chat rooms the user is a participant of
        String query = "SELECT room_id FROM ChatRoomParticipant WHERE user_id = ?";
        JsonArray params = new JsonArray().add(userId);

        jdbcClient.queryWithParams(query, params, roomIdsResult -> {
            if (roomIdsResult.succeeded()) {
                List<JsonObject> roomIds = roomIdsResult.result().getRows();
                System.out.println("roomIds : " + roomIds);
                if (roomIds.isEmpty()) {
                    resultHandler.handle(Future.succeededFuture(new JsonArray()));
                    return;
                }

                List<ChatRoom> chatRoomList = new ArrayList<>();
                AtomicInteger count = new AtomicInteger(roomIds.size());

                for (JsonObject roomIdObj : roomIds) {
                    Integer roomId = roomIdObj.getInteger("room_id");
                    System.out.println("roomId : " + roomId);

                    selectChatRoomWhereId(roomId.toString(), chatRoomResult -> {
                        if (chatRoomResult.succeeded()) {
                            JsonObject chatRoomJson = chatRoomResult.result();
                            String roomName = chatRoomJson.getString("name");
                            System.out.println("chatRoomJson : " + chatRoomJson);

                            selectChatRoomParticipant(roomId, participantResult -> {
                                if (participantResult.succeeded()) {
                                    List<JsonObject> participants = Collections.singletonList(participantResult.result());
                                    Set<String> participantNames = participants.stream()
                                            .map(json -> json.getString("user_id"))  // Assuming "user_id" is a string. Adjust if needed.
                                            .collect(Collectors.toSet());

                                    ChatRoom room = new ChatRoom(roomId, roomName, new HashSet<>(), participantNames);
                                    chatRoomList.add(room);
                                    System.out.println("chatRoomList : " + chatRoomList);

                                    if (count.decrementAndGet() == 0) {
                                        // Send the chat rooms once we have processed all of them
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
                            System.out.println("FAILED selectChatRoom ");
                            if (count.decrementAndGet() == 0) {
                                resultHandler.handle(Future.succeededFuture(new JsonArray(chatRoomList.stream()
                                        .map(ChatRoom::toJson)
                                        .collect(Collectors.toList()))));
                            }
                        }
                    });
                }
            } else {
                resultHandler.handle(Future.failedFuture(roomIdsResult.cause()));
            }
        });
    }

    private void insertUser(String userEmail, Handler<AsyncResult<Object>> handler) {
        String query = "INSERT INTO User (user_email, user_password) VALUES (?, ?)";
        JsonArray params = new JsonArray().add(userEmail).add("some_default_password");

        jdbcClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                Integer generatedId = res.result().getKeys().getInteger(0);
                handler.handle(Future.succeededFuture(generatedId));
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void insertChatRoom(String roomName, Handler<AsyncResult<Integer>> handler) {
        String query = "INSERT INTO ChatRoom (name) VALUES (?)";
        JsonArray params = new JsonArray().add(roomName);

        jdbcClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                // Get the generated key (room number) after insertion
                Integer generatedId = res.result().getKeys().getInteger(0);
                handler.handle(Future.succeededFuture(generatedId));
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void insertChatRoomParticipant(String roomId, Integer userId, Handler<AsyncResult<Void>> handler) {
        String query = "INSERT INTO ChatRoomParticipant (room_id, user_id) VALUES (?, ?)";
        JsonArray params = new JsonArray()
                .add(roomId)
                .add(userId);

        jdbcClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                handler.handle(Future.succeededFuture());
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    private void insertChatItem(JsonObject chatItem, Handler<AsyncResult<Void>> handler) {
        String query = "INSERT INTO ChatItem (room_id, sender_id, message) VALUES (?, ?, ?)";
        JsonArray params = new JsonArray()
                .add(chatItem.getInteger("room_id"))
                .add(chatItem.getInteger("sender_id"))
                .add(chatItem.getString("message"));

        jdbcClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                handler.handle(Future.succeededFuture());
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    public void checkDatabaseConnection() {
        jdbcClient.query("SELECT 1", res -> {
            if (res.succeeded()) {
                System.out.println("Database connected successfully!");
            } else {
                System.err.println("Failed to connect to database: " + res.cause());
            }
        });
    }
}