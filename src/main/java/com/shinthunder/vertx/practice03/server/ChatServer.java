// TODO : 230821 1520 HTTP로 이미지 업로드/다운로드 코드 가져오기 완료
package com.shinthunder.vertx.practice03.server;

import com.hazelcast.config.Config;
import com.shinthunder.vertx.practice03.object.ChatItem;
import com.shinthunder.vertx.practice03.object.ChatRoom;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatServer extends AbstractVerticle {
    // -------------------------- CONSTANTS --------------------------
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);
    private static final int WEBSOCKET_PORT = 8080;
    private static final int HTTP_PORT = 8081;
    private static final int NUM_OF_INSTANCES = 5;
    private static final String BROADCAST_MESSAGE_ADDRESS = "broadcast.message.address";
    private static final String uploadDirectory = "/Users/hawaii/Desktop/10_path/[CHAT] Codes/chat-v3/";
    private static final String downloadDirectory = "/Users/hawaii/Desktop/10_path/[CHAT] Codes/chat-v3/";

    // -------------------------- MEMBER VARIABLES --------------------------
    private final Set<String> processedMessageIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<ServerWebSocket> clients = new HashSet<>();
    private AsyncMap<String, String> userToSocketMap;  // <userName, socketAddress>
    private AsyncMap<Integer, ChatRoom> rooms;
    private AsyncMap<String, Integer> roomNameToIdMap;
    private AtomicInteger roomCounter = new AtomicInteger(0);
    private AtomicInteger userCounter = new AtomicInteger(0);
    private boolean awaitingImage = false;
    private String roomAwaitingImage = null;

    // -------------------------- WEBSOCKET HANDLER METHODS --------------------------
    private void handleClientAction(ServerWebSocket socket, ChatItem chatItem) {
        switch (chatItem.getAction()) {
            case "MOVE":
//                System.out.println("==================================================");
//                System.out.println("    senderName : " + chatItem.getSenderName());
//                System.out.println("    userCounter : " + userCounter.incrementAndGet());
                logger.info(" name : {}, userCounter : {}",chatItem.getSenderName(), userCounter.incrementAndGet());
//                System.out.println("");
//                System.out.println("");
//                System.out.println("");
//                System.out.println("");
//                System.out.println("");
//                System.out.println("MOVE : "+chatItem);
////                System.out.println("MOVE");
//                System.out.println("");
//                System.out.println("");
//                System.out.println("");
//                System.out.println("");
//                System.out.println("");
                break;
            default:
                logger.warn("Unknown action: {}", chatItem.getAction());
        }
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
//                        for (String userName : chatRoom.getUsers()) {
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
//                logger.info("Received raw message: {}", buffer.toString());
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
}
