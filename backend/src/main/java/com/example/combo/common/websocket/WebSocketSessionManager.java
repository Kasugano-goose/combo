package com.example.combo.common.websocket;

import jakarta.websocket.Session;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {
    //在线用户映射
    private  final ConcurrentHashMap <Long, Session> onlineSession = new ConcurrentHashMap<>();

    // 用户上线 注册会话
    public void register(Long playerId,Session session){
        onlineSession.put(playerId,session);
    }

    //用户下线  移除会话
    public void unregister(Long playerId){
        onlineSession.remove(playerId);
    }
    //判断用户是否上线
    public boolean isOnline(Long playerId){
        return onlineSession.containsKey(playerId);
    }

    //向指定用户发送消息
    public boolean sendToUser(Long playerId,String message){
        Session session = onlineSession.get(playerId);
        if (session != null &&session.isOpen()){
            try {
                session.getBasicRemote().sendText(message);
                return true;
            } catch (IOException e) {
                // 发送失败，移除无效会话
                onlineSession.remove(playerId);
                return false;
            }
        }
        return false;
    }
    //显示在线人数
    public int getOnlineCount() {
        return onlineSession.size();
    }
}
