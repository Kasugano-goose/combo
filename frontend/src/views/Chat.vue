<template>
  <div class="home-body">
    <div class="home-page">
      <div class="home-header">
        <h1>聊天</h1>
        <div class="header-actions">
          <router-link to="/home" class="secondary-button">返回主页</router-link>
        </div>
      </div>

      <div class="chat-container">
        <div class="chat-sidebar">
          <div class="chat-target-form">
            <input v-model.number="targetId" type="number" placeholder="输入对方账号ID" />
            <button class="action-button" @click="startChat" :disabled="!targetId">
              开始聊天
            </button>
          </div>

          <div class="ws-status">
            <span :class="['ws-dot', connected ? 'connected' : 'disconnected']"></span>
            {{ connected ? '已连接' : '未连接' }}
          </div>

          <div v-if="chatTarget" class="chat-target-info" style="margin-top:12px;">
            正在和 <strong>{{ chatTarget }}</strong> 聊天
          </div>
        </div>

        <div class="chat-main">
          <div class="chat-messages" ref="messagesRef">
            <div v-if="messages.length === 0" class="chat-hint">
              输入对方ID开始聊天
            </div>
            <div v-for="(msg, i) in messages" :key="i"
              :class="['chat-bubble', msg.type]">
              {{ msg.content }}
              <div v-if="msg.time" class="chat-time">{{ msg.time }}</div>
            </div>
          </div>

          <div class="chat-input-area">
            <input v-model="inputText" placeholder="输入消息..."
              @keyup.enter="sendMessage" :disabled="!connected" />
            <button class="action-button" @click="sendMessage"
              :disabled="!connected || !inputText.trim()">
              发送
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onUnmounted } from 'vue'
import { usePlayerStore } from '../stores/player'
import { useWebSocket } from '../composables/useWebSocket'

const store = usePlayerStore()
const targetId = ref(null)
const chatTarget = ref('')
const inputText = ref('')
const messages = ref([])
const messagesRef = ref(null)

function scrollBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function addMessage(content, type = 'system', time = '') {
  messages.value.push({ content, type, time })
  scrollBottom()
}

const { connected, connect, disconnect, send } = useWebSocket(`/ws/chat/${store.playerId}`, {
  onMessage(data) {
    if (data.type === 'CHAT') {
      const time = data.timestamp ? new Date(data.timestamp).toLocaleTimeString() : ''
      addMessage(data.content, 'received', time)
    } else if (data.type === 'ERROR') {
      addMessage(data.content, 'error')
    }
  },
  onOpen() {
    addMessage('聊天服务已连接', 'system')
  },
  onClose() {
    addMessage('连接已断开', 'system')
  }
})

function startChat() {
  if (!targetId.value) return
  if (targetId.value === store.playerId) {
    alert('不能和自己聊天')
    return
  }
  disconnect()
  messages.value = []
  chatTarget.value = `玩家 ${targetId.value}`
  connect()
  addMessage(`正在连接...`, 'system')
}

function sendMessage() {
  const text = inputText.value.trim()
  if (!text || !connected.value) return

  send({ toPlayerId: targetId.value, content: text })

  const time = new Date().toLocaleTimeString()
  addMessage(text, 'sent', time)
  inputText.value = ''
}

onUnmounted(() => {
  disconnect()
})
</script>
