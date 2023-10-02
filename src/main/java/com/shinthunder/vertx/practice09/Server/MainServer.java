package com.shinthunder.vertx.practice09.Server;

// TODO : 230830 1030 : 초대기능 만들기 제작했음. 이제 (create + join + watch + invite + notice)을 하나로 통합한 액션을 추가하기

import com.hazelcast.config.Config;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
/** 커밋 순서
 * 1. teamnova0 : gcam
 * 2. hawaii : gcam
 * 3. hawaii : gpu
 * 4. teamnova0 : gpx (== git pull -X theirs)
 * */
/**
 * [추가해야할 기능]
 * 1. 채팅방을 생성했을 때, 상대방을 초대할 수 있도록 해야함.
 * 2. 생성 시 초대가 연동되게 하는게 아니라, 초대 기능을 따로 만들어서, 채팅방 create join 액션 시 invite 되도록 하기!
 * <p>
 * [리눅스 옮긴 마지막 시간 : 230829 1920]
 * <p>
 * [리눅스 옮길 때 주의]
 * // logback.xml에 추가
 * //      <logger name="org.mariadb.jdbc" level="warn"/>
 * //
 * // pom.xml에 추가
 * //      <mariadb-java-client.version>3.1.4</mariadb-java-client.version>
 * //      <dependency>
 * //       <groupId>org.mariadb.jdbc</groupId>
 * //      	 <artifactId>mariadb-java-client</artifactId>
 * //       <version>${mariadb-java-client.version}</version>
 * //      </dependency>
 * //
 * // DBVerticle.java에서 수정
 * //   setupDB() 메서드에서 jdbc url을 수정해야함.
 * //
 * // MainServer.java에서 수정
 * //       HOST_SERVER = "0.0.0.0"으로 해야함
 * <p>
 * <p>
 * [개발 개요]
 * * 전반적으로 멀티 인스턴스일 때 문제가 많음. 현재 고성능이 필요하지 않으니, 인스턴스를 1개로 운영하기. 그러면 문제 안생김.
 * * 메타버스 움직임 처리할 때는 인스턴스를 여러 개로 운영하기.
 * <p>
 * [문제 1]
 * a,b,c에서 서버 접속 -> 방(aa) 접속 -> 채팅하다가 -> c를 /unwatch -> a,b가 서로 채팅하면 == duplicated msg 시작됨. 문제 해결할 것
 */
public class MainServer {
    // -------------------------- CONSTANTS --------------------------
    private static final Logger logger = LoggerFactory.getLogger(MainServer.class);
    private static final int NUM_OF_INSTANCES = 1;

    public static final String HOST_SERVER = "0.0.0.0";
    //    public static final String HOST_SERVER = "localhost";
    //    public static final String HOST_SERVER = "127.0.0.1";
    public static final String ADDRESS_BROADCAST_MESSAGE = "broadcast.message.address";
    public static final String ADDRESS_IMAGE_ACTION = "image.actions";
    public static final String ADDRESS_DB_ACTION = "db.actions";

    public static final int WEBSOCKET_PORT = 50000;
    public static final int HTTP_PORT = 50001;

    public static final long MAX_FILE_SIZE = 500L * 1024 * 1024; // 500MB
    public static final Set<String> SUPPORTED_FILE_TYPES = new HashSet<>(Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".txt", ".aac", ".mp3", ".mp4", ".mov", ".webm"));
    public static final String uploadDirectory = "/home/teamnova0/workspace/testbed/shinji/chat-v3-image-upload-directory/";
//    public static final String downloadDirectory = "/home/teamnova0/workspace/testbed/shinji/chat-v3-image-download-directory/";

    /**
     * ---------------------  JDBC  -----------------------------
     */
    public static final String JDBC_USER = "root";
    public static final String JDBC_URL = "jdbc:mysql://localhost:3306/linktown";// hawaii
    public static final String JDBC_PASSWORD = "root";// hawaii
//    public static final String JDBC_URL = "jdbc:mariadb://localhost:3306/linktown";// teamnova0
//    public static final String JDBC_PASSWORD = "teamnova0";// teamnova0

    public static final String deleteChatRoomParticipant = "deleteChatRoomParticipant";
    public static final String deleteChatRoomActiveStatus = "deleteChatRoomActiveStatus";
    public static final String insertChatItem = "insertChatItem";
    public static final String insertChatItemAndSelectTimestamp = "insertChatItemAndSelectTimestamp";
    public static final String selectUserById = "selectUserById";
    public static final String selectUserByEmail = "selectUserByEmail";
    public static final String selectUserByEmailForConnect = "selectUserByEmailForConnect";
    public static final String selectChatRoomWhereId = "selectChatRoomWhereId";
    public static final String selectChatRoomWhereName = "selectChatRoomWhereName";
    public static final String selectChatRoomsCountForTalentReq = "selectChatRoomsCountForTalentReq";
    public static final String selectChatItemWhereRoomId = "selectChatItemWhereRoomId";
    public static final String selectChatRoomParticipantWhereUserId = "selectChatRoomParticipantWhereUserId";
    public static final String selectChatRoomParticipantWhereRoomId = "selectChatRoomParticipantWhereRoomId";
    public static final String selectAllEmailsWHERERoomIdWithJoin = "selectAllEmailsWHERERoomIdWithJoin";
    public static final String selectChatItemWhereId = "selectChatItemWhereId";
    public static final String insertUser = "insertUser";
    public static final String insertChatRoom = "insertChatRoom";
    public static final String insertChatRoomParticipant = "insertChatRoomParticipant";
    public static final String selectChatRoomActiveStatusByRoomId = "selectChatRoomActiveStatusByRoomId";
    public static final String selectChatRoomActiveStatusByUserId = "selectChatRoomActiveStatusByUserId";
    public static final String selectMessageReadStatusByMessageId = "selectMessageReadStatusByMessageId";
    public static final String selectMessageReadStatusByUserId = "selectMessageReadStatusByUserId";
    public static final String insertChatRoomActiveStatus = "insertChatRoomActiveStatus";
    public static final String insertMessageReadStatus = "insertMessageReadStatus";
    public static final String updateChatRoomActiveStatusWHEREUserId = "updateChatRoomActiveStatusWHEREUserId";
    public static final String updateChatRoomActiveStatusWHERERoomIdAndUserId = "updateChatRoomActiveStatusWHERERoomIdAndUserId";
    public static final String updateMessageReadStatus = "updateMessageReadStatus";

    /**
     * ---------------------  ACTION  -----------------------------
     */
    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";
    public static final String STATUS = "STATUS";
    public static final String CREATE_JOIN_WATCH_INVITE_NOTICE = "CREATE_JOIN_WATCH_INVITE_NOTICE";
    public static final String CREATE_JOIN_WATCH_INVITE_NOTICE_SUCCESS = "CREATE_JOIN_WATCH_INVITE_NOTICE_SUCCESS";
    public static final String CREATE_JOIN_WATCH_INVITE_NOTICE_FAILED = "CREATE_JOIN_WATCH_INVITE_NOTICE_FAILED";
    public static final String CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_GENERAL = "CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_GENERAL";
    public static final String CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_TALENT_REQ = "CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_TALENT_REQ";
    public static final String CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_TALENT_SELL = "CREATE_JOIN_WATCH_INVITE_NOTICE_WITH_TALENT_SELL";
    public static final String MESSAGE = "MESSAGE";
    public static final String FILE = "FILE";
    public static final String MESSAGE_DUPLICATED = "MESSAGE_DUPLICATED";
    public static final String MESSAGE_NOTICE = "MESSAGE_NOTICE";
    public static final String CONNECT = "CONNECT";
    public static final String CONNECT_SUCCESS = "CONNECT_SUCCESS";
    public static final String CONNECT_FAILED = "CONNECT_FAILED";
    public static final String DISCONNECT = "DISCONNECT";
    public static final String INVITE = "INVITE";
    public static final String CREATE = "CREATE";
    public static final String JOIN = "JOIN";
    public static final String WATCH = "WATCH";
    public static final String WATCH_SUCCESS = "WATCH_SUCCESS";
    public static final String WATCH_FAILED = "WATCH_FAILED";
    public static final String UNWATCH = "UNWATCH";
    public static final String UNWATCH_SUCCESS = "UNWATCH_SUCCESS";
    public static final String UNWATCH_FAILED = "UNWATCH_FAILED";
    public static final String LEAVE = "LEAVE";
    public static final String LEAVE_SUCCESS = "LEAVE_SUCCESS";
    public static final String LEAVE_FAILED = "LEAVE_FAILED";
    public static final String SELECT_CHATROOMLIST = "SELECT_CHATROOMLIST";
    public static final String SELECT_CHATROOMLIST_FAILED = "SELECT_CHATROOMLIST_FAILED";
    public static final String SELECT_CHATROOM = "SELECT_CHATROOM";
    public static final String PREPARE_IMAGE_UPLOAD = "PREPARE_IMAGE_UPLOAD";
    public static final String PREPARE_IMAGE_UPLOAD_SUCCESS = "PREPARE_IMAGE_UPLOAD_SUCCESS";
    public static final String PREPARE_IMAGE_UPLOAD_FAILED = "PREPARE_IMAGE_UPLOAD_FAILED";
    public static final String IMAGE_UPLOADED = "IMAGE_UPLOADED";
    public static final String START_IMAGE_DOWNLOAD = "START_IMAGE_DOWNLOAD";
    public static final String GENERAL = "GENERAL";
    public static final String TALENT_SELL = "TALENT_SELL";
    public static final String TALENT_REQ = "TALENT_REQ";

    public static void main(String[] args) {
        setupVertxCluster();
    }

    private static void setupVertxCluster() {
        VertxOptions options = configureVertxOptions();
        Vertx.clusteredVertx(options).onComplete(res -> {
            if (res.succeeded()) {
                Vertx vertx = res.result();
                vertx.deployVerticle(ChatVerticle.class.getName(), new DeploymentOptions().setInstances(NUM_OF_INSTANCES));
                vertx.deployVerticle(DBVerticle.class.getName());
                vertx.deployVerticle(HTTPVerticle.class.getName());
            } else {
                logger.error("Cluster up failed: ", res.cause());
            }
        });
    }

    private static VertxOptions configureVertxOptions() {
        Config hazelcastConfig = new Config();
        hazelcastConfig.setClusterName("my-cluster-wow-2");
        ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
        return new VertxOptions().setClusterManager(mgr);
    }
}
