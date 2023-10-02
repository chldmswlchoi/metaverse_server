package com.shinthunder.vertx.practice05.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.List;

public class DBServer extends AbstractVerticle {
    private JDBCClient jdbcClient;

    @Override
    public void start() {

        JsonObject mysqlConfig = new JsonObject()
                .put("url", "jdbc:mysql://YOUR_MYSQL_HOST:YOUR_MYSQL_PORT/YOUR_DB_NAME")
                .put("driver_class", "com.mysql.jdbc.Driver")
                .put("user", "YOUR_DB_USER")
                .put("password", "YOUR_DB_PASSWORD");

        jdbcClient = JDBCClient.createShared(vertx, mysqlConfig);

    }
    private void fetchAllUsers(Handler<AsyncResult<List<JsonObject>>> handler) {
        jdbcClient.query("SELECT * FROM User", res -> {
            if (res.succeeded()) {
                handler.handle(Future.succeededFuture(res.result().getRows()));
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

}
