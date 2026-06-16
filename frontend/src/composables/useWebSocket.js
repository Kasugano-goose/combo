import { ref, onUnmounted } from 'vue'

export function useWebSocket(path, { onMessage, onOpen, onClose, onError } = {}) {
  const connected = ref(false)
  let ws = null
  let reconnectTimer = null

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
      onOpen?.()
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        onMessage?.(data)
      } catch {
        // 非 JSON 消息忽略
      }
    }

    ws.onclose = () => {
      connected.value = false
      onClose?.()
    }

    ws.onerror = () => {
      onError?.()
    }
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
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
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return { connected, connect, disconnect, send }
}
