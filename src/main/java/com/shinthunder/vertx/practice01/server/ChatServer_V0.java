package com.shinthunder.vertx.practice01.server;

import io.vertx.core.AbstractVerticle;

public class ChatServer_V0 extends AbstractVerticle {
//
//    // -------------------------- CONSTANTS --------------------------
//    private static final Logger logger = LoggerFactory.getLogger(ChatServer_V0.class);
//    private static final int WEBSOCKET_PORT = 8080;
//    private static final int HTTP_PORT = 8081;
//    private static final int numOfInstances = 2;
//    private static final String uploadDirectory = "/Users/hawaii/Desktop/10_path/[CHAT] Codes/chat-v3/";
//    private static final String downloadDirectory = "/Users/hawaii/Desktop/10_path/[CHAT] Codes/chat-v3/";
//
//    // -------------------------- MEMBER VARIABLES --------------------------
//
//    private final Set<ServerWebSocket> clients = new HashSet<>();
//    private final Map<String, ServerWebSocket> userToSocketMap = new ConcurrentHashMap<>();
//    private final Map<Integer, ChatRoom> rooms = new ConcurrentHashMap<>();
//    private final Map<String, Integer> roomNameToIdMap = new ConcurrentHashMap<>();
//    private AtomicInteger roomCounter = new AtomicInteger(0);
//    private boolean awaitingImage = false;
//    private String roomAwaitingImage = null;
//
//    // -------------------------- HTTP HANDLER METHODS --------------------------
//
//    private void httpHandler(HttpServerRequest request) {
//        logger.info("{} '{}' {}", request.method(), request.path(), request.remoteAddress());
//        if (request.path().equals("/upload") && request.method() == HttpMethod.POST) {
//            upload(request);
//        } else if (request.path().startsWith("/download/")) {
//            String sanitizedPath = request.path().substring(10).replaceAll("/", "");
//            download(sanitizedPath, request);
//        } else {
//            request.response().setStatusCode(404).end();
//        }
//    }
//
//    private void upload(HttpServerRequest request) {
//        request.setExpectMultipart(true);
//        request.uploadHandler(upload -> {
//            String fileType = upload.filename().substring(upload.filename().lastIndexOf("."));
//            String filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
//
//            if (fileType.equalsIgnoreCase(".jpg")
//                    || fileType.equalsIgnoreCase(".jpeg")
//                    || fileType.equalsIgnoreCase(".png")
//                    || fileType.equalsIgnoreCase(".gif")
//                    || fileType.equalsIgnoreCase(".txt")
//                    || fileType.equalsIgnoreCase(".aac")
//                    || fileType.equalsIgnoreCase(".mp3")
//                    || fileType.equalsIgnoreCase(".mp4")
//                    || fileType.equalsIgnoreCase(".mov")
//                    || fileType.equalsIgnoreCase(".webm")
//            ) {
//                filename += fileType;
//            } else {
//                request.response().setStatusCode(400).end("Unsupported file type. Supported types: .jpg .jpeg .png .gif .txt .aac .mp3 .mp4 .mov .webm");
//                return;
//            }
//
//            if (upload.size() > 500 * 1024 * 1024) {  // Check if file is larger than 500MB
//                request.response().setStatusCode(413).end("File too large.");
//                return;
//            }
//
//            String absoluteFilepath = uploadDirectory + filename;
////            System.out.println("absoluteFilepath : " + absoluteFilepath);
//            upload.streamToFileSystem(absoluteFilepath);
//            String finalFilename = filename;
//            // 업로드 완료 후 이미지 URL 또는 경로를 모든 클라이언트에게 전송하는 부분 추가
//            String finalFilename1 = filename;
//            upload.endHandler(v -> {
//                request.response().setStatusMessage(finalFilename1).setStatusCode(200).end("File uploaded successfully as " + finalFilename);
//            }).exceptionHandler(    // 업로드 중 오류 발생 시
//                    e -> {
//                        request.response().setStatusCode(500).end("File upload failed.");
//                        System.out.println("error : " + e.getMessage());
//                        this.awaitingImage = false; // 리셋
//                        this.roomAwaitingImage = null; // 리셋
//                    });
//        });
//    }
//
//    private void prepareImageUpload(String roomName, ServerWebSocket socket) {
//        this.awaitingImage = true;
//        this.roomAwaitingImage = roomName;
//        logger.info("Server is now awaiting an image for room: {}", roomName);
//    }
//
//    private void imageUploaded(ChatItem chatItem, ServerWebSocket socket) {
//        String roomName = chatItem.getRoomName();
//        System.out.println("roomName : " + roomName);
//        System.out.println("roomAwaitingImage : " + roomAwaitingImage);
//        if (awaitingImage && roomName.equals(roomAwaitingImage)) {
//            // 클라이언트에게 이미지 업로드가 서버에 의해 성공적으로 수신되었음을 알림
//            logger.info("Server received image upload completion notice for room: {}", roomName);
//            this.awaitingImage = false; // 리셋
//            this.roomAwaitingImage = null; // 리셋
//
//            // 여기서 '이미지 다운로드 시작'이라는 액션을 지닌 메시지를 전송함
//            ChatItem chatImageDownloadStartItem = new ChatItem();
//            chatImageDownloadStartItem.setSenderName(chatItem.getSenderName());
//            chatImageDownloadStartItem.setRoomName(roomName);
//            chatImageDownloadStartItem.setAction("startImageDownload");
//            chatImageDownloadStartItem.setMessage(chatItem.getMessage()); // 서버 상 파일의 이름이 담김
//            broadcastMessageInRoom(chatImageDownloadStartItem);
//
//        } else {
//            logger.warn("Unexpected image upload completion notice for room: {}", roomName);
//        }
//    }
//
//    private void download(String path, HttpServerRequest request) {
//        String filePath = downloadDirectory + path;
//        System.out.println("download filePath : " + filePath);
//        if (!vertx.fileSystem().existsBlocking(filePath)) {
//            request.response().setStatusCode(404).end();
//            System.out.println("return 404");
//            return;
//        }
//        OpenOptions opts = new OpenOptions().setRead(true);
//        vertx.fileSystem().open(filePath, opts, ar -> {
//            if (ar.succeeded()) {
//                downloadFilePipe(ar.result(), request);
//            } else {
//                logger.error("Read failed", ar.cause());
//                request.response().setStatusCode(500).end();
//            }
//        });
//    }
//
//    private void downloadFilePipe(AsyncFile file, HttpServerRequest request) {
//        HttpServerResponse response = request.response();
//        response.setStatusCode(200)
//                .putHeader("Content-Type", getMediaType(request.path()))
//                .setChunked(true);
//        file.pipeTo(response);
//    }
//
//    private static String getMediaType(String filename) {
//        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
//        return switch (extension) {
//            case "jpg", "jpeg" -> "image/jpeg";
//            case "png" -> "image/png";
//            case "gif" -> "image/gif";
//            case "txt" -> "text/plain";
//            case "aac" -> "audio/aac";
//            case "mp3" -> "audio/mpeg";
//            case "mp4" -> "video/mp4";
//            case "mov" -> "video/quicktime";
//            case "webm" -> "video/webm";
//            default -> "application/octet-stream";// Default binary data MIME type
//        };
//    }
//
//    // -------------------------- CLIENT HANDLER METHODS --------------------------
//
//    private void handleClientAction(ServerWebSocket socket, ChatItem chatItem) {
//        switch (chatItem.getAction()) {
//            case "create":
//                createRoom(chatItem.getRoomName(), socket);
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
//            case "prepareImageUpload":
//                prepareImageUpload(chatItem.getRoomName(), socket);
//                break;
//            case "imageUploaded":
//                imageUploaded(chatItem, socket);
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
//                    handleClientAction(socket, chatItem);
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
//            userToSocketMap.values().remove(socket);  // Remove socket from user mapping
//            clients.remove(socket);
//            logger.info("Client disconnected: {}", socket.remoteAddress().host());
//        });
//    }
//
//    private void createRoom(String roomName, ServerWebSocket socket) {
//        if (!roomNameToIdMap.containsKey(roomName)) {
//            int roomId = roomCounter.incrementAndGet();
//            roomNameToIdMap.put(roomName, roomId);
//            rooms.put(roomId, new ChatRoom(roomId, roomName));
//            logger.info("Room {} created", roomName);
//        } else {
//            logger.warn("Room {} already exists", roomName);
//        }
//    }
//
//    private void joinRoom(String roomName, ServerWebSocket socket) {
//        Integer roomId = roomNameToIdMap.get(roomName);
//        if (roomId == null || !rooms.containsKey(roomId)) {
//            logger.warn("Room {} does not exist. Can't join.", roomName);
//            return;
//        }
//        rooms.get(roomId).addUserSocket(socket);
//        logger.info("Client {} joined room {}", socket.remoteAddress().host(), roomName);
//    }
//
//    private void leaveRoom(String roomName, ServerWebSocket socket) {
//        Integer roomId = roomNameToIdMap.get(roomName);
//        if (roomId == null || !rooms.containsKey(roomId)) {
//            logger.warn("Room {} does not exist. Can't leave.", roomName);
//            return;
//        }
//        rooms.get(roomId).removeUserSocket(socket);
//        logger.info("Client {} left room {}", socket.remoteAddress().host(), roomName);
//    }
//
//    private void broadcastMessageInRoom(ChatItem chatItem) {
//        Integer roomId = roomNameToIdMap.get(chatItem.getRoomName());
//        if (roomId == null || !rooms.containsKey(roomId)) {
//            logger.warn("Room {} does not exist. Can't broadcast message.", chatItem.getRoomName());
//            return;
//        }
//        ChatRoom chatRoom = rooms.get(roomId);
//        for (ServerWebSocket client : chatRoom.getUsersSockets()) {
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
//        userToSocketMap.values().remove(socket);
//        for (ChatRoom roomClients : rooms.values()) {
//            roomClients.removeUserSocket(socket);
//        }
//        clients.remove(socket);
//        logger.info("Client disconnected: {}", socket.remoteAddress().host());
//    }
//
//    // -------------------------- START and Main METHODS --------------------------
//
//
//    public static void main(String[] args) {
//        Vertx vertx = Vertx.vertx();
//        vertx.deployVerticle(new ChatServer_V0());
//    }
//
//    @Override
//    public void start() {
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
//
//        vertx.createHttpServer()
//                .requestHandler(this::httpHandler)
//                .listen(HTTP_PORT, res -> {
//                    if (res.succeeded()) {
//                        logger.info("HTTP server running on port {}", WEBSOCKET_PORT);
//                    } else {
//                        logger.error("Failed to start HTTP server", res.cause());
//                    }
//                });
//    }
}