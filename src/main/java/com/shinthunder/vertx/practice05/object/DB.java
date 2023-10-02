package com.shinthunder.vertx.practice05.object;

public class DB {
//    private void insertUser(String username, String socketAddress) {
//        String sql = "INSERT INTO User (username, socket_address) VALUES (?, ?)";
//        JsonArray params = new JsonArray().add(username).add(socketAddress);
//
//        jdbcClient.updateWithParams(sql, params, ar -> {
//            if (ar.succeeded()) {
//                System.out.println("User inserted successfully!");
//            } else {
//                System.err.println("Failed to insert user: " + ar.cause());
//            }
//        });
//    }
//
//    private void selectUser(String username, Handler<AsyncResult<JsonObject>> handler) {
//        String sql = "SELECT id, socket_address FROM User WHERE username = ?";
//        JsonArray params = new JsonArray().add(username);
//
//        jdbcClient.queryWithParams(sql, params, ar -> {
//            if (ar.succeeded() && !ar.result().getRows().isEmpty()) {
//                handler.handle(Future.succeededFuture(ar.result().getRows().get(0)));
//            } else {
//                handler.handle(Future.failedFuture("User not found or error: " + ar.cause()));
//            }
//        });
//    }
//
//    private void insertChatRoom(String roomName) {
//        String sql = "INSERT INTO ChatRoom (name) VALUES (?)";
//        JsonArray params = new JsonArray().add(roomName);
//
//        jdbcClient.updateWithParams(sql, params, ar -> {
//            if (ar.succeeded()) {
//                System.out.println("Chat room inserted successfully!");
//            } else {
//                System.err.println("Failed to insert chat room: " + ar.cause());
//            }
//        });
//    }
//
//    private void updateChatRoom(int roomId, String newRoomName) {
//        String sql = "UPDATE ChatRoom SET name = ? WHERE id = ?";
//        JsonArray params = new JsonArray().add(newRoomName).add(roomId);
//
//        jdbcClient.updateWithParams(sql, params, ar -> {
//            if (ar.succeeded()) {
//                System.out.println("Chat room updated successfully!");
//            } else {
//                System.err.println("Failed to update chat room: " + ar.cause());
//            }
//        });
//    }
//
//    private void selectChatRoom(String roomName, Handler<AsyncResult<JsonObject>> handler) {
//        String sql = "SELECT id FROM ChatRoom WHERE name = ?";
//        JsonArray params = new JsonArray().add(roomName);
//
//        jdbcClient.queryWithParams(sql, params, ar -> {
//            if (ar.succeeded() && !ar.result().getRows().isEmpty()) {
//                handler.handle(Future.succeededFuture(ar.result().getRows().get(0)));
//            } else {
//                handler.handle(Future.failedFuture("Chat room not found or error: " + ar.cause()));
//            }
//        });
//    }
//
//    private void insertChatItem(int roomId, int senderId, String content) {
//        String sql = "INSERT INTO ChatItem (room_id, sender_id, content) VALUES (?, ?, ?)";
//        JsonArray params = new JsonArray().add(roomId).add(senderId).add(content);
//
//        jdbcClient.updateWithParams(sql, params, ar -> {
//            if (ar.succeeded()) {
//                System.out.println("Chat item inserted successfully!");
//            } else {
//                System.err.println("Failed to insert chat item: " + ar.cause());
//            }
//        });
//    }
}
