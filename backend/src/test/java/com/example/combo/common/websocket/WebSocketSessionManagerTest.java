package com.example.combo.common.websocket;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocketSessionManager 多端点注册/注销行为测试（Mockito 模拟 Session，无需真实连接）。
 */
class WebSocketSessionManagerTest {

    private WebSocketSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new WebSocketSessionManager();
    }

    /** 构造一个"已打开"的 mock Session（getBasicRemote 用于校验消息发送） */
    private Session openSession(String id) {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(basic);
        return session;
    }

    @Test
    @DisplayName("同一玩家可同时注册多个端点，消息广播到所有端点")
    void registerMultipleEndpointsBroadcastsToAll() throws Exception {
        Session friend = openSession("s-friend");
        Session chat = openSession("s-chat");

        manager.register(1L, "friend", friend);
        manager.register(1L, "chat", chat);

        assertTrue(manager.isOnline(1L));
        assertTrue(manager.sendToUser(1L, "hello"));

        verify(friend.getBasicRemote()).sendText("hello");
        verify(chat.getBasicRemote()).sendText("hello");
    }

    @Test
    @DisplayName("同一端点重复注册：旧会话被关闭，只有新会话收到消息")
    void reRegisterClosesOldSession() throws Exception {
        Session old = openSession("old");
        Session fresh = openSession("fresh");

        manager.register(1L, "friend", old);
        manager.register(1L, "friend", fresh);

        verify(old).close(); // 旧会话被主动关闭
        assertTrue(manager.sendToUser(1L, "msg"));
        verify(fresh.getBasicRemote()).sendText("msg");
        verify(old.getBasicRemote(), never()).sendText(any());
    }

    @Test
    @DisplayName("旧会话延迟触发 onClose：身份校验保证不误删新注册的会话")
    void staleUnregisterKeepsNewSession() throws Exception {
        Session old = openSession("old");
        Session fresh = openSession("fresh");
        manager.register(1L, "friend", old);
        manager.register(1L, "friend", fresh);

        // 模拟旧会话的 @OnClose 延迟到达（传入的是被替换掉的旧 session）
        manager.unregister(1L, "friend", old);

        assertTrue(manager.isOnline(1L)); // 新会话仍在
        assertTrue(manager.sendToUser(1L, "still-here"));
        verify(fresh.getBasicRemote()).sendText("still-here");
    }

    @Test
    @DisplayName("注销最后一个端点后玩家离线，发送返回 false")
    void unregisterLastEndpointGoesOffline() throws Exception {
        Session friend = openSession("s-friend");
        manager.register(1L, "friend", friend);

        manager.unregister(1L, "friend", friend);

        assertFalse(manager.isOnline(1L));
        assertFalse(manager.sendToUser(1L, "no-one"));
    }
}
