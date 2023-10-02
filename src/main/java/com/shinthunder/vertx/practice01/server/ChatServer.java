// TODO : 230821 1520 HTTP로 이미지 업로드/다운로드 코드 가져오기 완료
package com.shinthunder.vertx.practice01.server;

import com.hazelcast.config.Config;
import com.shinthunder.vertx.practice01.object.ChatItem;
import com.shinthunder.vertx.practice01.object.ChatRoom;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
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
            case "create":
                createRoom(chatItem.getRoomName(), socket, ar -> {
                    if (ar.succeeded()) {
                        logger.info("Success to create room");
                    } else {
                        commonErrorHandler("createRoom", ar.cause());
                    }
                });
                break;
            case "join":
                joinRoom(chatItem.getRoomName(), chatItem.getSenderName(), ar -> {
                    if (ar.succeeded()) {
                        userToSocketMap.put(chatItem.getSenderName(), socket.remoteAddress().toString());
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
            case "prepareImageUpload":
                prepareImageUpload(chatItem.getRoomName(), socket);
                break;
            case "imageUploaded":
                imageUploaded(chatItem, socket);
                break;
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
        configureHttpServer();
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

    private void configureHttpServer() {
        vertx.createHttpServer()
                .requestHandler(this::httpHandler)
                .listen(HTTP_PORT, res -> {
                    if (res.succeeded()) {
                        logger.info("HTTP server running on port {}", WEBSOCKET_PORT);
                    } else {
                        logger.error("Failed to start HTTP server", res.cause());
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

    // -------------------------- HTTP HANDLER METHODS --------------------------

    private void httpHandler(HttpServerRequest request) {
        logger.info("{} '{}' {}", request.method(), request.path(), request.remoteAddress());
        if (request.path().equals("/upload") && request.method() == HttpMethod.POST) {
            upload(request);
        } else if (request.path().startsWith("/download/")) {
            String sanitizedPath = request.path().substring(10).replaceAll("/", "");
            download(sanitizedPath, request);
        } else {
            request.response().setStatusCode(404).end();
        }
    }

    private void upload(HttpServerRequest request) {
        request.setExpectMultipart(true);
        request.uploadHandler(upload -> {
            String fileType = upload.filename().substring(upload.filename().lastIndexOf("."));
            String filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            if (fileType.equalsIgnoreCase(".jpg")
                    || fileType.equalsIgnoreCase(".jpeg")
                    || fileType.equalsIgnoreCase(".png")
                    || fileType.equalsIgnoreCase(".gif")
                    || fileType.equalsIgnoreCase(".txt")
                    || fileType.equalsIgnoreCase(".aac")
                    || fileType.equalsIgnoreCase(".mp3")
                    || fileType.equalsIgnoreCase(".mp4")
                    || fileType.equalsIgnoreCase(".mov")
                    || fileType.equalsIgnoreCase(".webm")
            ) {
                filename += fileType;
            } else {
                request.response().setStatusCode(400).end("Unsupported file type. Supported types: .jpg .jpeg .png .gif .txt .aac .mp3 .mp4 .mov .webm");
                return;
            }

            if (upload.size() > 500 * 1024 * 1024) {  // Check if file is larger than 500MB
                request.response().setStatusCode(413).end("File too large.");
                return;
            }

            String absoluteFilepath = uploadDirectory + filename;
//            System.out.println("absoluteFilepath : " + absoluteFilepath);
            upload.streamToFileSystem(absoluteFilepath);
            String finalFilename = filename;
            // 업로드 완료 후 이미지 URL 또는 경로를 모든 클라이언트에게 전송하는 부분 추가
            String finalFilename1 = filename;
            upload.endHandler(v -> {
                request.response().setStatusMessage(finalFilename1).setStatusCode(200).end("File uploaded successfully as " + finalFilename);
            }).exceptionHandler(    // 업로드 중 오류 발생 시
                    e -> {
                        request.response().setStatusCode(500).end("File upload failed.");
                        System.out.println("error : " + e.getMessage());
                        this.awaitingImage = false; // 리셋
                        this.roomAwaitingImage = null; // 리셋
                    });
        });
    }

    private void prepareImageUpload(String roomName, ServerWebSocket socket) {
        this.awaitingImage = true;
        this.roomAwaitingImage = roomName;
        logger.info("Server is now awaiting an image for room: {}", roomName);
    }

    private void imageUploaded(ChatItem chatItem, ServerWebSocket socket) {
        String roomName = chatItem.getRoomName();
        System.out.println("roomName : " + roomName);
        System.out.println("roomAwaitingImage : " + roomAwaitingImage);
        if (awaitingImage && roomName.equals(roomAwaitingImage)) {
            // 클라이언트에게 이미지 업로드가 서버에 의해 성공적으로 수신되었음을 알림
            logger.info("Server received image upload completion notice for room: {}", roomName);
            this.awaitingImage = false; // 리셋
            this.roomAwaitingImage = null; // 리셋

            // 여기서 '이미지 다운로드 시작'이라는 액션을 지닌 메시지를 전송함
            ChatItem chatImageDownloadStartItem = new ChatItem();
            chatImageDownloadStartItem.setSenderName(chatItem.getSenderName());
            chatImageDownloadStartItem.setRoomName(roomName);
            chatImageDownloadStartItem.setAction("startImageDownload");
            chatImageDownloadStartItem.setMessage(chatItem.getMessage()); // 서버 상 파일의 이름이 담김
            broadcastMessageInRoom(chatImageDownloadStartItem, ar -> {
                if (ar.succeeded()) {
                    logger.info("Success to broadcastMessageInRoom");
                } else {
                    commonErrorHandler("broadcastMessageInRoom", ar.cause());
                }
            });

        } else {
            logger.warn("Unexpected image upload completion notice for room: {}", roomName);
        }
    }

    private void download(String path, HttpServerRequest request) {
        String filePath = downloadDirectory + path;
        System.out.println("    download filePath : " + filePath);
        if (!vertx.fileSystem().existsBlocking(filePath)) {
            request.response().setStatusCode(404).end();
            System.out.println("return 404");
            return;
        }
        OpenOptions opts = new OpenOptions().setRead(true);
        vertx.fileSystem().open(filePath, opts, ar -> {
            if (ar.succeeded()) {
                downloadFilePipe(ar.result(), request);
            } else {
                logger.error("Read failed", ar.cause());
                request.response().setStatusCode(500).end();
            }
        });
    }

    private void downloadFilePipe(AsyncFile file, HttpServerRequest request) {
        HttpServerResponse response = request.response();
        response.setStatusCode(200)
                .putHeader("Content-Type", getMediaType(request.path()))
                .setChunked(true);
        file.pipeTo(response);
    }

    private static String getMediaType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "txt" -> "text/plain";
            case "aac" -> "audio/aac";
            case "mp3" -> "audio/mpeg";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            default -> "application/octet-stream";// Default binary data MIME type
        };
    }
}
