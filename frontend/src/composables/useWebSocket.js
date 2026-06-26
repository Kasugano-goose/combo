import { ref, onUnmounted } from 'vue'

export function useWebSocket(path, { onMessage, onOpen, onClose, onError } = {}) {
  const connected = ref(false)
  let ws = null
  let reconnectTimer = null
  let reconnectAttempts = 0
  const MAX_RECONNECT_ATTEMPTS = 10
  const BASE_DELAY = 1000 // 初始重连间隔 1s

  function getUrl() {
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${proto}//${window.location.host}${path}`
  }

  function connect() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      return
    }

    ws = new WebSocket(getUrl())

    ws.onopen = () => {
      connected.value = true
      reconnectAttempts = 0 // 重连成功，重置计数
      console.log('[WebSocket] 连接已建立:', getUrl())
      onOpen?.()
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        console.log('[WebSocket] 收到消息:', data.type, data)
        onMessage?.(data)
      } catch {
        // 非 JSON 消息忽略
        console.warn('[WebSocket] 非 JSON 消息:', event.data)
      }
    }

    ws.onclose = (event) => {
      connected.value = false
      console.warn('[WebSocket] 连接关闭, code=', event.code, 'reason=', event.reason)
      onClose?.()

      // 自动重连（指数退避）
      if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
        const delay = Math.min(BASE_DELAY * Math.pow(2, reconnectAttempts), 30000)
        reconnectAttempts++
        console.log(`[WebSocket] ${delay}ms 后尝试第 ${reconnectAttempts} 次重连...`)
        reconnectTimer = setTimeout(() => {
          connect()
        }, delay)
      } else {
        console.error('[WebSocket] 重连次数已达上限，停止重连')
      }
    }

    ws.onerror = (event) => {
      console.error('[WebSocket] 连接错误:', event)
      onError?.()
    }
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    reconnectAttempts = MAX_RECONNECT_ATTEMPTS // 阻止自动重连
    if (ws) {
      ws.onclose = null // 避免触发 onClose 回调
      ws.close()
      ws = null
      connected.value = false
    }
  }

  function send(data) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(typeof data === 'string' ? data : JSON.stringify(data))
    } else {
      console.warn('[WebSocket] 发送失败，连接未就绪, readyState=', ws?.readyState)
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return { connected, connect, disconnect, send }
}
