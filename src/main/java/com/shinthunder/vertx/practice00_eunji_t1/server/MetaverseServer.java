package com.shinthunder.vertx.practice00_eunji_t1.server;

import com.hazelcast.config.Config;
import com.shinthunder.vertx.practice00_eunji_t1.object.ChatItem;
import com.shinthunder.vertx.practice00_eunji_t1.object.ClientAction;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MetaverseServer extends AbstractVerticle {
    // -------------------------- CONSTANTS --------------------------
    private static final Logger logger = LoggerFactory.getLogger(MetaverseServer.class);
//    private static final int WEBSOCKET_PORT = 8080;
    private static final int WEBSOCKET_PORT = 60002;

    private static final int NUM_OF_INSTANCES = 1; // 버티클 개수
    private static final String BROADCAST_MESSAGE_ADDRESS = "broadcast.message.address";

    private static final int UP = 0;
    private static final int DOWN = 1;
    private static final int RIGHT = 2;
    private static final int LEFT = 3;

    // -------------------------- MEMBER VARIABLES --------------------------
    //  중복 메시지 처리를 방지하는데 사용 -> 이미 처리된 메시지의 ID 저장
    // 중복된 값을 허용하지 않은 특징이 있어, 각 메시지의 고유 Id 저장하는데 사용
    // Collections.synchronizedSet() 여러 스레드에서 동시에 접근할 때도 데이터의 일관성이 유지됨
    private final Set<String> processedMessageIds = Collections.synchronizedSet(new HashSet<>());

    //현재 연결되어 있는 웹소켓 클라이언트들의 집합
    // 새로운 클라이언트가 연결 될 때마다 이 집합에 추가, 연결을 끊을 때 제거됨
    private final Set<ServerWebSocket> clients = new HashSet<>();

    // 비동기 맵 인터페이스 Key : 사용자 id , Value : 소켓 주소
    // 서로 다른 Verticle이나 노드에서도 동일한 데이터에 접근이 가능
    private AsyncMap<Integer, String> userToSocketMap;  // <userId, socketAddress>
    private AsyncMap<Integer, JsonObject> userLocationMap; // <userId, locationData>
    private AsyncMap<Integer, Set<Integer>> roomToUsersMap;  // roonNum to set of userIDs
    private AsyncMap<Integer, JsonObject> userMediaMap;  // TODO : 신지 추가 <userId, MediaData>


    // -------------------------- WEBSOCKET HANDLER METHODS --------------------------
    private void handleClientAction(ServerWebSocket socket, ClientAction clientAction) {
        logger.info("!!!!!!!!!!!!!!!!!!!!!!!! handleClientAction!!!!!!!!!!!!!!!!!!!!");
//        Buffer buffer = Json.encodeToBuffer(clientAction);

         int x =clientAction.getX();;
         int y= clientAction.getY();
         int userId = clientAction.getUserId();;
         String nickName = clientAction.getNickName();
         int direction= clientAction.getDirection();
         int roomNumber= clientAction.getRoomNumber();
         int changeRoomNumber = clientAction.getChangeRoomNumber();
         String texture = clientAction.getTexture();

        logger.info(String.format("x: %d, y: %d, userId: %d, nickName: %s, direction: %d, roomNumber: %d, changeRoomNumber: %d, texture: %s",
                x, y, userId, nickName, direction, roomNumber, changeRoomNumber, texture));

        try{
            switch (clientAction.getAction()) {


                case "MOVE":
                    try {
                        JsonObject moveData = new JsonObject();
                        moveData.put("action","MOVE");
                        moveData.put("direction",direction);
                        moveData.put("userId",userId);
                        moveData.put("x",x);
                        moveData.put("y",y);
                        moveData.put("roomNumber",roomNumber);
                        moveData.put("texture",texture);

                        sendMessageToRoomUsers(roomNumber, moveData);
                        changeUserLocationMapData(userId,x,y,direction);

                    } catch (Exception e) {
                        logger.error("Error handling  MOVE", e);
                    }
                    break;

                case "ENTER_METAVERSE":
                    try{
                        logger.info(" name : {}, ENTER_METAVERSE !! ", userId);
                        // 맵 데이터에 입장한 유저 정보 저장해줌
                        userToSocketMap.put(userId, socket.remoteAddress().toString());
                        addNewUserToRoomToUserMap(roomNumber,userId);

                        JsonObject roomData = new JsonObject();
                        roomData.put("action","ENTER_METAVERSE_SUCCESS");
                        roomData.put("userId",userId);
                        roomData.put("nickName",nickName);
                        roomData.put("texture",texture);
                        roomData.put(" roomNumber",roomNumber);
                        socket.writeTextMessage(roomData.toString());
                    } catch (Exception e) {
                    logger.error("Error handling  ENTER_METAVERSE", e);}
                    break;

                case "EXISTING_USER_INFO":
                    try{
                        logger.info(" name : {}, EXISTING_USER_INFO !! ", userId);

                        JsonObject data = new JsonObject();
                        data.put("action","ADD_NEW_USER_EVENT");
                        data.put("userId",userId);
                        data.put("nickName",nickName);
                        data.put("texture",texture);
                        logger.info("texture : {}  ", texture);
                        // roomNumber에 해당하는 유저들에게 data 보냄
                        sendMessageToRoomUsers(roomNumber, data);
                        // 해당 방의 유저 리스트 보내줌
                        sendUserListToNewUser(socket,clientAction);
                        //  위치 관련된 값 초기화 해줌
                        initLocationData(x,y,userId,nickName,texture,socket);

                    }catch (Exception e) {
                        logger.error("Error handling  EXISTING_USER_INFO", e);}
                    break;

                case "TRY_ROOM_CHANGE":

                    try{
                        logger.info(" name : {}, TRY_ROOM_CHANGE! !! ", clientAction.getUserId());

                        //방 이동을 위해 관련된 기존 방에 관련된 데이터 제거해줌
                        removeDataToChangeRoom(roomNumber,userId);

                        //기존 방 유저에게 해당 유저가 나갔다고 알려줌
                        sendMessageRemoveUser(roomNumber,userId);

                        // 이동한 방번호와 유저 정보를 연결시켜 저장해줌
                        addNewUserToRoomToUserMap(changeRoomNumber,userId);

                        // 해당 유저에게 방 이동이 성공했다고 알려줌
                        JsonObject ChangeRoomData = new JsonObject();
                        ChangeRoomData.put("action","TRY_ROOM_CHANGE_SUCCESS");
                        ChangeRoomData.put("userId",userId);
                        ChangeRoomData.put("changeRoomNumber",changeRoomNumber);
                        ChangeRoomData.put("roomNumber",roomNumber);
                        logger.info("TRY_ROOM_CHANGE_SUCCESS ", ChangeRoomData);
                        socket.writeTextMessage(ChangeRoomData.toString());

                    }
                    catch (Exception e){
                        logger.error("Error handling  TRY_ROOM_CHANGE", e);}
                    break;

                case "EXIT_METAVERSE":

                    try{
                        logger.info(" name : {}, EXIT_METAVERSE! !! ", userId);

                        //메타버스 나갔을 때 유저 관련된 정보 모두 제거해줌
                        removeExitUserInfo(socket,userId,roomNumber);
                        //기존 방 유저에게 해당 유저가 나갔다고 알려줌
                        sendMessageRemoveUser(roomNumber,userId);

                    }
                    catch (Exception e){
                        logger.error("Error handling  REMOVE", e);
                    }

                    break;

                // TODO :  신지 추가
                case "ADD_WEBRTC_STATUS":

                    try{
                        logger.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        logger.info("       ADD_WEBRTC_STATUS : ");
                        logger.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//                logger.info("ADD_WEBRTC_STATUS : getAction : {}", clientAction.getAction());
//                logger.info("ADD_WEBRTC_STATUS : getUserId : {}", clientAction.getUserId());
//                logger.info("ADD_WEBRTC_STATUS : getNickName : {}", clientAction.getNickName());
//                logger.info("ADD_WEBRTC_STATUS : getMediaType : {}", clientAction.getMediaType());
//                logger.info("ADD_WEBRTC_STATUS : getStreamId : {}", clientAction.getStreamId());
                        addMediaData(clientAction.getUserId(), clientAction.getNickName(),
                                clientAction.getRoomNumber(), clientAction.getScreenShareStreamId(), clientAction.getVideoStreamId());

                    }catch (Exception e){
                        logger.error("Error handling  ADD_WEBRTC_STATUS", e);
                    }

                    break;

                // TODO :  신지 추가
                case "UPDATE_WEBRTC_LEAVE":
                    try{
                        logger.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        logger.info("       UPDATE_WEBRTC_LEAVE : ");
                        logger.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        removeMediaData(clientAction.getUserId(), clientAction.getNickName(), clientAction.getRoomNumber(),
                                clientAction.getScreenShareStreamId(), clientAction.getVideoStreamId());
                    }catch (Exception e){
                        logger.error("Error handling  UPDATE_WEBRTC_LEAVE", e);
                    }

                    break;

                default:
                    logger.warn("Unknown action: {}", clientAction);
            }
        }
        catch(Exception e){
            logger.error("Unexpected error occurred while processing client action", e);
        }

    }

    private void sendMessageRemoveUser(int roomNumber,int userId) {
        logger.info("sendMessageRemoveUser ");

        JsonObject ExitUserdata = new JsonObject();
        ExitUserdata.put("action","REMOVE");
        ExitUserdata.put("userId",userId);
        logger.info("ExitUserdata ", ExitUserdata);
        sendMessageToRoomUsers(roomNumber, ExitUserdata);
    }

    private void removeDataToChangeRoom(int roomNumber, int userId) {
        logger.info("removeDataToChangeRoom ");

        removeUserFromRoomToUserMap(roomNumber,userId);
        removeUserLocationMap(userId);
    }

    private void broadcastMessageInRoom(ChatItem chatItem) {
        if (processedMessageIds.contains(chatItem.getMessageId())) {
            // 중복 메시지이므로 무시
            logger.warn("           Duplicated message received with ID: {}", chatItem.getMessageId());
            return;
        }
        logger.info("UserName : {}, messageId : {}, message : {}", chatItem.getSenderName(), chatItem.getMessageId(), chatItem.getMessage());
        // 메시지 ID를 처리된 ID 목록에 추가
        processedMessageIds.add(chatItem.getMessageId());
        Buffer buffer = Json.encodeToBuffer(chatItem);
        userToSocketMap.entries(res -> {
            if (res.succeeded()) {
                Map<Integer, String> map = res.result();
                for (Map.Entry<Integer, String> entry : map.entrySet()) {
                    String socketAddress = entry.getValue();
                    ServerWebSocket clientSocket = getClientSocketByAddress(socketAddress);
                    if (clientSocket != null) {
                        // 해당 소켓이 현재 Verticle 인스턴스에 있으므로 클라이언트에게 직접 메시지 전달
                        try {
                            logger.error("          해당 소켓이 현재 Verticle 인스턴스에 있기에 클라이언트에게 직접 메시지 전달 : {} -> 전체", chatItem.getSenderName());
                            clientSocket.writeTextMessage(buffer.toString());
                        } catch (Exception e) {
                            logger.error("Failed to send message to user {}: {}", entry.getKey(), e.getMessage());
                        }
                    } else {
                        // 해당 소켓이 현재 Verticle 인스턴스에 없을 경우 이벤트 버스를 통해 메시지 전달
                        logger.error("          해당 소켓이 현재 Verticle 인스턴스에 없을 경우 이벤트 버스를 통해 메시지 전달");
                        vertx.eventBus().publish(BROADCAST_MESSAGE_ADDRESS, buffer);
                    }
                }
            } else {
                logger.error("Error getting userToSocketMap entries: {}", res.cause().getMessage());
            }
        });
    }

    // -------------------------- UTILITY METHODS --------------------------
    private ServerWebSocket getClientSocketByAddress(String socketAddress) {
        logger.info("Looking for socket with address: {}", socketAddress);
        for (ServerWebSocket socket : clients) {
            if (socket.remoteAddress().toString().equals(socketAddress)) {
                logger.info("           Found a match for socket address: {}", socketAddress);
                return socket;
            }
        }
        logger.warn("           No match found for socket address: {}", socketAddress);
        return null;
    }

    // -------------------------- START METHODS --------------------------

    private void setupEventBusMessageHandler() {
        // 이벤트 버스 메시지 수신 핸들러 설정
        vertx.eventBus().consumer(BROADCAST_MESSAGE_ADDRESS, message -> {
            logger.info("               Received message via event bus");
            Buffer buffer = (Buffer) message.body();
            ChatItem chatItem = Json.decodeValue(buffer, ChatItem.class);
            // 직접 broadcastMessageInRoom 메서드를 호출
            broadcastMessageInRoom(chatItem);
        });
    }

    @Override
    public void start() {
        initializeSharedData();
        configureWebSocketServer();
        setupEventBusMessageHandler();
    }

    private void initializeSharedData() {
        vertx.sharedData().<Integer, String>getAsyncMap("userToSocketMap", res -> {
            if (res.succeeded()) userToSocketMap = res.result();
            else logger.error("Error initializing userToSocketMapAsync:", res.cause());
        });

        vertx.sharedData().<Integer, JsonObject>getAsyncMap("userLocationMap", res -> {
            if (res.succeeded()) userLocationMap = res.result();
            else logger.error("Error initializing userLocationMapAsync:", res.cause());
        });

        vertx.sharedData().<Integer, Set<Integer>>getAsyncMap("roomToUsersMap", res -> {
            if (res.succeeded()) roomToUsersMap = res.result();
            else logger.error("Error initializing userToRoomMap:", res.cause());
        });

        vertx.sharedData().<Integer, JsonObject>getAsyncMap("userMediaMap", res -> {
            if (res.succeeded()) userMediaMap = res.result();
            else logger.error("Error initializing userMediaMap:", res.cause());
        });


    }

    private void configureWebSocketServer() {
        vertx.createHttpServer().webSocketHandler(this::webSocketHandler).exceptionHandler(e -> logger.error("Error occurred with server: {}", e.getMessage())).listen(WEBSOCKET_PORT, res -> {
            if (res.succeeded()) {
                logger.info("Server is now listening on port {}", WEBSOCKET_PORT);
            } else {
                logger.error("Failed to bind on port PORT: {}", res.cause().getMessage());
            }
        });
    }


    public void webSocketHandler(ServerWebSocket socket) {
        logger.info("Client connected: {}", socket.remoteAddress());
        clients.add(socket);
//        logger.info("clients.size() : {}", clients.size());
        socket.handler(buffer -> {
            try {
                logger.info("Received raw message: {}", buffer.toString());
                ClientAction clientAction = Json.decodeValue(buffer.toString(), ClientAction.class);
                handleClientAction(socket, clientAction);
//                broadcastMovement(chatItem);
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



    //------------------------------handleClientAction 로직 관련 메서드 ----------------------------

    private void initLocationData(int x,int y,int userId,String nickName,String texture,ServerWebSocket socket){
        JsonObject locationData = new JsonObject()
                .put("x", x)
                .put("y", y)
                .put("direction", DOWN)
                .put("nickName", nickName)
                .put("texture",texture);
        userLocationMap.put(userId, locationData);
    }




    //------------------------------handleClientAction 로직 관련 메서드 ----------------------------


    //------------------------------METHOD ----------------------------


    /**
     * 사용자를 방에 추가하는 메서드
     *
     * @param roomNum 방의 번호
     * @param userId   추가할 사용자의 ID
     */
    private void addNewUserToRoomToUserMap(int roomNum, int userId) {
        System.out.println("addNewUserToRoomToUserMap" + roomNum + ": " + userId);

        roomToUsersMap.get(roomNum, res -> {
            if (res.succeeded()) {
                System.out.println(roomNum+ "번 방에 해당하는 value 값을 성공적으로 가져왔을 때");

                Set<Integer> userIDs = res.result();
                if (userIDs == null) {
                    System.out.println(roomNum+"번 방에 유저 자신밖에 없을 때");
                    userIDs = new HashSet<>();
                }
                userIDs.add(userId);
                System.out.println(roomNum+"번 방에 있는 userId 리스트" + userIDs);

                roomToUsersMap.put(roomNum, userIDs, putRes -> {
                    if (putRes.failed()) {
                        System.err.println("roomToUsersMap에 값을 추가하는데 실패 한 경우 " + putRes.cause());
                    }
                    System.out.println("roomToUsersMap에 값을 성공적으로 추가한 경우 ");
                    printUsersInRoom(roomNum);

                });
            } else {
                // Handle get failure
                System.out.println(roomNum+ "번 방에 해당하는 value 값을 가져오는데 실패한 경우" + res.cause());
                printUsersInRoom(roomNum);

            }
        });
    }

    private void removeUserFromRoomToUserMap(int roomNum,int userId) {
        roomToUsersMap.get(roomNum, res -> {
            if (res.succeeded()) {
                System.out.println(roomNum+ "번 방에 해당하는 value 값을 성공적으로 가져왔을 때");
                Set<Integer> userIDs = res.result();
                if (userIDs != null) {
                    userIDs.remove(userId);
                    if (userIDs.isEmpty()) {
                        System.out.println(roomNum+ "번 방에 유저 자신밖에 없을 때 ");
                        roomToUsersMap.remove(roomNum, removeRes -> {
                            if (removeRes.failed()) {
                                // Handle remove failure
                                System.err.println("Failed to remove room: " + removeRes.cause());
                            }
                            printUsersInRoom(roomNum);

                        });
                    } else {
                        roomToUsersMap.put(roomNum, userIDs, putRes -> {
                            if (putRes.failed()) {
                                // Handle put failure
                                System.err.println("Failed to update room: " + putRes.cause());
                            }
                            printUsersInRoom(roomNum);

                        });
                    }
                }
            } else {
                // Handle get failure
                System.err.println("Failed to get users from room: " + res.cause());
            }
        });
    }

    public void changeUserLocationMapData (int userId,int x,int y,int direction){
        logger.debug("changeUserLocationMapData 함수 호출");
        userLocationMap.get(userId, res -> {
            if (res.succeeded()) {
                JsonObject playerInfo = res.result();
                if (playerInfo != null) {
                    // playerInfo 객체의 x, y 값을 변경합니다.
                    playerInfo.put("x", x);
                    playerInfo.put("y", y);
                    playerInfo.put("direction",direction);

                    // 변경된 playerInfo 객체를 다시 맵에 저장합니다.
                    userLocationMap.put(userId, playerInfo, putRes -> {
                        if (putRes.succeeded()) {
                            logger.info("userId: {}의 위치 정보가 성공적으로 업데이트 되었습니다.", userId);
                            logger.info(String.valueOf(userLocationMap.entries()));
                        } else {
                            logger.error("userId: {}의 위치 정보를 업데이트하는 동안 오류가 발생했습니다: {}", userId, putRes.cause().getMessage());
                        }
                    });
                } else {
                    logger.warn("userId: {}에 대한 playerInfo가 존재하지 않습니다.", userId);
                }
            } else {
                logger.error("userId: {}의 playerInfo를 얻는 동안 오류가 발생했습니다: {}", userId, res.cause().getMessage());
            }
        });
    }

    public void removeExitUserInfo (ServerWebSocket socket, int userIdToRemove,int roomNum ){
        logger.debug("removeExitUserInfo");

        clients.remove(socket); // 소켓에서 클라이언트 제거
        logger.info("나간 유저 제거 후 clients  값 확인 : {}", clients);

        removeUserToSocketMap(userIdToRemove);
        removeUserLocationMap(userIdToRemove);
        removeUserFromRoomToUserMap(roomNum,userIdToRemove);
    }

    public void removeUserToSocketMap(int userIdToRemove){
        logger.info("removeUserToSocketMap");

        // userToSocketMap에서 유저 제거
        userToSocketMap.remove(userIdToRemove, res -> {
            if (res.succeeded()) {
                logger.info("userToSocketMap에서 유저 {} 제거 성공", userIdToRemove);
                printUserToSocketMap();

            } else {
                logger.error("userToSocketMap에서 유저 {} 제거 실패: {}", userIdToRemove, res.cause().getMessage());
                printUserToSocketMap();

            }
        });
    }

    public void removeUserLocationMap(int userIdToRemove){
        logger.info("removeUserLocationMap");
        // userLocationMap에서 유저 제거
        userLocationMap.remove(userIdToRemove, res -> {
            if (res.succeeded()) {
                logger.info("userLocationMap에서 유저 {} 제거 성공", userIdToRemove);
                printUserLocationMap();

            } else {
                logger.error("userLocationMap에서 유저 {} 제거 실패: {}", userIdToRemove, res.cause().getMessage());
                printUserLocationMap();
            }
        });

    }

    public void printUserLocationMap(){
        logger.debug("printUserLocationMap ");
        userLocationMap.entries(res -> {
            if (res.succeeded()) {
                Map<Integer, JsonObject> entries = res.result();
                logger.info("userLocationMap 값 : {}", entries);

            } else {
                logger.error("userLocationMap entries 가져오는 실패함", res.cause().getMessage());
            }
        });
    }

    public void printUserToSocketMap(){
        logger.debug("printUserToSocketMap ");
        userToSocketMap.entries(res -> {
            if (res.succeeded()) {
                Map<Integer, String> entries = res.result();
                logger.info("userToSocketMap 값 : {}", entries);

            } else {
                logger.error("userToSocketMap entries 가져오는 실패함", res.cause().getMessage());
            }
        });
    }

    /**
     * 방에 있는 사용자 목록을 반환하는 메서드
     *
     * @param roomNum 방의 이름
     */
    private void printUsersInRoom(Integer roomNum) {
        System.out.println("printUsersInRoom");

        roomToUsersMap.get(roomNum, res -> {
            if (res.succeeded()) {
                Set<Integer> userIDs = res.result();
                if (userIDs != null) {
                    // 성공적으로 사용자 목록을 가져왔을 때의 로직
                    System.out.println("Users in room " + roomNum + ": " + userIDs);
                } else {
                    System.out.println("No users found in room " + roomNum);
                }
            } else {
                // 사용자 목록을 가져오는데 실패했을 때의 에러 처리
                System.err.println("Failed to get users from room " + roomNum + ": " + res.cause());
            }
        });
    }


    private Future<Set<Integer>> getUsersInRoom(Integer roomNumber) {
        System.out.println("getUsersInRoom");

        Promise<Set<Integer>> promise = Promise.promise();
        roomToUsersMap.get(roomNumber, ar -> {
            if (ar.succeeded()) {
                promise.complete(ar.result());
            } else {
                promise.fail(ar.cause());
            }
        });
        return promise.future();
    }

    private void sendMessageToRoomUsers(Integer roomNumber, JsonObject message) {
        logger.info("sendMessageToRoomUsers : 특정 방에 속한 모든 유저에게 메세지 보냄");
        getUsersInRoom(roomNumber).onComplete(roomUsers -> {
            if (roomUsers.succeeded()) {
                logger.info("sendMessageToRoomUsers / 성공적으로 사용자 목록을 가져왔습니다.");
                Set<Integer> users = roomUsers.result();

                // 여기서 users가 null 또는 비어 있는 경우를 확인합니다.
                if (users == null || users.isEmpty()) {
                    logger.warn("sendMessageToRoomUsers / 방 번호 {}에 사용자가 없습니다.", roomNumber);
                    return;  // users가 null이나 비어 있으면 추가 로직을 실행하지 않습니다.
                }

                logger.info("sendMessageToRoomUsers / 해당 방의 모든 사용자에게 메시지를 보내기 위해 사용자 목록을 순회합니다.");
                for (Integer userId : users) {
                    logger.info("sendMessageToRoomUsers / 사용자 ID " + userId + "를 통해 해당 사용자의 WebSocket 주소를 비동기로 조회합니다.");
                    userToSocketMap.get(userId, result -> {
                        if (result.succeeded() && result.result() != null) {
                            String clientAddress = result.result();
                            logger.info("sendMessageToRoomUsers / 사용자의 WebSocket 주소 조회가 성공했습니다. 주소: " + clientAddress);

                            for (ServerWebSocket  clientSocket : clients) {
                                if (clientSocket.remoteAddress().toString().equals(clientAddress)) {
                                    try {
                                        clientSocket.writeTextMessage(message.toString());
                                        logger.info("sendMessageToRoomUsers / Message sent to user {}: {}", userId, message.encode());
                                    } catch (Exception e) {
                                        logger.error("sendMessageToRoomUsers / Failed to send message to client {}: {}", clientSocket.remoteAddress().host(), e.getMessage());
                                    }
                                }
                            }
                        } else {
                            if (result.failed()) {
                                logger.error("sendMessageToRoomUsers / Failed to retrieve socket address for user {}: {}", userId, result.cause().getMessage());
                            }
                        }
                    });
                }
            } else {
                logger.error("sendMessageToRoomUsers / Failed to get users in room: {}", roomUsers.cause().getMessage());
            }
        });
    }

    private void sendUserListToNewUser(ServerWebSocket socket, ClientAction clientAction) {
        // 먼저, 해당 방의 유저 목록을 가져옵니다.
        logger.info("sendUserListToNewUser : 당 방의 유저 목록을 가져오고 새로 접속한 유저에게 보내줌");

        getUsersInRoom(clientAction.getRoomNumber()).onComplete(roomUsers -> {
            if (roomUsers.succeeded()) {
                logger.info("sendUserListToNewUser : 성공적으로 사용자 목록을 가져왔습니다.");
                Set<Integer> usersInRoom = roomUsers.result();

                // 해당 방의 유저 정보만 userLocationMap에서 가져옵니다.
                userLocationMap.entries(res -> {
                    logger.info("sendUserListToNewUser / 해당 방의 유저 정보만 userLocationMap에서 가져옴");

                    if (res.succeeded()) {
                        logger.info("sendUserListToNewUser /해당 방의 유저 정보만 성공적으로 가져왔을 때");

                        Map<Integer, JsonObject> entries = res.result();
                        JsonArray players = new JsonArray();

                        for (Map.Entry<Integer, JsonObject> entry : entries.entrySet()) {
                            if (usersInRoom.contains(entry.getKey())) {  // 해당 방의 유저만 처리
                                JsonObject playerInfo = entry.getValue();
                                logger.debug("sendUserListToNewUser / userId: {}와 userNick:{}와 playerInfo: {}로 엔트리를 처리 중입니다", entry.getKey(), playerInfo);
                                playerInfo.put("userId", entry.getKey());
                                players.add(playerInfo);
                                logger.info(String.valueOf(players));
                            }
                        }

                        try {
                            JsonObject exitUserInfo = new JsonObject();
                            exitUserInfo.put("action", "EXISTING_USER_INFO_SUCCESS");
                            exitUserInfo.put("players", players);
                            socket.writeTextMessage(exitUserInfo.toString());
                            logger.info("sendUserListToNewUser / EXISTING_USER_INFO_SUCCESS 보냄");
                        } catch (Exception e) {
                            logger.error(e.getMessage());
                        }



                    } else {
                        logger.error("sendUserListToNewUser / Error retrieving location data: {}", res.cause().getMessage());
                    }
                });
            } else {
                logger.error("sendUserListToNewUser / Failed to get users in room: {}", roomUsers.cause().getMessage());
            }
        });
    }



    // -------------------------- Main METHODS --------------------------
    public static void main(String[] args) {
        setupVertxCluster();
    }

    private static void setupVertxCluster() {
        VertxOptions options = configureVertxOptions();
        Vertx.clusteredVertx(options).onComplete(res -> {
            if (res.succeeded())
                res.result().deployVerticle(MetaverseServer.class.getName(), new DeploymentOptions().setInstances(NUM_OF_INSTANCES));
            else logger.error("Cluster up failed: ", res.cause());
        });
    }

    private static VertxOptions configureVertxOptions() {
        Config hazelcastConfig = new Config();
        hazelcastConfig.setClusterName("metaverse-move-main");

//        hazelcastConfig.setClusterName("my-cluster-wow");
//        hazelcastConfig.setCPSubsystemConfig(new CPSubsystemConfig().setCPMemberCount(3)); // 여기 주석을 쳐야
        ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
        return new VertxOptions().setClusterManager(mgr);
    }


    //------------------------ webrtc -----------------------------

    // TODO :  신지 추가
    public void addMediaData(
            int userId,
            String nickName,
            int roomNumber,
            String screenShareStreamId,
            String videoStreamId) {
        // mediaData 관련된 값 저장해줌
        JsonObject mediaData = new JsonObject()
                .put("userId", userId)
                .put("nickName", nickName)
                .put("roomNumber", roomNumber)
                .put("videoStreamId", videoStreamId)
                .put("screenShareStreamId", screenShareStreamId);
        userMediaMap.put(userId, mediaData);
        userMediaMap.entries(res -> {
            if (res.succeeded()) {
                Map<Integer, JsonObject> entries = res.result();
                logger.info("userMediaMap 값 : \n{}", entries);

                JsonArray jsonArray = new JsonArray();
                for (Map.Entry<Integer, JsonObject> entry : entries.entrySet()) {
                    JsonObject jsonObject = entry.getValue();
                    if (jsonObject.getInteger("roomNumber") == roomNumber) {
//                        updateMediaData(roomNumber, jsonObject);
                        jsonArray.add(jsonObject);
                    }
                }

                logger.info("String.valueOf(jsonArray) 값 : \n{}", String.valueOf(jsonArray));
//                JsonObject jsonObjectToSend = new JsonObject(String.valueOf(jsonArray));
                JsonObject jsonObjectToSend = new JsonObject().put("users", jsonArray);
                logger.info("jsonObjectToSend 값 : \n{}", jsonObjectToSend);
                updateMediaData(roomNumber, jsonObjectToSend);

            } else {
                logger.error("userMediaMap entries 가져오는 실패함", res.cause().getMessage());
            }
        });
    }

    // TODO :  신지 추가
    public void updateMediaData(int roomNumber, JsonObject jsonObject) {
        JsonObject jsonObject1 = jsonObject;
        jsonObject1.put("action", "UPDATED_WEBRTC_STATUS");
        sendMessageToRoomUsers(roomNumber, jsonObject);
    }

    // TODO :  신지 추가
    public void removeMediaData(
            int userId,
            String nickName,
            int roomNumber,
            String screenShareStreamId,
            String videoStreamId
    ) {

        userMediaMap.entries(res -> {
            if (res.succeeded()) {
                Map<Integer, JsonObject> entries = res.result();
                logger.info("userMediaMap 값 : \n{}", entries);

                JsonArray jsonArray = new JsonArray();
                for (Map.Entry<Integer, JsonObject> entry : entries.entrySet()) {
                    JsonObject jsonObject = entry.getValue();
                    if (jsonObject.getInteger("roomNumber") == roomNumber) {
//                        updateMediaData(roomNumber, jsonObject);
                        jsonArray.add(jsonObject);
                    }
                    // userId와 일치하는 항목을 찾으면 해당 항목을 userMediaMap에서 제거
                    if (entry.getKey().equals(userId)) {
                        userMediaMap.remove(userId, removeRes -> {
                            if (removeRes.succeeded()) {
                                logger.info("userId {} 에 해당하는 항목을 userMediaMap에서 제거하였습니다.", userId);
                            } else {
                                logger.error("userMediaMap에서 userId {} 항목을 제거하는 데 실패했습니다.", userId, removeRes.cause());
                            }
                        });
                    }
                }

                logger.info("String.valueOf(jsonArray) 값 : \n{}", String.valueOf(jsonArray));
//                JsonObject jsonObjectToSend = new JsonObject(String.valueOf(jsonArray));
                JsonObject jsonObjectToSend = new JsonObject().put("users", jsonArray);
                logger.info("jsonObjectToSend 값 : \n{}", jsonObjectToSend);
                updateMediaData(roomNumber, jsonObjectToSend);

            } else {
                logger.error("userMediaMap entries 가져오는 실패함", res.cause().getMessage());
            }
        });
    }



}