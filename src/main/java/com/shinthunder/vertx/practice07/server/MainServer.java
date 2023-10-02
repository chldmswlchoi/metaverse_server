package com.shinthunder.vertx.practice07.server;
/**
 * 내일 주의사항
 * 1. 클라이언트측을 자바로 하지 말고, 프론트엔드에서 접속하기
 * 이유 : 그래야 user_id, roomName 등을 받아오는 순서를 정할 수 있음
 * 2.
 */

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

public class MainServer {
    private static final Logger logger = LoggerFactory.getLogger(MainServer.class);
    private static final int NUM_OF_INSTANCES = 2;
    //    public static final String HOST_SERVER = "0.0.0.0";
    //    public static final String HOST_SERVER = "localhost";
    public static final String HOST_SERVER = "127.0.0.1";
    public static final String ADDRESS_BROADCAST_MESSAGE = "broadcast.message.address";
    public static final String ADDRESS_IMAGE_ACTION = "image.actions";
    public static final String ADDRESS_DB_ACTION = "db.actions";

    public static final int WEBSOCKET_PORT = 50000;
    public static final int HTTP_PORT = 50001;
    public static final Set<String> SUPPORTED_FILE_TYPES = new HashSet<>(Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".txt", ".aac", ".mp3", ".mp4", ".mov", ".webm"));
    public static final String uploadDirectory = "/home/teamnova0/workspace/testbed/shinji/chat-v3-image-upload-directory/";
//    public static final String downloadDirectory = "/home/teamnova0/workspace/testbed/shinji/chat-v3-image-download-directory/";

    /**
     * ---------------------  JDBC  -----------------------------
     */
    public static final String JDBC_USER = "root";
//    public static final String JDBC_URL = "jdbc:mysql://localhost:3306/linktown";// hawaii
    public static final String JDBC_URL = "jdbc:mysql://localhost:3306/linktownChat";// hawaii
    public static final String JDBC_PASSWORD = "root";// hawaii
    //    public static final String JDBC_URL = "jdbc:mariadb://localhost:3306/linktown";// teamnova0
//    public static final String JDBC_PASSWORD = "teamnova0";// teamnova0

    public static void main(String[] args) {
        setupVertxCluster();
    }

    private static void setupVertxCluster() {
        VertxOptions options = configureVertxOptions();
        Vertx.clusteredVertx(options).onComplete(res -> {
            if (res.succeeded()) {
                res.result().deployVerticle(ChatVerticle.class.getName(), new DeploymentOptions().setInstances(NUM_OF_INSTANCES));
                res.result().deployVerticle(DBVerticle.class.getName());
            } else {
                logger.error("Cluster up failed: ", res.cause());
            }
        });
    }

    private static VertxOptions configureVertxOptions() {
        Config hazelcastConfig = new Config();
        hazelcastConfig.setClusterName("my-cluster-wow");
        ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
        return new VertxOptions().setClusterManager(mgr);
    }
}
