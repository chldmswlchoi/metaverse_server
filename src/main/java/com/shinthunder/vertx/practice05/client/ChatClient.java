package com.shinthunder.vertx.practice05.client;

import com.shinthunder.vertx.practice05.object.ChatItem;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import static com.shinthunder.vertx.practice05.server.ChatServer.*;

public class ChatClient extends AbstractVerticle {

    private final Logger logger = LoggerFactory.getLogger(ChatClient.class);
    private static final String downloadDirectory = "/Users/hawaii/Downloads/FileUpDownload-testbed-client-to-download/";
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
        AtomicInteger userId = new AtomicInteger();
        vertx.createHttpClient().webSocket(WEBSOCKET_PORT, HOST_SERVER, "/", webSocketAsyncResult -> {
            if (webSocketAsyncResult.succeeded()) {

                // TODO : setUserId를 받기 위해서 임시로 메시지 수신 코드 넣음
                WebSocket webSocket = webSocketAsyncResult.result();
                webSocket.textMessageHandler(message -> {
                    ChatItem response = Json.decodeValue(message, ChatItem.class);
//                    switch (response.getAction()) {
//                        case "setUserId":
                    System.out.println("==================================================\n        setUserId");
                    System.out.println("    >> userId : " + response.getUserId() + ", email : " + response.getSenderEmail() + ", msg : " + response.getMessage());
                    userId.set(response.getUserId());
//                            break;
//                    }
                });


                ChatItem chatItem = new ChatItem();
                chatItem.setSenderEmail(username);
                chatItem.setUserId(userId.get());
                chatItem.setAction("connect");
                webSocketAsyncResult.result().writeTextMessage(Json.encode(chatItem)).onComplete(res -> {
                    if (res.succeeded()) {
                        logger.info("Connected to WebSocket as user: {}", username);
                        logger.info("Connected to WebSocket as userId: {}", userId);
                    } else {
                        logger.error("Failed to connect from WebSocket", res.cause());
                    }
                });
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
                System.out.println("Enter room name to create:");
                handleRoomInput("Enter room name to create:", "create", webSocket, username, userId);
                break;
            case "2":
                logger.info("Option 'Join room' selected by user: {}", username);
                System.out.println("Enter room name to join:");
                handleRoomInput("Enter room name to join:", "join", webSocket, username, userId);
                break;
            case "3":
                logger.info("Option 'Leave room' selected by user: {}", username);
                System.out.println("Enter room name to leave:");
                handleRoomInput("Enter room name to leave:", "leave", webSocket, username, userId);
                break;
            case "4":
                logger.info("Option 'Disconnect' selected by user: {}", username);
                ChatItem chatItem = new ChatItem();
                chatItem.setAction("disconnect");
                webSocket.writeTextMessage(Json.encode(chatItem));
                webSocket.close();
                System.exit(0);
                break;
            case "5":
                selectChatRoomList(webSocket, username, userId);
                break;
            case "6":
                selectChatRoom(webSocket, username, userId, "roomName");
                break;
            default:
                System.out.println("Invalid option. Try again.");
                showMenu(webSocket, username, userId);
        }
    }

    private void selectChatRoomList(WebSocket webSocket, String username, AtomicInteger userId) {
        ChatItem request = new ChatItem();
        request.setSenderEmail(username);
        request.setUserId(userId.get());
        request.setAction("selectChatRoomList");
        webSocket.writeTextMessage(Json.encode(request));
    }

    private void selectChatRoom(WebSocket webSocket, String username, AtomicInteger userId, String roomName) {
        ChatItem request = new ChatItem();
        request.setSenderEmail(username);
        request.setUserId(userId.get());
        request.setAction("selectChatRoom");
        request.setRoomName(roomName);
        webSocket.writeTextMessage(Json.encode(request));
    }

    // --- Chat session related methods ---
    private void handleChatSession(WebSocket webSocket, String username, String roomName, AtomicInteger userId) {
        webSocket.textMessageHandler(message -> {
            ChatItem response = Json.decodeValue(message, ChatItem.class);
//            System.out.println("    Received message from server: " + message);
//            System.out.println("        roomName : " + roomName);
            switch (response.getAction()) {
                case "setUserId":
                    System.out.println("==================================================\n        setUserId");
                    System.out.println("    >> " + response.getUserId() + " : " + response.getSenderEmail() + " : " + response.getMessage());
                    break;
                case "message":
                    System.out.println("    >> " + response.getSenderEmail() + " : " + response.getMessage());
                    break;
                case "startImageDownload":
                    System.out.println("        >> " + response.getSenderEmail() + ": Image Name : " + response.getMessage());
                    downloadImage(response.getMessage(), webSocket, username, roomName);
                    break;
            }
        });
        readInputAndSendMessage(webSocket, username, roomName, userId);
    }

    private void readInputAndSendMessage(WebSocket webSocket, String username, String roomName, AtomicInteger userId) {
        System.out.println("Enter your messages below (type '/leave' to quit or '/sendImage' to send an image):");
        readInputFromUserToExecuteBlocking(messageText -> {
            if ("/leave".equalsIgnoreCase(messageText)) {
                leaveChatRoom(webSocket, username, roomName, userId);
            } else if ("/sendImage".equalsIgnoreCase(messageText)) {
                handleImageUploadFromChat(webSocket, username, roomName, userId); // Call the new method to handle image upload from chat session
            } else {
                sendMessage(webSocket, username, roomName, "message", messageText, userId);
                readInputAndSendMessage(webSocket, username, roomName, userId);
            }
        });
    }

    private void leaveChatRoom(WebSocket webSocket, String username, String roomName, AtomicInteger userId) {
        ChatItem chatItem = new ChatItem();
        chatItem.setSenderEmail(username);
        chatItem.setRoomName(roomName);
        chatItem.setAction("leave");
        webSocket.writeTextMessage(Json.encode(chatItem));
        showMenu(webSocket, username, userId);
    }

    // --- Room related methods ---
    private void handleRoomInput(String prompt, String action, WebSocket webSocket, String username, AtomicInteger userId) {
        readInputFromUserToExecuteBlocking(roomName -> {
            try {
                // TODO : setUserId를 받기 위해서 임시로 메시지 수신 코드 넣음
//                WebSocket webSocket = roomName.result();
                webSocket.textMessageHandler(message -> {
                    try {
                        System.out.println("message : " + message);
//                        JsonObject receivedJson = new JsonObject(message);
//
//                        // If the received JSON contains a specific field (for example, "chatRooms"),
//                        // you can process it into a list of ChatRoom.
//                        if (receivedJson.containsKey("chatRooms")) {
//                            JsonArray chatRoomsArray = receivedJson.getJsonArray("chatRooms");
//                            List<ChatRoom> chatRooms = chatRoomsArray.stream()
//                                    .map(obj -> ((JsonObject) obj).encode()) // Convert each JsonObject to a String
//                                    .map(ChatRoom::fromJson) // Now you can use ChatRoom::fromJson without error
//                                    .toList();
//
//                            System.out.println("==================================================");
//                            System.out.println("        handleRoomInput - inside");
//
//                            for (ChatRoom chatRoom : chatRooms) {
//                                System.out.println("    >> " + chatRoom);
//                                System.out.println("    >> " + chatRoom.toString());
//                            }
//                        }

                        // Here, you can add additional handling for other fields or types of messages
                        // that may be sent to this client.

                    } catch (Exception e) {
                        System.err.println("Failed to process the received message.");
                        e.printStackTrace();
                    }
                });

                System.out.println("==================================================\n        handleRoomInput - outside");
                System.out.println("    >> userId : " + userId + ", email : " + username + ", roomName : " + roomName);
                sendMessage(webSocket, username, roomName, action, "", userId);
                if ("join".equals(action)) handleChatSession(webSocket, username, roomName, userId);
                else showMenu(webSocket, username, userId);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error reading room name", e);
            }
        });
    }

    // --- Image related methods ---
    private void handleImageUploadFromChat(WebSocket webSocket, String username, String roomName, AtomicInteger
            userId) {
        System.out.println("Enter the image file path:");
        readInputFromUserToExecuteBlocking(imagePath -> {
            try {
                ChatItem chatItem = new ChatItem();
                chatItem.setAction("prepareImageUpload");
                chatItem.setSenderEmail(username);
                chatItem.setRoomName(roomName);
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
                client.post(HTTP_PORT, HOST_SERVER, "/upload")
                        .sendMultipartForm(form)
                        .onSuccess(response -> {
                            if (response.statusCode() == 200) {
//                                System.out.println("    Image uploaded successfully !!");
                                String fileNameOnServer = response.statusMessage();
//                                System.out.println("    fileNameOnServer    : " + fileNameOnServer);
//                                System.out.println("    roomName            : " + roomName);
                                sendMessage(webSocket, username, roomName, "imageUploaded", fileNameOnServer, userId);
                            } else {
                                System.out.println("Image uploaded smth wrong !!");
                            }
                            readInputAndSendMessage(webSocket, username, roomName, userId); // Return back to the chat session
                        })
                        .onFailure(err -> {
                            logger.error("Error during image upload", err);
                            readInputAndSendMessage(webSocket, username, roomName, userId); // Return back to the chat session on failure as well
                        });
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error reading image path", e);
                readInputAndSendMessage(webSocket, username, roomName, userId); // Return back to the chat session if there's an error reading the path
            } finally {
                System.out.println("keep going");
            }

        });
    }

    private void downloadImage(String imagePath, WebSocket webSocket, String username, String roomName) {
        WebClient client = WebClient.create(vertx);
        client.get(HTTP_PORT, HOST_SERVER, "/download/" + imagePath)
                .send(ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        // Assuming you want to save the image to the current directory with the same filename.
                        String fileName = downloadDirectory + imagePath;
                        vertx.fileSystem().writeFile(fileName, response.body(), writeRes -> {
                            if (writeRes.succeeded()) {
//                                System.out.println("        Image downloaded and saved as " + fileName);
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
            case "mp3" -> "audio/mp3";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case ".webm" -> "video/webm";
            default -> "application/octet-stream";
        };
    }

    // --- General utility methods ---
    private void sendMessage(WebSocket webSocket, String username, String roomName, String action, String
            messageText, AtomicInteger userId) {
        ChatItem messageItem = new ChatItem();
        messageItem.setSenderEmail(username);
        messageItem.setRoomName(roomName);
        messageItem.setAction(action);
        messageItem.setMessage(messageText);
        messageItem.setUserId(userId.get());
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
        vertx.deployVerticle(new ChatClient());
    }
}