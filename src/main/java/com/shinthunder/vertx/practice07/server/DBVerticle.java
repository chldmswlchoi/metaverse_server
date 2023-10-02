package com.shinthunder.vertx.practice07.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.shinthunder.vertx.practice07.server.MainServer.*;

public class DBVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(DBVerticle.class);
    private Map<String, Handler<Message<JsonObject>>> actionHandlers;
    private JDBCClient jdbcClient;

    @Override
    public void start() {
        setupDB();
        setupActionHandlers();
        vertx.eventBus().consumer(ADDRESS_DB_ACTION, this::handleDatabaseActions);
    }

    private void setupDB() {
        JsonObject mysqlConfig1 = new JsonObject()
                .put("driver_class", "com.mysql.cj.jdbc.Driver")//
                .put("max_pool_size", 30)//
                .put("user", JDBC_USER)//
                .put("url", JDBC_URL) //
                .put("password", JDBC_PASSWORD);
        jdbcClient = JDBCClient.createShared(vertx, mysqlConfig1);
    }

    private void setupActionHandlers() {
        actionHandlers = new HashMap<>();
        actionHandlers.put("insertChatItem", this::insertChatItem);
//        actionHandlers.put("updateUser", this::updateUser);
        actionHandlers.put("selectUserByEmail", this::selectUserByEmail);
        actionHandlers.put("selectChatRoomWhereId", this::selectChatRoomWhereId);
        actionHandlers.put("selectChatRoom", this::selectChatRoom);
        actionHandlers.put("selectChatRoomParticipant", this::selectChatRoomParticipant);
        actionHandlers.put("selectChatItem", this::selectChatItem);
        actionHandlers.put("insertUser", this::insertUser);
        actionHandlers.put("insertChatRoom", this::insertChatRoom);
        actionHandlers.put("insertChatRoomParticipant", this::insertChatRoomParticipant);
    }

    private void handleDatabaseActions(Message<JsonObject> message) {
        String action = message.body().getString("action");
        Handler<Message<JsonObject>> handler = actionHandlers.get(action);
        if (handler != null) handler.handle(message);
        else message.reply(new JsonObject().put("error", "Unknown action"));
    }

    private void selectUserByEmail(Message<JsonObject> message) {
        String userEmail = message.body().getString("userEmail");

        String query = "SELECT * FROM User WHERE user_email = ?";
        JsonArray params = new JsonArray().add(userEmail);
//        System.out.println("query : "+query);
//        System.out.println("params : "+params);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
//                System.out.println("results : "+results);
                if (!results.isEmpty()) {
                    message.reply(new JsonObject()
                            .put("status", "success")
                            .put("data", results.get(0))
                    );
                } else {
                    message.reply(new JsonObject()
                            .put("status", "error")
                            .put("message", "User not found")
                    );
                }
            } else {
//                System.out.println("res.cause().getMessage() : "+res.cause().getMessage());
                message.reply(new JsonObject()
                        .put("status", "error")
                        .put("message", res.cause().getMessage())
                );
            }
        });
    }

    private void selectChatRoomWhereId(Message<JsonObject> message) {
        String roomId = message.body().getString("roomId");
        String query = "SELECT * FROM ChatRoom WHERE id = ?";
        JsonArray params = new JsonArray().add(roomId);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    message.reply(new JsonObject().put("status", "success").put("data", results.get(0)));
                } else {
                    message.reply(new JsonObject().put("status", "error").put("message", "ChatRoom not found"));
                }
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoom(Message<JsonObject> message) {
        String roomName = message.body().getString("roomName");
        String query = "SELECT * FROM ChatRoom WHERE name = ?";
        JsonArray params = new JsonArray().add(roomName);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    message.reply(new JsonObject().put("status", "success").put("data", results.get(0)));
                } else {
                    message.reply(new JsonObject().put("status", "error").put("message", "ChatRoom not found"));
                }
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoomParticipant(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        String query = "SELECT * FROM ChatRoomParticipant WHERE room_id = ?";
        JsonArray params = new JsonArray().add(roomId);
//        System.out.println("==================================================");
//        System.out.println("    query : " + query);
//        System.out.println("    params : " + params);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
//                System.out.println("    results : " + results);
                if (!results.isEmpty()) {
                    message.reply(new JsonObject().put("status", "success").put("data", results.get(0)));
                } else {
                    message.reply(new JsonObject().put("status", "error").put("message", "Participant not found"));
                }
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatItem(Message<JsonObject> message) {
        Integer itemId = message.body().getInteger("itemId");
        String query = "SELECT * FROM ChatItem WHERE id = ?";
        JsonArray params = new JsonArray().add(itemId);

        jdbcClient.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    message.reply(new JsonObject().put("status", "success").put("data", results.get(0)));
                } else {
                    message.reply(new JsonObject().put("status", "error").put("message", "ChatItem not found"));
                }
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertUser(Message<JsonObject> message) {
        String userEmail = message.body().getString("userEmail");
        String query = "INSERT INTO User (user_email, user_password) VALUES (?, ?)";
        JsonArray params = new JsonArray().add(userEmail).add("some_default_password");

        jdbcClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                Integer generatedId = res.result().getKeys().getInteger(0);
                message.reply(new JsonObject().put("status", "success").put("generatedId", generatedId));
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertChatRoom(Message<JsonObject> message) {
        String roomName = message.body().getString("roomName");
        String query = "INSERT INTO ChatRoom (name) VALUES (?)";
        JsonArray params = new JsonArray().add(roomName);

        jdbcClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                Integer generatedId = res.result().getKeys().getInteger(0);
                message.reply(new JsonObject().put("status", "success").put("generatedId", generatedId));
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertChatRoomParticipant(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        Integer userId = message.body().getInteger("userId");
        String query = "INSERT INTO ChatRoomParticipant (room_id, user_id) VALUES (?, ?)";
        JsonArray params = new JsonArray()
                .add(roomId)
                .add(userId);

        jdbcClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                System.out.println("111111");
                message.reply(new JsonObject().put("status", "success"));
            } else {
                System.out.println("22222222");
                message.reply(new JsonObject().put("status", "error").put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertChatItem(Message<JsonObject> message) {
        logger.info("=================== insertChatItem() ===============================");
        JsonObject chatItem = message.body().getJsonObject("chatItem");
        String query = "INSERT INTO ChatItem (room_id, sender_id, message) VALUES (?, ?, ?)";
        JsonArray params = new JsonArray()
                .add(chatItem.getInteger("room_id"))
                .add(chatItem.getInteger("sender_id"))
                .add(chatItem.getString("message"));

        jdbcClient.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put("status", "success"));
            } else {
                message.reply(new JsonObject().put("status", "error").put("message", res.cause().getMessage()));
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
