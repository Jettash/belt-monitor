package com.wjiamao.belt_monitor.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 处理器
 * 负责管理所有前端连接，并向它们推送数据
 */
@Component
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    // 存放所有当前连接的前端客户端
    private static final CopyOnWriteArraySet<WebSocketSession> sessions
            = new CopyOnWriteArraySet<>();

    /** 前端连接时触发 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("前端已连接，当前连接数：" + sessions.size());
    }

    /** 前端断开时触发 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("前端已断开，当前连接数：" + sessions.size());
    }

    /**
     * 向所有已连接的前端推送消息
     * 由 DataSimulator 定时调用
     */
    public static void broadcast(String jsonMessage) {
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            } catch (Exception e) {
                System.err.println("推送失败：" + e.getMessage());
            }
        }
    }
}