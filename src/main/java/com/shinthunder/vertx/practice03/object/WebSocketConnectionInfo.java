package com.shinthunder.vertx.practice03.object;

public class WebSocketConnectionInfo {
    private String userId;  // 사용자 ID
    private String ipAddress;  // IP 주소
    private long connectedTime;  // 연결 시간

    public WebSocketConnectionInfo(String userId, String ipAddress, long connectedTime) {
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.connectedTime = connectedTime;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public long getConnectedTime() {
        return connectedTime;
    }

    public void setConnectedTime(long connectedTime) {
        this.connectedTime = connectedTime;
    }
}