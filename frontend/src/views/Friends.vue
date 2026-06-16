<template>
  <div class="home-body">
    <div class="home-page">
      <div class="home-header">
        <h1>好友</h1>
        <div class="header-actions">
          <router-link to="/home" class="secondary-button">返回主页</router-link>
        </div>
      </div>

      <!-- 发送好友请求 -->
      <div class="panel-section">
        <h3 class="section-title">发送好友请求</h3>
        <div class="inline-form">
          <input v-model.number="targetId" type="number" placeholder="输入对方账号ID" />
          <button class="action-button" @click="handleSendRequest" :disabled="sending">
            {{ sending ? '发送中...' : '发送请求' }}
          </button>
        </div>
        <div v-if="msg.text" :class="['message', msg.type]" style="margin-top:12px;">{{ msg.text }}</div>
      </div>

      <!-- 收到的好友请求 -->
      <div class="panel-section">
        <h3 class="section-title">收到的好友请求</h3>
        <div v-if="pendingRequests.length === 0" class="empty-hint">暂无待处理的好友请求</div>
        <div class="request-list">
          <div v-for="req in pendingRequests" :key="req.friendshipId" class="request-item">
            <div class="request-info">
              <span class="request-name">{{ req.requesterName }}</span>
              <span class="request-id">ID: {{ req.requesterId }}</span>
            </div>
            <div class="request-actions">
              <button class="accept-btn" @click="handleAccept(req.friendshipId)">接受</button>
              <button class="reject-btn" @click="handleReject(req.friendshipId)">拒绝</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 好友列表 -->
      <div class="panel-section">
        <h3 class="section-title">我的好友</h3>
        <div v-if="friends.length === 0" class="empty-hint">还没有好友，快去添加吧</div>
        <div class="friend-grid">
          <div v-for="f in friends" :key="f.friendshipId" class="friend-card">
            <div class="friend-avatar">{{ f.username?.charAt(0) || '?' }}</div>
            <div class="friend-info">
              <span class="friend-name">{{ f.username }}</span>
              <span class="friend-rank">{{ rankName(f.rankScore) }} · 积分 {{ f.rankScore }}</span>
              <span class="friend-id">ID: {{ f.playerId }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from '../stores/player'
import { useWebSocket } from '../composables/useWebSocket'
import { sendFriendRequest, acceptRequest, rejectRequest, getFriends, getPendingRequests } from '../api/friendship'

const store = usePlayerStore()
const targetId = ref(null)
const sending = ref(false)
const msg = ref({ text: '', type: '' })
const friends = ref([])
const pendingRequests = ref([])

function rankName(score) {
  if (score >= 5000) return '大师'
  if (score >= 4000) return '钻石'
  if (score >= 3000) return '铂金'
  if (score >= 2000) return '黄金'
  if (score >= 1000) return '白银'
  return '青铜'
}

async function loadData() {
  try {
    const [f, p] = await Promise.all([getFriends(), getPendingRequests()])
    friends.value = f
    pendingRequests.value = p
  } catch (e) {
    console.error('加载好友数据失败:', e)
  }
}

// WebSocket 实时通知
const { connect, disconnect } = useWebSocket(`/ws/friend/${store.playerId}`, {
  onMessage(data) {
    if (data.type === 'FRIEND_REQUEST' || data.type === 'FRIEND_ACCEPTED') {
      loadData() // 收到通知时刷新列表
    }
  }
})

onMounted(() => {
  loadData()
  connect()
})

onUnmounted(() => {
  disconnect()
})

async function handleSendRequest() {
  if (!targetId.value) return
  msg.value = { text: '', type: '' }
  sending.value = true
  try {
    await sendFriendRequest(targetId.value)
    msg.value = { text: '好友请求已发送', type: 'success' }
    targetId.value = null
  } catch (e) {
    msg.value = { text: e.message, type: 'error' }
  } finally {
    sending.value = false
  }
}

async function handleAccept(id) {
  try {
    await acceptRequest(id)
    await loadData()
  } catch (e) {
    alert(e.message)
  }
}

async function handleReject(id) {
  try {
    await rejectRequest(id)
    await loadData()
  } catch (e) {
    alert(e.message)
  }
}
</script>
