package com.shinthunder.vertx.practice09.Client;

import com.shinthunder.vertx.practice09.Object.ChatItem;
import com.shinthunder.vertx.practice09.Object.ChatRoom;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import static com.shinthunder.vertx.practice09.Server.MainServer.*;

public class ChatClientWithRemoteServer extends AbstractVerticle {

    private final Logger logger = LoggerFactory.getLogger(ChatClientWithRemoteServer.class);
    private static final int PORT = 50000;
    private static final int PORT2 = 50001;
    //        private static final String HOST = "localhost";
//    private static final String HOST = "221.148.25.26"; // teamnova2
    private static final String HOST = "221.148.25.247"; // teamnova0
    private static final String downloadDirectory = "/Users/hawaii/Downloads/FileUpDownload-testbed-client-to-download/";
    private AtomicInteger userId = new AtomicInteger();
    List<ChatRoom> chatRooms = new ArrayList<>();

    // /sendImage
    // /Users/hawaii/Downloads/FileUpDownload-testbed-client-to-upload/1.png

    // --- Username related methods ---
    private void promptForUsername() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();
            logger.info("Username entered: {}", username);
            vertx.runOnContext(v -> handleUsername(username));
        }).start();
    }

    private void handleUsername(String username) {
        vertx.createHttpClient().webSocket(PORT, HOST, "/", webSocketAsyncResult -> {
            if (webSocketAsyncResult.succeeded()) {
                WebSocket webSocket = webSocketAsyncResult.result();
                webSocket.textMessageHandler(message -> {
                    if (message.trim().startsWith("[")) {
                        // 메시지가 여러 ChatItem 객체라면 (Message is a JsonArray)
                        JsonArray jsonArray = new JsonArray(message);
                        System.out.println("==================================================\n    Received jsonArray: " + jsonArray);
                        for (int i = 0; i < jsonArray.size(); i++) {
                            JsonObject roomJson = jsonArray.getJsonObject(i);
//                            System.out.println("==================================================\n    roomJson(" + i + ") : " + roomJson);
                            ChatRoom chatRoom = Json.decodeValue(roomJson.encode(), ChatRoom.class);
                            chatRooms.add(chatRoom);
                        }
                    } else {
                        // 그렇지 않다면 단일 ChatItem 객체로 역직렬화 합니다.
                        ChatItem response = Json.decodeValue(message, ChatItem.class);
                        System.out.println("==================================================\n        setUserId");
                        System.out.println("    >> response : " + response);
//                        System.out.println("    >> userId : " + response.getUserId() + ", email : " + response.getSenderEmail() + ", msg : " + response.getMessage());
                        userId.set(response.getUserId());
                    }
                });

                ChatItem chatItem = new ChatItem();
                chatItem.setSenderEmail(username);
                chatItem.setUserId(userId.get());
                chatItem.setAction(CONNECT);
                webSocketAsyncResult.result().writeTextMessage(Json.encode(chatItem)).onComplete(res -> {
                    if (res.succeeded()) {
                        logger.info("Connected to WebSocket as username: {}", username);
                        logger.info("Connected to WebSocket as userId: {}", userId);
                    } else {
                        logger.error("Failed to connect from WebSocket", res.cause());
                    }
                });
//                webSocket.textMessageHandler(message -> {
//                    ChatItem response = Json.decodeValue(message, ChatItem.class);
//                    System.out.println("    >> response : " + response);
//                });
                showMenu(webSocketAsyncResult.result(), username, userId);
            } else {
                logger.error("Failed to connect to WebSocket", webSocketAsyncResult.cause());
                System.exit(1);
            }
        });
    }

    // --- Menu related methods ---
    private void showMenu(WebSocket webSocket, String username, AtomicInteger userId) {
        System.out.println("Choose an option:");
        System.out.println("1. Create room");
        System.out.println("2. Join room");
        System.out.println("3. Leave room");
        System.out.println("4. Disconnect");
        System.out.println("5. queryChatRoomList");
        System.out.println("6. queryChatRoom");
        System.out.println("7. Watch room");
        System.out.println("8. Invite user to room");
        System.out.println("9. create-Join-Watch-Invite-Notice");

        readInputFromUserToExecuteBlocking(option -> {
            try {
                handleMenuOption(option, webSocket, username, userId);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error reading menu option", e);
            }
        });
    }

    private void handleMenuOption(String option, WebSocket webSocket, String username, AtomicInteger userId) {
        switch (option) {
            case "1":
                logger.info("Option 'Create room' selected by user: {}", username);
                handleRoomInput("Enter room id to create:", CREATE, webSocket, username, userId);
                break;
            case "2":
                logger.info("Option 'Join room' selected by user: {}", username);
                handleRoomInput("Enter room id to join:", JOIN, webSocket, username, userId);
                break;
            case "3":
                logger.info("Option 'Leave room' selected by user: {}", username);
                handleRoomInput("Enter room id to leave:", LEAVE, webSocket, username, userId);
                break;
            case "4":
                logger.info("Option 'Disconnect' selected by user: {}", username);
                ChatItem chatItem = new ChatItem();
                chatItem.setAction(DISCONNECT);
                webSocket.writeTextMessage(Json.encode(chatItem));
                webSocket.close();
                System.exit(0);
                break;
            case "5":
                selectChatRoomList(webSocket, username, userId);
                break;
            case "6":
//                selectChatRoom(webSocket, username, userId, null);
                handleRoomInput("Enter room id to SELECT_CHATROOM:", SELECT_CHATROOM, webSocket, username, userId);
                break;
            case "7":
                logger.info("Option 'Watch room' selected by user: {}", username);
                handleRoomInput("Enter room id to watch:", WATCH, webSocket, username, userId);
                break;
            case "8":
                logger.info("Option 'Invite user to room' selected by user: {}", username);
                handleInviteToRoom(webSocket, username, userId); // 이 메서드를 새로 만듭니다.
                break;
            case "9":
                logger.info("Option 'create-Join-Watch-Invite-Notice' selected by user: {}", username);
                handleCreateJoinWatchInviteNotice(webSocket, username, userId);
                break;
            default:
                System.out.println("Invalid option. Try again.");
                showMenu(webSocket, username, userId);
        }
    }

    private void handleCreateJoinWatchInviteNotice(WebSocket webSocket, String username, AtomicInteger userId) {
        System.out.println("Enter the name of the room you want to create: 여기 의미 없음. 뭘 넣든 서버로 가는 roomId는 -1로 넣을 예정");
        readInputFromUserToExecuteBlocking(roomId -> {
            System.out.println("Enter the ID of the user you want to invite(숫자 넣어요 숫자!):");
            readInputFromUserToExecuteBlocking(inviteeId -> {
                try {
                    // 이 부분에서 서버에게 create-Join-Watch-Invite-Notice 메시지를 보낼 수 있습니다.
                    ChatItem chatItem = new ChatItem();
                    chatItem.setUserId(userId.get());
                    chatItem.setSenderEmail(username);
                    chatItem.setAction(CREATE_JOIN_WATCH_INVITE_NOTICE);
                    chatItem.setRoomId(-1);
                    chatItem.setRoomName("");
                    chatItem.setMessage(inviteeId);
                    webSocket.writeTextMessage(Json.encode(chatItem));

                    // 이 부분에서 서버로부터 응답을 기다리고 처리하는 코드를 넣을 수 있습니다.
                    // 예를 들어, webSocket.textMessageHandler 를 여기에 설정할 수 있습니다.
                    webSocket.textMessageHandler(message -> {
                        System.out.println("    >> response : " + message);
//                        ChatItem response = Json.decodeValue(message, ChatItem.class);
//                        System.out.println("    >> response : " + response);
                    });
                    showMenu(webSocket, username, userId);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("Error in create-Join-Watch-Invite-Notice", e);
                }
            });
        });
    }

    private void handleInviteToRoom(WebSocket webSocket, String username, AtomicInteger userId) {
        System.out.println("Enter the room id where you want to invite a user:");
        readInputFromUserToExecuteBlocking(roomId -> {
            System.out.println("Enter the username of the user you want to invite:");
            readInputFromUserToExecuteBlocking(invitee -> {
                try {
                    // 여기서 메시지를 서버로 전송
                    sendMessageForInvite(webSocket, username, Integer.parseInt(roomId), INVITE, invitee, userId);
                    showMenu(webSocket, username, userId);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("Error inviting user to room", e);
                }
            });
        });
    }

    private void sendMessageForInvite(WebSocket webSocket, String username, Integer roomId, String action, String invitee, AtomicInteger userId) {
        ChatItem chatItem = new ChatItem();
        chatItem.setUserId(userId.get());
        chatItem.setSenderEmail(username);
        chatItem.setAction(action);
        chatItem.setRoomId(roomId);
        chatItem.setMessage(invitee);
        webSocket.writeTextMessage(Json.encode(chatItem));
    }

    private void selectChatRoomList(WebSocket webSocket, String username, AtomicInteger userId) {
//        logger.debug("username : " + username + ", userId : " + userId.get());
        ChatItem request = new ChatItem();
        request.setSenderEmail(username);
        request.setUserId(userId.get());
        request.setAction(SELECT_CHATROOMLIST);
//        logger.debug("request : " + request);
        webSocket.writeTextMessage(Json.encode(request));
        showMenu(webSocket, username, userId);
    }

    private void selectChatRoom(WebSocket webSocket, String username, AtomicInteger userId, Integer roomId) {
        ChatItem request = new ChatItem();
        request.setSenderEmail(username);
        request.setUserId(userId.get());
        request.setAction(SELECT_CHATROOM);
        request.setRoomId(roomId);
        logger.debug("request : " + request);
        webSocket.writeTextMessage(Json.encode(request));
    }


    // --- Chat session related methods ---
    private void handleChatSession(WebSocket webSocket, String username, Integer roomId, AtomicInteger userId) {
        webSocket.textMessageHandler(message -> {
            if (message.trim().startsWith("[")) {
                // 메시지가 여러 ChatItem 객체라면 (Message is a JsonArray)
                JsonArray jsonArray = new JsonArray(message);
//                        System.out.println("==================================================\n    Received jsonArray: " + jsonArray);
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonObject roomJson = jsonArray.getJsonObject(i);
                    System.out.println("==================================================\n    roomJson(" + i + ") : " + roomJson);
                    ChatRoom chatRoom = Json.decodeValue(roomJson.encode(), ChatRoom.class);
                    chatRooms.add(chatRoom);
                }
            } else {
                // 그렇지 않다면 단일 ChatItem 객체로 역직렬화 합니다.
                ChatItem response = Json.decodeValue(message, ChatItem.class);
                switch (response.getAction()) {
                    case "setUserId":
                        System.out.println("==================================================\n        setUserId");
                        System.out.println("    >> " + response);
                        break;
                    case MESSAGE:
                    case MESSAGE_DUPLICATED:
//                    System.out.println("    >> |  action : " + response.getAction() + "" +
//                            "       |  user : " + response.getSenderEmail() + " : " + response.getMessage());


//                    Set<ChatRoomActiveStatus> chatRoomActiveStatus = response.getChatRoomActiveStatus();
//                    for (ChatRoomActiveStatus chatRoomActiveStatus1 : chatRoomActiveStatus) {
//                        System.out.println("    >> |  action : " + response.getAction() +
//                                "       |  user : " + response.getSenderEmail() + " : " + response.getMessage() +
//                                "       |  chatRoomActiveStatus.userId : " + chatRoomActiveStatus1.getUserId() +
//                                "       |  chatRoomActiveStatus.userId : " + chatRoomActiveStatus1.getUserId() +
//                                "       |  chatRoomActiveStatus.userId : " + chatRoomActiveStatus1.getUserId()
//                        );
//                    }
                        System.out.println("    >> |  action : " + response.getAction() +
                                "       |  user : " + response.getSenderEmail() + " : " + response.getMessage() +
                                "       |  chatRoomActiveStatus : " + response.getChatRoomActiveStatus());

                        //                    System.out.println("    >> "+response);
                        break;
                    case START_IMAGE_DOWNLOAD:
//                    System.out.println("        >> " + response.getSenderEmail() + ": Image Name : " + response.getMessage());
                        System.out.println("    >> " + response);
                        downloadImage(response.getMessage(), webSocket, username, roomId);
                        break;
                }
            }
        });
        readInputAndSendMessage(webSocket, username, roomId, userId);
    }

    private void readInputAndSendMessage(WebSocket webSocket, String username, Integer roomId, AtomicInteger userId) {
        System.out.println("Enter your messages below (type '/leave' to quit or '/sendImage' to send an image):");
        readInputFromUserToExecuteBlocking(messageText -> {
            switch (messageText) {
                case "/leave":
                    leaveChatRoom(webSocket, username, roomId, userId);
                    break;
                case "/unwatch":
                    unwatchChatRoom(webSocket, username, roomId, userId);
                    break;
                case "/sendImage":
                    handleImageUploadFromChat(webSocket, username, roomId, userId);
                    break;
                default:
                    sendMessage(webSocket, username, roomId, MESSAGE, messageText, userId);
                    readInputAndSendMessage(webSocket, username, roomId, userId);
            }
        });
    }

    private void leaveChatRoom(WebSocket webSocket, String username, Integer roomId, AtomicInteger userId) {
        ChatItem chatItem = new ChatItem();
        chatItem.setSenderEmail(username);
        chatItem.setRoomId(roomId);
        chatItem.setAction(LEAVE);
        webSocket.writeTextMessage(Json.encode(chatItem));
        showMenu(webSocket, username, userId);
    }

    private void unwatchChatRoom(WebSocket webSocket, String username, Integer roomId, AtomicInteger userId) {
        ChatItem chatItem = new ChatItem();
        chatItem.setSenderEmail(username);
        chatItem.setRoomId(roomId);
        chatItem.setAction(UNWATCH);
        webSocket.writeTextMessage(Json.encode(chatItem));
        // After unwatching, you might want to redirect the user back to the main menu.
        showMenu(webSocket, username, userId);
    }

    // --- Room related methods ---
    private void handleRoomInput(String prompt, String action, WebSocket webSocket, String username, AtomicInteger userId) {
        System.out.println(prompt);
        readInputFromUserToExecuteBlocking(roomId -> {
            try {
                System.out.println("==================================================\n        handleRoomInput - outside");
                System.out.println("    >> userId : " + userId + ", email : " + username + ", roomId : " + roomId + ", action : " + action);
                sendMessage(webSocket, username, Integer.parseInt(roomId), action, "", userId);
                if (JOIN.equals(action) || WATCH.equals(action))
                    handleChatSession(webSocket, username, Integer.parseInt(roomId), userId);
                else if (SELECT_CHATROOM.equals(action))
                    selectChatRoom(webSocket, username, userId, Integer.parseInt(roomId));
                else showMenu(webSocket, username, userId);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error reading room id", e);
            }
        });
    }

    // --- General utility methods ---
    private void sendMessage(WebSocket webSocket, String username, Integer roomId, String action, String
            messageText, AtomicInteger userId) {
        ChatItem messageItem = new ChatItem();
        messageItem.setSenderEmail(username);
        messageItem.setRoomId(roomId);
        messageItem.setAction(action);
        messageItem.setMessage(messageText);
        messageItem.setUserId(userId.get());
//        messageItem.setMessageId(UUID.randomUUID().toString());
        webSocket.writeTextMessage(Json.encode(messageItem));
    }

    private void readInputFromUserToExecuteBlocking(Handler<String> handler) {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            String messageText = scanner.nextLine();
            vertx.runOnContext(v -> handler.handle(messageText));
        }).start();
    }

    // --- Start and Main methods ---
    @Override
    public void start() {
        promptForUsername();
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new ChatClientWithRemoteServer());
    }

    // --- Image related methods ---
    private void handleImageUploadFromChat(WebSocket webSocket, String username, Integer roomId, AtomicInteger
            userId) {
        System.out.println("Enter the image file path:");
        readInputFromUserToExecuteBlocking(imagePath -> {
            try {
                ChatItem chatItem = new ChatItem();
                chatItem.setAction(PREPARE_IMAGE_UPLOAD);
                chatItem.setSenderEmail(username);
                chatItem.setRoomId(roomId);
                webSocket.writeTextMessage(Json.encode(chatItem));

                // HTTP POST 요청을 사용하여 이미지 업로드
                MultipartForm form = MultipartForm.create()
                        .attribute("imageDescription", "a very nice image")
                        .binaryFileUpload(
                                "imageFile",
                                new File(imagePath).getName(),
                                imagePath,
                                getMediaType(imagePath));
                WebClient client = WebClient.create(vertx);
                client.post(PORT2, HOST, "/upload")
                        .sendMultipartForm(form)
                        .onSuccess(response -> {
                            if (response.statusCode() == 200) {
//                                System.out.println("    Image uploaded successfully !!");
                                String fileNameOnServer = response.statusMessage();
                                System.out.println("    fileNameOnServer    : " + fileNameOnServer);
                                System.out.println("    roomId            : " + roomId);
                                sendMessage(webSocket, username, roomId, IMAGE_UPLOADED, fileNameOnServer, userId);
                            } else {
                                System.out.println("Image uploaded smth wrong !!");
                            }
                            readInputAndSendMessage(webSocket, username, roomId, userId); // Return back to the chat session
                        })
                        .onFailure(err -> {
                            logger.error("Error during image upload", err);
                            readInputAndSendMessage(webSocket, username, roomId, userId); // Return back to the chat session on failure as well
                        });
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error reading image path", e);
                readInputAndSendMessage(webSocket, username, roomId, userId); // Return back to the chat session if there's an error reading the path
            } finally {
                System.out.println("keep going");
            }
        });
    }

    private void downloadImage(String imagePath, WebSocket webSocket, String username, Integer roomId) {
        WebClient client = WebClient.create(vertx);
        client.get(PORT2, HOST, "/download/" + imagePath)
                .send(ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();

                        // Debugging lines
                        String fileName = downloadDirectory + imagePath;
                        if (fileName == null) {
                            System.err.println("fileName is null");
                            return;
                        }
                        if (response.body() == null) {
                            System.err.println("response.body() is null");
                            return;
                        }

                        // Assuming you want to save the image to the current directory with the same filename.
                        System.out.println("        fileName " + fileName);
//                        System.out.println("        response.body() " + response.body());
                        vertx.fileSystem().writeFile(fileName, response.body(), writeRes -> {
                            if (writeRes.succeeded()) {
                                System.out.println("        Image downloaded and saved as " + fileName);
                            } else {
                                System.err.println("Failed to save the image: " + writeRes.cause().getMessage());
                            }
                        });
                    } else {
                        System.err.println("Failed to download the image: " + ar.cause().getMessage());
                    }
                });
    }

    private static String getMediaType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "txt" -> "text/plain";
            case "aac" -> "audio/aac";
//            case "mp3" -> "audio/mp3";
            case "mp3" -> "audio/mpeg";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case ".webm" -> "video/webm";
            default -> "application/octet-stream";
        };
    }
}
