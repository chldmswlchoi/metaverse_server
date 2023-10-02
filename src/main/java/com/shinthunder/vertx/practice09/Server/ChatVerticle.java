package com.shinthunder.vertx.practice09.Server;

import com.shinthunder.vertx.practice09.Handler.WebSocketHandler;
import com.shinthunder.vertx.practice09.Object.ChatItem;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static com.shinthunder.vertx.practice09.Server.MainServer.*;


public class ChatVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ChatVerticle.class);
    private WebSocketHandler webSocketHandler;
    private String thisVerticleID;

    // -------------------------- START METHODS --------------------------
    @Override
    public void start() {
//        thisVerticleID = deploymentID();
        thisVerticleID = UUID.randomUUID().toString();
        logger.info("==================================================\n    thisVerticleID : " + thisVerticleID);
        webSocketHandler = new WebSocketHandler(vertx, thisVerticleID);
        initializeSharedData();
        configureWebSocketServer();
        setupEventBusMessageHandler();
    }

    private void initializeSharedData() {
        vertx.sharedData().<String, String>getAsyncMap("userToSocketMap", res -> {
            if (res.succeeded()) {
                webSocketHandler.setUserEmailToSocketMap(res.result());
            } else {
                logger.error("Error initializing userToSocketMapAsync:", res.cause());
            }
        });
    }

    private void configureWebSocketServer() {
        vertx.createHttpServer()
                .webSocketHandler(this::webSocketHandler)
                .exceptionHandler(e -> logger.error("Error occurred with server: {}", e.getMessage()))
                .listen(WEBSOCKET_PORT, HOST_SERVER, res -> {
                    if (res.succeeded()) {
                        logger.info("Server is now listening on port {}", WEBSOCKET_PORT);
                    } else {
                        logger.error("Failed to bind on port PORT: {}", res.cause().getMessage());
                    }
                });
    }

    public void webSocketHandler(ServerWebSocket socket) {
        logger.info("Client connected: {}", socket.remoteAddress().host());
        webSocketHandler.addClients(socket);
        socket.handler(buffer -> {
            try {
                logger.info("==================================================\n" +
                        "   Received raw message: {}" +
                        "\n==================================================\n", buffer.toString());
                ChatItem chatItem = Json.decodeValue(buffer, ChatItem.class);
                webSocketHandler.handleClientAction(socket, chatItem);
            } catch (Exception e) {
                logger.error("Failed to process message from {}: {}", socket.remoteAddress().host(), e.getMessage(), e);
            }
        }).exceptionHandler(e -> {
            logger.error("Error occurred with client {}: {}", socket.remoteAddress().host(), e.getMessage());
        }).closeHandler(v -> {
            webSocketHandler.disconnectClient(socket).onComplete(ar -> {
                if (ar.succeeded()) {
                    logger.info("Client disconnected: {}", socket.remoteAddress().host());
                } else {
                    logger.error("Error handling disconnection: {}", ar.cause().getMessage());
                }
            });
//            webSocketHandler.removeSocketFromUserMap(socket);
//            webSocketHandler.removeClients(socket);
        });
    }

    private void setupEventBusMessageHandler() {
        vertx.eventBus().consumer(ADDRESS_BROADCAST_MESSAGE, message -> {
            // Convert the received Buffer to a String
            String jsonStr = (message.body()).toString();

            // Now parse the String into a JsonObject
            JsonObject receivedMessage = new JsonObject(jsonStr);
            String origin = receivedMessage.getString("origin");
            String userEmail = receivedMessage.getString("userEmail");
            logger.info("Received message via event bus. {} : {}", userEmail, origin);
            ChatItem chatItem = receivedMessage.getBuffer("chatItem").toJsonObject().mapTo(ChatItem.class);
//            logger.info("origin : " + origin);
//            logger.info("chatItem : " + chatItem);
            if (!thisVerticleID.equals(origin)) {
                // Decode the chatItem from the received message
//                ChatItem chatItem = Json.decodeValue(receivedMessage.getJsonObject("chatItem").encode(), ChatItem.class);

                // Process the chatItem as required (in this case, broadcasting it)
//                logger.info("==================================================\n       thisVerticleID != origin");
                chatItem.setAction(MESSAGE_DUPLICATED);
                webSocketHandler.broadcastMessageInRoom(chatItem);
            } else {
//                logger.info("==================================================\n       thisVerticleID == origin");
            }
        });
    }
}