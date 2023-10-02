package com.shinthunder.vertx.practice09.Utility;

import com.shinthunder.vertx.practice09.Object.ChatItem;
import com.shinthunder.vertx.practice09.Object.ChatRoom;
import com.shinthunder.vertx.practice09.Object.ChatRoomActiveStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.shinthunder.vertx.practice09.Server.MainServer.*;
import static com.shinthunder.vertx.practice09.Server.MainServer.STATUS;

public class Util {
    private static final Logger logger = LoggerFactory.getLogger(Util.class);
    private final Vertx vertx;

    public Util(Vertx vertx) {
        this.vertx = vertx;
    }

    public void insertChatRoom(String relatedType, Integer relatedId, Integer talentId, Handler<AsyncResult<Integer>> handler) {
        JsonObject payloadInsertChatRoom = new JsonObject()
                .put("action", insertChatRoom)
                .put("relatedType", relatedType)
                .put("relatedId", relatedId)
                .put("talentId", talentId);

        System.out.println("Inserting new Chat Room");
        System.out.println("        relatedType : " + relatedType);
        System.out.println("        relatedId : " + relatedId);
        System.out.println("        talentId : " + talentId);
        vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoom, reply -> {
            System.out.println("Chat Room Insertion Reply Received");
            if (reply.succeeded()) {
                JsonObject dbResponse = (JsonObject) reply.result().body();
                if (SUCCESS.equals(dbResponse.getString(STATUS))) {
                    System.out.println("    generated roomId : " + dbResponse.getInteger("generatedId"));
                    handler.handle(Future.succeededFuture(dbResponse.getInteger("generatedId")));
                } else {
                    System.out.println("    failed to generate roomId : " + dbResponse.getString("message"));
                    handler.handle(Future.failedFuture(dbResponse.getString("message")));
                }
            } else {
                handler.handle(Future.failedFuture(reply.cause()));
            }
        });
    }

    public void insertChatItem(ChatItem chatItem, Integer roomId, Handler<JsonObject> handler) {
        JsonObject messageObject = new JsonObject()
                .put("room_id", roomId)
                .put("sender_id", chatItem.getUserId())
                .put("message", chatItem.getMessage())
                .put("action", chatItem.getAction());
        JsonObject payload = new JsonObject()
                .put("action", insertChatItemAndSelectTimestamp)
                .put("chatItem", messageObject);

        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, insertRes -> {
            JsonObject dbResponse = (JsonObject) insertRes.result().body();
            handler.handle(dbResponse);
        });
    }

    public void insertChatRoomActiveStatus(Integer roomId, Integer userId, Handler<AsyncResult<Void>> handler) {
        JsonObject payloadInsertChatRoomActiveStatus = new JsonObject()
                .put("action", insertChatRoomActiveStatus)
                .put("roomId", roomId)
                .put("userId", userId)
                .put("isWatching", 0);
        vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoomActiveStatus, reply -> {
            JsonObject dbResponse = (JsonObject) reply.result().body();
            if (SUCCESS.equals(dbResponse.getString(STATUS))) handler.handle(Future.succeededFuture());
            else handler.handle(Future.failedFuture(dbResponse.getString("message")));
        });
    }

    public void insertChatRoomParticipant(Integer roomId, Integer inviteeId, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", insertChatRoomParticipant)
                .put("roomId", roomId)
                .put("userId", inviteeId);

        vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payload, reply -> {
            if (reply.succeeded()) handler.handle(Future.succeededFuture((JsonObject) reply.result().body()));
            else handler.handle(Future.failedFuture(reply.cause()));
        });
    }

    public void insertChatRoomActiveStatus(Integer roomId, Integer userId, int isWatching, Handler<AsyncResult<Void>> handler) {
        JsonObject payloadInsertChatRoomActiveStatus = new JsonObject()
                .put("action", insertChatRoomActiveStatus)
                .put("roomId", roomId)
                .put("userId", userId)
                .put("isWatching", isWatching);

        vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadInsertChatRoomActiveStatus, reply -> {
            if (reply.succeeded()) handler.handle(Future.succeededFuture());
            else handler.handle(Future.failedFuture(reply.cause()));
        });
    }

    public void selectAllEmailsWHERERoomIdWithJoin(Integer roomId, Handler<List<String>> handler) {
        /**  room_id에 대응하는 ChatRoomParticipant 테이블의 모든 참여자의 이메일 주소를 user 테이블에서 검색하여 반환 */
        JsonObject payload = new JsonObject()
                .put("action", selectAllEmailsWHERERoomIdWithJoin)
                .put("roomId", roomId);
        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, usersRes -> {
            JsonObject dbResponse = (JsonObject) usersRes.result().body();
            if (ERROR.equals(dbResponse.getString(STATUS))) {
                logger.error("Failed to fetch users from chat room: {}", dbResponse.getString("message"));
                return;
            }
            List<String> userEmails = dbResponse.getJsonArray("message").stream().map(emailObj -> (String) emailObj).toList();
            handler.handle(userEmails);
        });
    }

    public void selectChatRoomActiveStatusByRoomId(Integer roomId, Handler<Set<ChatRoomActiveStatus>> handler) {
        JsonObject payloadActiveStatus = new JsonObject()
                .put("action", selectChatRoomActiveStatusByRoomId)
                .put("roomId", roomId);

        vertx.eventBus().request(ADDRESS_DB_ACTION, payloadActiveStatus, activeStatusReply -> {
            JsonObject dbResponseActiveStatus = (JsonObject) activeStatusReply.result().body();
            if (SUCCESS.equals(dbResponseActiveStatus.getString(STATUS))) {
                List<JsonObject> activeStatuses = dbResponseActiveStatus.getJsonArray("message").getList();
                Set<ChatRoomActiveStatus> chatRoomActiveStatusSet = _convertJsonToActiveStatusSet(activeStatuses);
//                System.out.println("         chatRoomActiveStatusSet : "+  chatRoomActiveStatusSet);
                handler.handle(chatRoomActiveStatusSet);
            } else handler.handle(null);
        });
    }

    public void selectChatRoomById(Integer roomId, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", selectChatRoomWhereId)
                .put("roomId", roomId);
        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, res -> {
            if (res.succeeded()) handler.handle(Future.succeededFuture((JsonObject) res.result().body()));
            else handler.handle(Future.failedFuture(res.cause()));
        });
    }

    public void selectChatRoomParticipantWhereUserId(Integer userId, Handler<AsyncResult<List<JsonObject>>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", selectChatRoomParticipantWhereUserId)
                .put("userId", userId);
        vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payload, reply -> {
            JsonObject dbResponse = (JsonObject) reply.result().body();
            System.out.println("        result selectChatRoomParticipantWhereUserId dbResponse : " + dbResponse);
            if (SUCCESS.equals(dbResponse.getString(STATUS))) {
                List<JsonObject> rooms = dbResponse.getJsonArray("message").getList();
                System.out.println("        SUCCESS selectChatRoomParticipantWhereUserId List<JsonObject> rooms : " + rooms);
                handler.handle(Future.succeededFuture(rooms));
            } else if (ERROR.equals(dbResponse.getString(STATUS))) {
                if ("Participant not found".equals(dbResponse.getString("message"))) {
                    List<JsonObject> rooms = new ArrayList<>();
                    rooms.add(JsonObject.mapFrom(new ChatRoom()));
                    System.out.println("        ERROR selectChatRoomParticipantWhereUserId List<JsonObject> rooms : " + rooms);
                    handler.handle(Future.succeededFuture(rooms));
                } else handler.handle(Future.failedFuture(dbResponse.getString("message")));
            }
        });
    }

    public void selectChatRoomParticipantWhereRoomId(Integer roomId, Handler<AsyncResult<List<JsonObject>>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", selectChatRoomParticipantWhereRoomId)
                .put("roomId", roomId);

        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, res -> {
            if (res.succeeded()) {
                JsonObject dbResponse = (JsonObject) res.result().body();
                if (SUCCESS.equals(dbResponse.getString(STATUS))) {
                    List<JsonObject> participants = dbResponse.getJsonArray("message").getList();
                    handler.handle(Future.succeededFuture(participants));
                } else handler.handle(Future.failedFuture(dbResponse.getString("message")));
            } else handler.handle(Future.failedFuture(res.cause()));
        });
    }

    public void selectUserById(Integer userId, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", "selectUserById")
                .put("userId", userId);

        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, res -> {
            if (res.succeeded()) handler.handle(Future.succeededFuture((JsonObject) res.result().body()));
            else handler.handle(Future.failedFuture(res.cause()));
        });
    }

    public void selectChatItemByRoomId(Integer roomId, Handler<AsyncResult<List<JsonObject>>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", selectChatItemWhereRoomId)
                .put("roomId", roomId);

        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, chatItemReply -> {
            JsonObject dbResponse = (JsonObject) chatItemReply.result().body();
            if (SUCCESS.equals(dbResponse.getString(STATUS))
                    || ("ChatItems not found".equals(dbResponse.getString("error-message")))) {
                List<JsonObject> chatItems = dbResponse.getJsonArray("message").getList();
                handler.handle(Future.succeededFuture(chatItems));
            } else handler.handle(Future.failedFuture(dbResponse.getString("message")));
        });
    }

    public void selectUserByEmailForConnect(String userEmail, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", selectUserByEmailForConnect)
                .put("userEmail", userEmail);

        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, reply -> {
            JsonObject dbResponse = (JsonObject) reply.result().body();
            if (SUCCESS.equals(dbResponse.getString(STATUS))) {
                System.out.println("    result selectUserByEmailForConnect dbResponse : " + dbResponse);
                handler.handle(Future.succeededFuture(dbResponse.getJsonObject("message")));
            }
            else {
                System.out.println("\n\n\n\n");
                System.out.println("\n\n\n\n");
                System.out.println("    ====================================================");
                System.out.println("    !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                System.out.println("    result selectUserByEmailForConnect dbResponse : " + dbResponse);
                System.out.println("            로그인 실패 !!!!!!!!!!!!!!");
                System.out.println("            로그인 실패 !!!!!!!!!!!!!!");
                System.out.println("            로그인 실패 !!!!!!!!!!!!!!");
                System.out.println("    !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                System.out.println("    ====================================================");
                System.out.println("\n\n\n\n");
                System.out.println("\n\n\n\n");
                handler.handle(Future.failedFuture(dbResponse.getString("message")));
            }
        });
    }

    public void updateChatRoomActiveStatus(Integer roomId, Integer userId, Boolean isWatching, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", updateChatRoomActiveStatusWHERERoomIdAndUserId)
                .put("roomId", roomId)
                .put("userId", userId)
                .put("isWatching", isWatching ? 1 : 0);  // Convert boolean to integer

        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, reply -> {
            JsonObject dbResponse = (JsonObject) reply.result().body();
            if (SUCCESS.equals(dbResponse.getString(STATUS))) handler.handle(Future.succeededFuture(dbResponse));
            else handler.handle(Future.failedFuture(dbResponse.getString("message")));
        });
    }

    public void updateChatRoomActiveStatus(Integer userId, int isWatching, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject payloadNew = new JsonObject()
                .put("action", updateChatRoomActiveStatusWHEREUserId)
                .put("userId", userId)
                .put("isWatching", isWatching);

        vertx.eventBus().<JsonObject>request(ADDRESS_DB_ACTION, payloadNew, reply -> {
            if (reply.succeeded()) handler.handle(Future.succeededFuture((JsonObject) reply.result().body()));
            else handler.handle(Future.failedFuture(reply.cause()));
        });
    }

    public void deleteChatRoomActiveStatus(Integer roomId, Integer userId, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", deleteChatRoomActiveStatus)
                .put("roomId", roomId)
                .put("userId", userId);

        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, reply -> {
            JsonObject dbResponse = (JsonObject) reply.result().body();
            if (SUCCESS.equals(dbResponse.getString(STATUS))) handler.handle(Future.succeededFuture(dbResponse));
            else handler.handle(Future.failedFuture(dbResponse.getString("message")));
        });
    }

    public void deleteChatRoomParticipant(Integer roomId, Integer userId, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject payload = new JsonObject()
                .put("action", deleteChatRoomParticipant)
                .put("roomId", roomId)
                .put("userId", userId);

        vertx.eventBus().request(ADDRESS_DB_ACTION, payload, reply -> {
            JsonObject dbResponse = (JsonObject) reply.result().body();
            if (SUCCESS.equals(dbResponse.getString(STATUS))) handler.handle(Future.succeededFuture(dbResponse));
            else handler.handle(Future.failedFuture(dbResponse.getString("message")));
        });
    }

    public Set<ChatRoomActiveStatus> _convertJsonToActiveStatusSet(List<JsonObject> activeStatuses) {
        Set<ChatRoomActiveStatus> resultSet = new HashSet<>();
        for (JsonObject status : activeStatuses) {
            ChatRoomActiveStatus activeStatus = new ChatRoomActiveStatus();
            activeStatus.setRoomId(status.getInteger("room_id"));
            activeStatus.setUserId(status.getInteger("user_id"));
            activeStatus.setIsWatching(status.getInteger("is_watching"));
            activeStatus.setLastWatchingAt(status.getString("last_watching_at"));
            resultSet.add(activeStatus);
        }
        return resultSet;
    }

    public static LocalDateTime parseTimestamp(String timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
        return LocalDateTime.parse(timestamp, formatter);
    }

    public ServerWebSocket getClientSocketByAddress(String socketAddress, Set<ServerWebSocket> clients) {
//        logger.info("Looking for socket with address: {}", socketAddress);
        for (ServerWebSocket socket : clients) {
            if (socket.remoteAddress().toString().equals(socketAddress)) {
//                logger.info("Found a match for socket address: {}", socketAddress);
                return socket;
            }
        }
//        logger.warn("No match found for socket address: {}", socketAddress);
        return null;
    }

    public String extractThreadNumber(String threadName) {
//        String threadName = Thread.currentThread().getName();
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(threadName);
        if (matcher.find()) {
            return matcher.group();
        }
        return "Unknown";
    }

    public void commonErrorHandler(String methodName, Throwable cause) {
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