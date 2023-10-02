// TODO : 230821 1520 HTTP로 이미지 업로드/다운로드 코드 가져오기 완료
package com.shinthunder.vertx.practice00_eunji_t1.server;

import com.hazelcast.config.Config;
import com.shinthunder.vertx.practice00_eunji_t1.object.ChatItem;
import com.shinthunder.vertx.practice00_eunji_t1.object.ClientAction;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
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
import java.util.concurrent.atomic.AtomicInteger;

public class ChatServer extends AbstractVerticle {
    // -------------------------- CONSTANTS --------------------------
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);
    private static final int WEBSOCKET_PORT = 8080;
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
    // -------------------------- WEBSOCKET HANDLER METHODS --------------------------
    private void handleClientAction(ServerWebSocket socket, ClientAction clientAction) {
        logger.info("handleClientAction");


        switch (clientAction.getAction()) {

            case "MOVE":
                Buffer buffer = Json.encodeToBuffer(clientAction);
                clients.forEach(clientSocket -> {
                    try {
                        int userId = clientAction.getUserId();
                        int x = clientAction.getX();
                        int y = clientAction.getY();
                        int direction = clientAction.getDirection();

                        JsonObject data = new JsonObject();
                        data.put("action","MOVE");
                        data.put("direction",direction);
                        data.put("userId",userId);
                        data.put("x",x);
                        data.put("y",y);
                        clientSocket.writeTextMessage(data.toString());
                        logger.info("위치 값 보냄 ", buffer.toString());

                        changeUserLocationMapData(userId,x,y,direction);

                    } catch (Exception e) {
                        logger.error("Failed to send message to client {}: {}", clientSocket.remoteAddress().host(), e.getMessage());
                    }
                });
                break;

            case "SAVE_USER_INFO":
                logger.info(" name : {}, SAVE_USER_INFO !! ", clientAction.getUserId());
                userToSocketMap.put(clientAction.getUserId(), socket.remoteAddress().toString());

                // 기존 유저들에게 새로운 유저가 들어왔다고 알림
                clients.forEach(clientSocket -> {
                    try {
                        JsonObject data = new JsonObject();
                        data.put("action","SAVE_USER_INFO");
                        data.put("userId",clientAction.getUserId());
                        data.put("nickName",clientAction.getNickName());
                        clientSocket.writeTextMessage(data.toString());
                        logger.info("SAVE_USER_INFO");

                    } catch (Exception e) {
                        logger.error("Failed to send message to client {}: {}", clientSocket.remoteAddress().host(), e.getMessage());
                    }
                });


                userLocationMap.entries(res -> { // userLocationMap의 모든 entries 가져옴
                    if (res.succeeded()) {
                        Map<Integer, JsonObject> entries = res.result();

                        if(entries.size()>0) {
                            logger.info("userLocationMap에서 모든 엔트리를 성공적으로 검색했을 때: {}", entries);

                            JsonArray players = new JsonArray();
                            for (Map.Entry<Integer, JsonObject> entry : entries.entrySet()) {
                                JsonObject playerInfo = entry.getValue();
                                logger.debug("userId: {}와 userNick:{}와  playerInfo: {}로 엔트리를 처리 중입니다", entry.getKey(), playerInfo);
                                playerInfo.put("userId", entry.getKey());

                                players.add(playerInfo);
                                logger.info(String.valueOf(players));
                            }

                            try{
                                JsonObject exitUserInfo = new JsonObject();
                                exitUserInfo.put("action", "EXISTING_USER_INFO");
                                exitUserInfo.put("players", players);
                                socket.writeTextMessage(exitUserInfo.toString());
                                logger.info("EXISTING_USER_INFO 보냄");
                            }

                            catch (Exception e) {
                                logger.error( e.getMessage());
                            }
                        }

                        // 자신의 위치 관련된 값 저장해줌
                        JsonObject locationData = new JsonObject()
                                .put("x", 931)
                                .put("y",1073)
                                .put("direction",DOWN)
                                .put("nickName",clientAction.getNickName());
                        userLocationMap.put(clientAction.getUserId(), locationData);
                        logger.info(String.valueOf(socket));

                    } else {
                        logger.error("Error retrieving location data: {}", res.cause().getMessage());
                    }
                });

                break;

            case "REMOVE":
                try{
                    int userId = clientAction.getUserId();
                    removeExitUserInfo(socket,userId);
                    broadcastUserExit(userId);


                }
                catch (Exception e){
                    logger.error("REMOVE event 처리 과정 중 에러 발생 ",e);

                }

                break;

            default:
                logger.warn("Unknown action: {}", clientAction);
        }
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


    //------------------------------METHOD ----------------------------
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

    public void removeExitUserInfo (ServerWebSocket socket, int userIdToRemove ){
        clients.remove(socket); // 소켓에서 클라이언트 제거
        logger.info("나간 유저 제거 후 clients  값 확인 : {}", clients);


        // userToSocketMap에서 유저 제거
        userToSocketMap.remove(userIdToRemove, res -> {
            if (res.succeeded()) {
                logger.info("userToSocketMap에서 유저 {} 제거 성공", userIdToRemove);
            } else {
                logger.error("userToSocketMap에서 유저 {} 제거 실패: {}", userIdToRemove, res.cause().getMessage());
            }
        });

        // userLocationMap에서 유저 제거
        userLocationMap.remove(userIdToRemove, res -> {
            if (res.succeeded()) {
                logger.info("userLocationMap에서 유저 {} 제거 성공", userIdToRemove);
                printUserLocationMap();

            } else {
                logger.error("userLocationMap에서 유저 {} 제거 실패: {}", userIdToRemove, res.cause().getMessage());
                printUserToSocketMap();
            }
        });

    }

    public void broadcastUserExit(int userId){

        clients.forEach(clientSocket -> {
            try {
                JsonObject data = new JsonObject();
                data.put("action","REMOVE");
                data.put("userId",userId);

                clientSocket.writeTextMessage(data.toString());
                logger.info("유저가 나갔다고 클라이언트에 알림 ", data);


            } catch (Exception e) {
                logger.error("Failed to send message to client {}: {}", clientSocket.remoteAddress().host(), e.getMessage());
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

    // -------------------------- Main METHODS --------------------------
    public static void main(String[] args) {
        setupVertxCluster();
    }

    private static void setupVertxCluster() {
        VertxOptions options = configureVertxOptions();
        Vertx.clusteredVertx(options).onComplete(res -> {
            if (res.succeeded())
                res.result().deployVerticle(ChatServer.class.getName(), new DeploymentOptions().setInstances(NUM_OF_INSTANCES));
            else logger.error("Cluster up failed: ", res.cause());
        });
    }

    private static VertxOptions configureVertxOptions() {
        Config hazelcastConfig = new Config();
        hazelcastConfig.setClusterName("my-cluster-wow");
//        hazelcastConfig.setCPSubsystemConfig(new CPSubsystemConfig().setCPMemberCount(3)); // 여기 주석을 쳐야
        ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
        return new VertxOptions().setClusterManager(mgr);
    }


}