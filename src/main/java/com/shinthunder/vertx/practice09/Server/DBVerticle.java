package com.shinthunder.vertx.practice09.Server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.shinthunder.vertx.practice09.Server.MainServer.*;

/**
 * 주의사항
 * ChatRoom에 게시물에 연관된 채팅방 개수 카운트를 업데이트하는 `Trigger` 설정이 되어 있다.
 * 따라서 ChatRoom에 새로운 게시물이 추가될 때마다 쿼리문 실행 없이도 talent_req테이블의 linking_cnt가 1씩 증가한다.
 * -- DELIMITER 설정: 이 구문은 MySQL의 명령 구분자를 변경합니다.
 * -- 기본적으로 MySQL은 세미콜론(;)을 명령어의 끝으로 인식합니다.
 * -- 트리거 내부에서 세미콜론을 사용해야 하므로, 명령어의 구분자를 임시로 변경합니다.
 * DELIMITER //
 * <p>
 * -- 트리거 생성: AFTER INSERT ON ChatRoom은 ChatRoom 테이블에 새로운 레코드가
 * -- 추가되면 이 트리거가 실행될 것임을 의미합니다.
 * CREATE TRIGGER increase_talent_req_linking_cnt
 * AFTER INSERT ON ChatRoom
 * FOR EACH ROW
 * BEGIN
 * -- IF NEW.related_type = 'talent_req': 새로 추가된 레코드(NEW)의 related_type이 'talent_req'인지 확인합니다.
 * IF NEW.related_type = 'talent_req' THEN
 * -- 해당 조건이 참이면 talent_req 테이블의 linking_cnt 열을 1 증가시킵니다.
 * -- 여기서 NEW.related_id는 새로 추가된 ChatRoom 레코드의 related_id입니다.
 * UPDATE talent_req
 * SET linking_cnt = linking_cnt + 1
 * WHERE req_id = NEW.related_id;
 * END IF;
 * END;
 * <p>
 * -- 명령어 구분자를 원래대로 세미콜론(;)으로 복원합니다.
 * //
 * DELIMITER ;
 */
public class DBVerticle extends AbstractVerticle {
    // -------------------------- CONSTANTS --------------------------
    private static final Logger logger = LoggerFactory.getLogger(DBVerticle.class);

    // -------------------------- MEMBER VARIABLES --------------------------
    private Map<String, Handler<Message<JsonObject>>> actionHandlers;
    private JDBCClient jdbcClient1, jdbcClient2;

    @Override
    public void start() {
        setupDB();
        setupActionHandlers();
        setupDBHandler();
    }

    private void setupDB() {
        // linktown, linktownChat 통합 이후, 코드
        JsonObject mysqlConfig1 = new JsonObject()
                .put("driver_class", "com.mysql.cj.jdbc.Driver")//
                .put("max_pool_size", 30)//
                .put("user", JDBC_USER)//
                .put("url", JDBC_URL) //
                .put("password", JDBC_PASSWORD);
        jdbcClient1 = JDBCClient.createShared(vertx, mysqlConfig1);
        checkDatabaseConnection_jdbcClient1();
        selectChatRoomsCountForTalentReq();

        // linktown, linktownChat 통합 이전, 코드
        //        JsonObject mysqlConfig1 = new JsonObject()
//                .put("url", "jdbc:mysql://localhost:3306/linktownChat") // hawaii
////                .put("url", "jdbc:mariadb://localhost:3306/linktownChat") // teamnova0
//                .put("driver_class", "com.mysql.cj.jdbc.Driver")//
////                .put("user", "root").put("password", "teamnova0")
//                .put("user", "root").put("password", "root")//
//                .put("max_pool_size", 30);
//        jdbcClient1 = JDBCClient.createShared(vertx, mysqlConfig1);
//        checkDatabaseConnection_jdbcClient1();

//        JsonObject mysqlConfig2 = new JsonObject()
//                .put("url", "jdbc:mysql://localhost:3306/linktown") // hawaii
////                .put("url", "jdbc:mariadb://localhost:3306/linktown") // teamnova0
//                .put("driver_class", "com.mysql.cj.jdbc.Driver")//
//                .put("user", "root")//
//                .put("password", "root")//
//                .put("max_pool_size", 30);
//        jdbcClient2 = JDBCClient.createShared(vertx, mysqlConfig2);
//        checkDatabaseConnection_jdbcClient2();
    }

    private void setupActionHandlers() {
        actionHandlers = new HashMap<>();
        actionHandlers.put(deleteChatRoomParticipant, this::deleteChatRoomParticipant);
        actionHandlers.put(deleteChatRoomActiveStatus, this::deleteChatRoomActiveStatus);
        actionHandlers.put(insertChatItem, this::insertChatItem);
        actionHandlers.put(insertChatItemAndSelectTimestamp, this::insertChatItemAndSelectTimestamp);
        actionHandlers.put(selectUserById, this::selectUserById);
        actionHandlers.put(selectUserByEmail, this::selectUserByEmail);
        actionHandlers.put(selectUserByEmailForConnect, this::selectUserByEmailForConnect);
        actionHandlers.put(selectChatRoomWhereId, this::selectChatRoomWhereId);
        actionHandlers.put(selectChatRoomWhereName, this::selectChatRoomWhereName);
//        actionHandlers.put(selectChatRoomsCountForTalentReq, this::selectChatRoomsCountForTalentReq);
        actionHandlers.put(selectChatItemWhereRoomId, this::selectChatItemWhereRoomId);
        actionHandlers.put(selectChatRoomParticipantWhereUserId, this::selectChatRoomParticipantWhereUserId);
        actionHandlers.put(selectChatRoomParticipantWhereRoomId, this::selectChatRoomParticipantWhereRoomId);
        actionHandlers.put(selectAllEmailsWHERERoomIdWithJoin, this::selectAllEmailsWHERERoomIdWithJoin);
        actionHandlers.put(selectChatItemWhereId, this::selectChatItemWhereId);
        actionHandlers.put(insertUser, this::insertUser);
        actionHandlers.put(insertChatRoom, this::insertChatRoom);
        actionHandlers.put(insertChatRoomParticipant, this::insertChatRoomParticipant);
        /** --------------------------------------------------*/
        actionHandlers.put(selectChatRoomActiveStatusByRoomId, this::selectChatRoomActiveStatusByRoomId);
        actionHandlers.put(selectChatRoomActiveStatusByUserId, this::selectChatRoomActiveStatusByUserId);
//        actionHandlers.put(selectMessageReadStatusByMessageId, this::selectMessageReadStatusByMessageId);
//        actionHandlers.put(selectMessageReadStatusByUserId, this::selectMessageReadStatusByUserId);
        actionHandlers.put(insertChatRoomActiveStatus, this::insertChatRoomActiveStatus);
        actionHandlers.put(insertMessageReadStatus, this::insertMessageReadStatus);
        actionHandlers.put(updateChatRoomActiveStatusWHEREUserId, this::updateChatRoomActiveStatusWHEREUserId);
        actionHandlers.put(updateChatRoomActiveStatusWHERERoomIdAndUserId, this::updateChatRoomActiveStatusWHERERoomIdAndUserId);
        actionHandlers.put(updateMessageReadStatus, this::updateMessageReadStatus);

    }

    // 채팅방 개수를 차감하는 메소드. 이미 차감했다면 차감하지 않습니다.
    private void decrementChatRoomCountIfNotAlready(Integer roomId) {
        String query = "UPDATE talent_req tr " +
                "JOIN ChatRoom cr ON tr.req_id = cr.related_id " +
                "SET tr.linking_cnt = tr.linking_cnt - 1, cr.decremented = 1 " +
                "WHERE cr.id = ? AND cr.decremented = 0";
        JsonArray params = new JsonArray().add(roomId);

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                logger.info("채팅방 개수 차감 성공");
            } else {
                logger.error("채팅방 개수 차감 실패 : {}", res.cause().getMessage());
            }
        });
    }

    private void deleteChatRoomParticipant(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        Integer userId = message.body().getInteger("userId");
        String query = "DELETE FROM ChatRoomParticipant WHERE room_id = ? AND user_id = ?";
        JsonArray params = new JsonArray().add(roomId).add(userId);

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                // 참가자 삭제 성공시 decrementChatRoomCountIfNotAlready 메소드 호출
                decrementChatRoomCountIfNotAlready(roomId);
                message.reply(new JsonObject().put(STATUS, SUCCESS));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void deleteChatRoomActiveStatus(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        Integer userId = message.body().getInteger("userId");

        String query = "DELETE FROM ChatRoomActiveStatus WHERE room_id = ? AND user_id = ?";
        JsonArray params = new JsonArray().add(roomId).add(userId);

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put(STATUS, SUCCESS));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoomActiveStatusByRoomId(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        String query = "SELECT * FROM ChatRoomActiveStatus WHERE room_id = ?";
        JsonArray params = new JsonArray().add(roomId);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    // Iterate over each JsonObject in the results and convert the timestamp
                    for (JsonObject jsonObject : results) {
                        // Convert LocalDateTime to string
                        if (jsonObject.containsKey("last_watching_at")) {
                            LocalDateTime lastWatchingAt = LocalDateTime.parse(jsonObject.getString("last_watching_at"));
                            jsonObject.put("last_watching_at", lastWatchingAt.toString());
                        }
                    }
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", new JsonArray(results)));
                } else {
                    logger.error("이 방 번호({})에 할당된 ChatRoomActiveStatus가 없음", roomId);
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "이 방 번호에 할당된 ChatRoomActiveStatus가 없음"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoomActiveStatusByUserId(Message<JsonObject> message) {
        Integer userId = message.body().getInteger("userId");
        String query = "SELECT * FROM ChatRoomActiveStatus WHERE user_id = ?";
        JsonArray params = new JsonArray().add(userId);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    for (JsonObject jsonObject : results) {
                        // Convert LocalDateTime to string
                        if (jsonObject.containsKey("last_watching_at")) {
                            LocalDateTime lastWatchingAt = LocalDateTime.parse(jsonObject.getString("last_watching_at"));
                            jsonObject.put("last_watching_at", lastWatchingAt.toString());
                        }
                    }
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", new JsonArray(results)));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Active status not found for user"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoomActiveStatusByRoomIdAndUserId(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        Integer userId = message.body().getInteger("userId");
        String query = "SELECT * FROM ChatRoomActiveStatus WHERE room_id = ? AND user_id = ?";
        JsonArray params = new JsonArray().add(roomId).add(userId);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", results.get(0)));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "ChatRoomActiveStatus not found"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertChatRoomActiveStatus(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        Integer userId = message.body().getInteger("userId");
        Integer isWatching = message.body().getInteger("isWatching");

        String query = "INSERT INTO ChatRoomActiveStatus (room_id, user_id, is_watching) VALUES (?, ?, ?)";
        JsonArray params = new JsonArray().add(roomId).add(userId).add(isWatching);

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put(STATUS, SUCCESS));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertMessageReadStatus(Message<JsonObject> message) {
        Integer messageId = message.body().getInteger("messageId");
        Integer userId = message.body().getInteger("userId");

        String query = "INSERT INTO MessageReadStatus (message_id, user_id) VALUES (?, ?)";
        JsonArray params = new JsonArray().add(messageId).add(userId);

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put(STATUS, SUCCESS));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void updateChatRoomActiveStatusWHERERoomIdAndUserId(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        Integer userId = message.body().getInteger("userId");
        Integer isWatching = message.body().getInteger("isWatching");

        String query = "UPDATE ChatRoomActiveStatus SET is_watching = ?, last_watching_at = CURRENT_TIMESTAMP(6) WHERE room_id = ? AND user_id = ?";
        JsonArray params = new JsonArray().add(isWatching).add(roomId).add(userId);

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put(STATUS, SUCCESS));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void updateChatRoomActiveStatusWHEREUserId(Message<JsonObject> message) {
        Integer userId = message.body().getInteger("userId");
        Integer isWatching = message.body().getInteger("isWatching");

        String query = "UPDATE ChatRoomActiveStatus SET is_watching = ?, last_watching_at = CURRENT_TIMESTAMP(6) WHERE user_id = ?";
        JsonArray params = new JsonArray().add(isWatching).add(userId);

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put(STATUS, SUCCESS));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void updateMessageReadStatus(Message<JsonObject> message) {
        Integer messageId = message.body().getInteger("messageId");
        Integer userId = message.body().getInteger("userId");
        String readAt = message.body().getString("readAt"); // assuming it's passed as a string in the format "YYYY-MM-DD HH:MM:SS.SSSSSS"

        String query = "UPDATE MessageReadStatus SET read_at = ? WHERE message_id = ? AND user_id = ?";
        JsonArray params = new JsonArray().add(readAt).add(messageId).add(userId);

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put(STATUS, SUCCESS));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    // -------------------------- EVENTBUS HANDLER (EventBus <-> DBVerticle) --------------------------
    private void setupDBHandler() {
        vertx.eventBus().consumer(ADDRESS_DB_ACTION, this::handleDatabaseActions);
    }

    private void handleDatabaseActions(Message<JsonObject> message) {
        String action = message.body().getString("action");
        Handler<Message<JsonObject>> handler = actionHandlers.get(action);
        if (handler != null) {
            handler.handle(message);
        } else {
            message.reply(new JsonObject().put(ERROR, "Unknown action"));
        }
    }

    private void selectUserById(Message<JsonObject> message) {
        Integer userId = message.body().getInteger("userId");

        String query = "SELECT * FROM user WHERE user_id = ?";
        JsonArray params = new JsonArray().add(userId);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
//                System.out.println("results : "+results);
                if (!results.isEmpty()) {
                    // message.reply(new JsonObject()
                    //     .put(STATUS, SUCCESS)
                    //     .put("message", results.get(0))
                    // );

                    JsonObject result = results.get(0);
                    if (result.containsKey("user_created_at")) {
                        LocalDateTime lastLoginAt = LocalDateTime.parse(result.getString("user_created_at"));
                        result.put("user_created_at", lastLoginAt.toString());
                    }
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", result));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "User not found"));
                }
            } else {
//                System.out.println("res.cause().getMessage() : "+res.cause().getMessage());
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectUserByEmail(Message<JsonObject> message) {
        String userEmail = message.body().getString("userEmail");

        String query = "SELECT * FROM user WHERE user_email = ?";
        JsonArray params = new JsonArray().add(userEmail);
//        System.out.println("String query : "+String query);
//        System.out.println("params : "+params);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
//                System.out.println("results : "+results);
                if (!results.isEmpty()) {
                    for (JsonObject jsonObject : results) {
                        // Convert LocalDateTime to string
                        if (jsonObject.containsKey("user_created_at")) {
                            LocalDateTime lastWatchingAt = LocalDateTime.parse(jsonObject.getString("user_created_at"));
                            jsonObject.put("user_created_at", lastWatchingAt.toString());
                        }
                    }
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", results.get(0)));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "User not found"));
                }
            } else {
//                System.out.println("res.cause().getMessage() : "+res.cause().getMessage());
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoomWhereId(Message<JsonObject> message) {
        String roomId = message.body().getString("roomId");
        String query = "SELECT * FROM ChatRoom WHERE id = ?";
        JsonArray params = new JsonArray().add(roomId);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();

                System.out.println("results : " + results);
//                if (results.get(0).g)
                if (!results.isEmpty()) {
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", results.get(0)));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "ChatRoom not found"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoomWhereName(Message<JsonObject> message) {
        String roomName = message.body().getString("roomName");
        String query = "SELECT * FROM ChatRoom WHERE name = ?";
        JsonArray params = new JsonArray().add(roomName);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", results.get(0)));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "ChatRoom not found"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoomsCountForTalentReq() {
//    private void selectChatRoomsCountForTalentReq(Message<JsonObject> message) {
//        int talentReqId = message.body().getInteger("talentReqId");
        int talentReqId = 25;

        String query = "SELECT COUNT(*) AS room_count FROM ChatRoom WHERE related_type = 'talent_req' AND related_id = ?";
        JsonArray params = new JsonArray().add(talentReqId);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    int roomCount = results.get(0).getInteger("room_count");

                    System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    System.out.println("    TEST : ");
                    System.out.println("    roomCount : " + roomCount);
                    System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("roomCount", roomCount));
                } else {
//                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "No rooms found for the talent_req"));
                }
            } else {
//                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoomParticipantWhereUserId(Message<JsonObject> message) {
        Integer userId = message.body().getInteger("userId");
        String query = "SELECT * FROM ChatRoomParticipant WHERE user_id = ?";
        JsonArray params = new JsonArray().add(userId);
        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                System.out.println("    results 0 : " + results);
                if (!results.isEmpty()) {
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", new JsonArray(results)));
                } else {
//                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Participant not found"));
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Participant not found"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatRoomParticipantWhereRoomId(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        String query = "SELECT * FROM ChatRoomParticipant WHERE room_id = ?";
        JsonArray params = new JsonArray().add(roomId);
        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
//                System.out.println("    !!! results 1 : " + results);
                if (!results.isEmpty()) {
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", new JsonArray(results)));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Participant not found"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectAllEmailsWHERERoomIdWithJoin(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        String query = "SELECT u.user_email " +// 사용자의 이메일 주소를 선택
                "FROM ChatRoomParticipant crp " +  // ChatRoomParticipant 테이블에서
                "JOIN user u ON crp.user_id = u.user_id " +  // ChatRoomParticipant의 user_id와 user 테이블의 user_id가 일치하는 레코드를 연결
                "WHERE crp.room_id = ?";           // 특정한 채팅방 ID에 해당하는 데이터만
        JsonArray params = new JsonArray().add(roomId);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<String> emails = res.result().getRows().stream().map(row -> row.getString("user_email")).collect(Collectors.toList());

                if (!emails.isEmpty()) {
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", new JsonArray(emails)));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Participant not found"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatItemWhereRoomId(Message<JsonObject> message) {
        String roomId = message.body().getString("roomId");
        String query = "SELECT * FROM ChatItem WHERE room_id = ?";
        JsonArray params = new JsonArray().add(roomId);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
//                System.out.println("==================================================");
//                System.out.println("    !! selectChatItemWhereRoomId !!");

                if (!results.isEmpty()) {
                    // Convert the LocalDateTime to a String
                    for (JsonObject result : results) {
                        if (result.containsKey("timestamp")) {
                            String timestampStr = result.getString("timestamp");
                            result.put("timestamp", timestampStr);
                        }
                    }
//                    System.out.println("    timestamp : " + results.get(0).getString("timestamp"));
//                    System.out.println("    -> results : " + results);
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", new JsonArray(results)));
                } else {
                    System.out.println("==================================================");
//                    System.out.println("    !! selectChatItemWhereRoomId !! : error 1: "+);
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.put("id", -1);
                    jsonObject.put("sender_id", -1);
                    jsonObject.put("room_id", -1);
                    jsonObject.put("message", "");
                    jsonObject.put("action", "");
                    jsonObject.put("timestamp", "");
                    results.add(jsonObject);
//                    System.out.println("    -> results : " + results);
                    message.reply(new JsonObject().put(STATUS, ERROR)
                            .put("message", new JsonArray(results))
                            .put("error-message", "ChatItems not found"));
                }
            } else {
                System.out.println("==================================================");
                System.out.println("    !! selectChatItemWhereRoomId !! : error 2: " + res.cause().getMessage());
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectChatItemWhereId(Message<JsonObject> message) {
        Integer id = message.body().getInteger("id");
        String query = "SELECT * FROM ChatItem WHERE id = ?";
        JsonArray params = new JsonArray().add(id);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    JsonObject jsonObject = results.get(0);

                    // LocalDateTime을 문자열로 변환
                    if (jsonObject.containsKey("timestamp")) {
                        LocalDateTime timestamp = LocalDateTime.parse(jsonObject.getString("timestamp"));
                        jsonObject.put("timestamp", timestamp.toString());
                    }

//                    System.out.println("results : " + jsonObject);
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", jsonObject));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "ChatItem not found"));
                }
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertUser(Message<JsonObject> message) {
        String userEmail = message.body().getString("userEmail");
        String query = "INSERT INTO user (user_email, user_password) VALUES (?, ?)";
        JsonArray params = new JsonArray().add(userEmail).add("some_default_password");

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                Integer generatedId = res.result().getKeys().getInteger(0);
                message.reply(new JsonObject().put(STATUS, SUCCESS).put("generatedId", generatedId));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertChatRoom(Message<JsonObject> message) {
        String roomName = message.body().getString("roomName");
        String relatedType = message.body().getString("relatedType");
        Integer relatedId = message.body().getInteger("relatedId");
        Integer talentId = message.body().getInteger("talentId");
        System.out.println("    roomName : " + roomName);
        System.out.println("    relatedType : " + relatedType);
        System.out.println("    relatedId : " + relatedId);
        System.out.println("    talentId : " + talentId);
        String query = "INSERT INTO ChatRoom (name, related_type, related_id,talent_id) VALUES (?,?,?,?)";
        JsonArray params = new JsonArray()
                .add(roomName)
                .add(relatedType)
                .add(relatedId)
                .add(talentId);

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                Integer generatedId = res.result().getKeys().getInteger(0);
                System.out.println("    DBVerticle insertChatRoom generatedId : " + generatedId);
                message.reply(new JsonObject().put(STATUS, SUCCESS).put("generatedId", generatedId));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertChatRoomParticipant(Message<JsonObject> message) {
        Integer roomId = message.body().getInteger("roomId");
        Integer userId = message.body().getInteger("userId");
        String query = "INSERT INTO ChatRoomParticipant (room_id, user_id) VALUES (?, ?)";
        JsonArray params = new JsonArray().add(roomId).add(userId);
        System.out.println("insertChatRoomParticipant : roomId : " + roomId);
        System.out.println("insertChatRoomParticipant : userId : " + userId);
        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", res.succeeded()));
            } else {
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertChatItem(Message<JsonObject> message) {
        logger.info("=================== insertChatItem() ===============================");
        JsonObject chatItem = message.body().getJsonObject("chatItem");
        String query = "INSERT INTO ChatItem (id,room_id, sender_id, message) VALUES (?, ?, ?, ?);";
        JsonArray params = new JsonArray().add(chatItem.getInteger("id")).add(chatItem.getInteger("room_id")).add(chatItem.getInteger("sender_id")).add(chatItem.getString("message"));

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                int insertedId = res.result().getKeys().getInteger(0);
                message.reply(new JsonObject().put(STATUS, SUCCESS).put("insertedId", insertedId));
            } else {
                logger.error("Failed to insert chat item: " + res.cause());
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void insertChatItemAndSelectTimestamp(Message<JsonObject> message) {
        logger.info("=================== insertChatItem() ===============================");
        JsonObject chatItem = message.body().getJsonObject("chatItem");
        String query = "INSERT INTO ChatItem (id,room_id, sender_id, message,action) VALUES (?, ?, ?, ?, ?);";
        JsonArray params = new JsonArray()
                .add(chatItem.getInteger("id"))//
                .add(chatItem.getInteger("room_id"))//
                .add(chatItem.getInteger("sender_id"))//
                .add(chatItem.getString("message"))//
                .add(chatItem.getString("action"));

        jdbcClient1.updateWithParams(query, params, res -> {
            if (res.succeeded()) {
                int insertedId = res.result().getKeys().getInteger(0);

                // 별도의 SELECT 쿼리를 사용하여 timestamp를 가져옵니다.
                String selectQuery = "SELECT timestamp FROM ChatItem WHERE id = ?;";
                JsonArray selectParams = new JsonArray().add(insertedId);
                jdbcClient1.queryWithParams(selectQuery, selectParams, selectRes -> {
                    if (selectRes.succeeded()) {
                        String timestamp = selectRes.result().getRows().get(0).getString("timestamp");
                        message.reply(new JsonObject().put(STATUS, SUCCESS).put("insertedId", insertedId).put("timestamp", timestamp));
                    } else {
                        logger.error("Failed to get timestamp: " + selectRes.cause());
                        message.reply(new JsonObject().put(STATUS, ERROR).put("message", selectRes.cause().getMessage()));
                    }
                });

            } else {
                logger.error("Failed to insert chat item: " + res.cause());
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    private void selectUserByEmailForConnect(Message<JsonObject> message) {
        String userEmail = message.body().getString("userEmail");
        System.out.println("==================================================");
        System.out.println("            HERE??              ");
        String query = "SELECT * FROM user WHERE user_email = ?"; // 대소문자 주의 !!!! user와 User는 서로 다른 거로 인식됨
        JsonArray params = new JsonArray().add(userEmail);

        jdbcClient1.queryWithParams(query, params, res -> {
            if (res.succeeded()) {
                List<JsonObject> results = res.result().getRows();
                if (!results.isEmpty()) {
                    JsonObject userJson = results.get(0);
                    // Convert user_created_at from LocalDateTime to String
                    Object createdAt = userJson.getValue("user_created_at");
                    if (createdAt instanceof LocalDateTime) {
                        userJson.put("user_created_at", createdAt.toString());
                    }
                    System.out.println("            userJson :              " + userJson);
                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", userJson));
                } else {
                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "User not found"));
                }
            } else {
                System.out.println("            res.cause() :              " + res.cause());
                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
            }
        });
    }

    public void checkDatabaseConnection_jdbcClient1() {
        jdbcClient1.query("SELECT 1", res -> {
            if (res.succeeded()) {
                System.out.println("Database connected successfully!");
            } else {
                System.err.println("Failed to connect to database: " + res.cause());
            }
        });
    }


    //
//    /**
//     * `JDBCClient2`로 DB와 연결함!! connect()를 처리할 용도.
//     */
//    private void selectUserByEmailForConnect(Message<JsonObject> message) {
//        String userEmail = message.body().getString("userEmail");
//        System.out.println("==================================================");
//        System.out.println("            HERE??              ");
//        String query = "SELECT * FROM user WHERE user_email = ?"; // 대소문자 주의 !!!! user와 User는 서로 다른 거로 인식됨
//        JsonArray params = new JsonArray().add(userEmail);
//
//        jdbcClient2.queryWithParams(query, params, res -> {
//            if (res.succeeded()) {
//                List<JsonObject> results = res.result().getRows();
//                if (!results.isEmpty()) {
//                    JsonObject userJson = results.get(0);
//                    // Convert user_created_at from LocalDateTime to String
//                    Object createdAt = userJson.getValue("user_created_at");
//                    if (createdAt instanceof java.time.LocalDateTime) {
//                        userJson.put("user_created_at", createdAt.toString());
//                    }
//                    System.out.println("            userJson :              " + userJson);
//                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", userJson));
//                } else {
//                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "User not found"));
//                }
//            } else {
//                System.out.println("            res.cause() :              " + res.cause());
//                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
//            }
//        });
//    }
//
//    public void checkDatabaseConnection_jdbcClient2() {
//        jdbcClient2.query("SELECT 1", res -> {
//            if (res.succeeded()) {
//                System.out.println("Database connected successfully!");
//            } else {
//                System.err.println("Failed to connect to database: " + res.cause());
//            }
//        });
//    }


//    private void selectMessageReadStatusByMessageId(Message<JsonObject> message) {
//        Integer messageId = message.body().getInteger("messageId");
//        String query = "SELECT * FROM MessageReadStatus WHERE message_id = ?";
//        JsonArray params = new JsonArray().add(messageId);
//
//        jdbcClient1. queryWithParams( query, params, res -> {
//            if (res.succeeded()) {
//                List<JsonObject> results = res.result().getRows();
//                if (!results.isEmpty()) {
//                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", new JsonArray(results)));
//                } else {
//                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Read status not found for message"));
//                }
//            } else {
//                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
//            }
//        });
//    }
//
//    private void selectMessageReadStatusByUserId(Message<JsonObject> message) {
//        Integer userId = message.body().getInteger("userId");
//        String query = "SELECT * FROM MessageReadStatus WHERE user_id = ?";
//        JsonArray params = new JsonArray().add(userId);
//
//        jdbcClient1. queryWithParams( query, params, res -> {
//            if (res.succeeded()) {
//                List<JsonObject> results = res.result().getRows();
//                if (!results.isEmpty()) {
//                    message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", new JsonArray(results)));
//                } else {
//                    message.reply(new JsonObject().put(STATUS, ERROR).put("message", "Read status not found for user"));
//                }
//            } else {
//                message.reply(new JsonObject().put(STATUS, ERROR).put("message", res.cause().getMessage()));
//            }
//        });
//    }
}

