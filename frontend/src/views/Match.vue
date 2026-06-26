<template>
  <div class="home-body">
    <div class="home-page">
      <div class="home-header">
        <h1>匹配</h1>
        <div class="header-actions">
          <router-link to="/home" class="secondary-button">返回主页</router-link>
        </div>
      </div>

      <div class="match-container">
        <div class="profile-card match-player-info">
          <p class="card-label">我的信息</p>
          <h2>{{ player.username }}</h2>
          <div class="match-stat"><span>段位</span><strong>{{ rankName }}</strong></div>
          <div class="match-stat"><span>积分</span><strong>{{ player.rankScore }}</strong></div>
        </div>

        <div class="profile-card match-control">
          <!-- 空闲状态 -->
          <div v-if="state === 'idle'" class="match-panel">
            <div class="match-icon">🎮</div>
            <p class="match-hint">准备好了吗？</p>
            <button class="match-button" @click="handleJoin" :disabled="!connected">
              开始匹配
            </button>
            <p v-if="!connected" class="match-hint" style="color:var(--error);">
              正在连接匹配服务...
            </p>
          </div>

          <!-- 等待中 -->
          <div v-if="state === 'waiting'" class="match-panel">
            <div class="match-icon spinning">⚔️</div>
            <p class="match-hint">正在寻找对手...</p>
            <div class="match-stats">
              <div class="match-stat-item">
                <span>在线等待</span>
                <strong>{{ poolSize }}</strong>
              </div>
              <div class="match-stat-item">
                <span>已等待</span>
                <strong>{{ elapsed }}s</strong>
              </div>
              <div class="match-stat-item">
                <span>剩余时间</span>
                <strong>{{ remaining }}s</strong>
              </div>
            </div>
            <button class="match-button match-cancel secondary-button" @click="handleLeave"
              style="color:var(--error); border-color:var(--error);">
              取消匹配
            </button>
          </div>

          <!-- 匹配成功 -->
          <div v-if="state === 'found'" class="match-panel">
            <div class="match-icon">🎉</div>
            <p class="match-hint">匹配成功！</p>
            <div class="match-result-info">
              <p><strong>对手：</strong>{{ opponent.name }}（ID: {{ opponent.id }}）</p>
              <p><strong>对手积分：</strong>{{ opponent.score }}</p>
            </div>
            <div class="confirm-status">{{ confirmText }}</div>
            <button class="match-button" @click="handleConfirm"
              :disabled="confirmed">
              {{ confirmed ? '等待对方确认...' : '确认进入场景' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '../stores/player'
import { useWebSocket } from '../composables/useWebSocket'
import { joinMatch, leaveMatch, getMatchStatus, confirmMatch } from '../api/match'

const router = useRouter()
const store = usePlayerStore()
const player = ref(store.player || {})

const rankNames = {
  BRONZE: '青铜', SILVER: '白银', GOLD: '黄金',
  PLATINUM: '铂金', DIAMOND: '钻石', MASTER: '大师'
}
const rankName = rankNames[player.value.rank] || '青铜'

const state = ref('idle') // idle | waiting | found
const poolSize = ref(0)
const elapsed = ref(0)
const remaining = ref(60)
const confirmed = ref(false)
const confirmText = ref('等待双方确认...')
const opponent = ref({ id: 0, name: '', score: 0 })

let pollTimer = null
let joinTime = 0

// 通用状态切换：清理所有定时器
function resetToIdle() {
  state.value = 'idle'
  confirmed.value = false
  opponent.value = { id: 0, name: '', score: 0 }
  stopPolling()
}

// 切换到匹配成功状态（统一入口，避免遗漏清理）
function switchToFound(opponentInfo, yourConfirmed = false, opponentConfirmed = false) {
  if (state.value === 'found') {
    console.log('[Match] 已在 found 状态，忽略重复切换')
    return
  }
  state.value = 'found'
  opponent.value = opponentInfo
  confirmed.value = yourConfirmed
  confirmText.value = opponentConfirmed ? '对方已确认，等待你的确认...' : '等待双方确认...'
  stopPolling()
  console.log('[Match] 切换到 found 状态，对手:', opponentInfo)
}

// WebSocket 接收匹配通知
const { connected, connect, disconnect } = useWebSocket(`/ws/friend/${store.playerId}`, {
  onMessage(data) {
    console.log('[Match] 收到 WebSocket 消息:', data.type, data)
    if (data.type === 'MATCHED') {
      switchToFound(
        { id: data.opponentId, name: data.opponentName, score: data.opponentScore },
        false, false
      )
    } else if (data.type === 'OPPONENT_CONFIRMED') {
      if (state.value === 'found') {
        confirmText.value = '对方已确认，等待你的确认...'
      }
    } else if (data.type === 'SCENE_READY') {
      stopPolling()
      router.push(`/scene?sceneId=${data.sceneId}`)
    } else if (data.type === 'MATCH_TIMEOUT') {
      console.log('[Match] 收到匹配超时通知')
      resetToIdle()
    } else if (data.type === 'CONFIRM_TIMEOUT') {
      console.log('[Match] 收到确认超时通知')
      resetToIdle()
      alert(data.message || '确认超时，匹配已取消，请重新匹配')
    }
  },
  // 重连后主动查询匹配状态（补偿断线期间可能丢失的通知）
  onOpen() {
    console.log('[Match] WebSocket 已连接，当前状态:', state.value)
    if (state.value === 'waiting' || state.value === 'found') {
      checkMatchStatusFallback()
    }
  }
})

// 重连后主动查询匹配状态（兜底）
async function checkMatchStatusFallback() {
  try {
    const status = await getMatchStatus(store.playerId)
    console.log('[Match] 重连后查询状态:', status)
    applyMatchStatus(status)
  } catch (e) {
    console.error('[Match] 重连后查询状态失败:', e)
  }
}

// 根据 /match/status 响应更新状态（统一逻辑，轮询和重连共用）
function applyMatchStatus(status) {
  // 发现有待确认的匹配对
  if (status.hasPendingMatch) {
    if (state.value !== 'found') {
      console.warn('[Match] 通过轮询/重连发现匹配结果', status)
      switchToFound(
        { id: status.opponentId, name: status.opponentName, score: status.opponentScore },
        status.yourConfirmed || false,
        status.opponentConfirmed || false
      )
    }
    return
  }

  // 不在池中也没有匹配对 → 超时或被取消
  if (!status.inPool && !status.hasPendingMatch) {
    if (state.value === 'waiting') {
      console.warn('[Match] 匹配已超时或被取消')
      resetToIdle()
    }
    return
  }

  // 在池中，更新等待信息
  if (status.inPool) {
    poolSize.value = status.poolSize
  }
}

function startPolling() {
  joinTime = Date.now()
  pollTimer = setInterval(async () => {
    // 如果已经不在 waiting 状态，停止轮询
    if (state.value !== 'waiting') {
      stopPolling()
      return
    }
    try {
      const status = await getMatchStatus(store.playerId)
      console.log('[轮询] /match/status 返回:', JSON.stringify(status))
      elapsed.value = Math.floor((Date.now() - joinTime) / 1000)
      remaining.value = Math.max(0, Math.floor((status.timeout - (Date.now() - joinTime)) / 1000))
      applyMatchStatus(status)
    } catch (e) {
      console.error('[轮询] /match/status 请求失败:', e.message)
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function handleJoin() {
  try {
    const status = await joinMatch()
    console.log('[Match] 加入匹配池成功:', JSON.stringify(status))
    poolSize.value = status.poolSize
    state.value = 'waiting'
    startPolling()
  } catch (e) {
    console.error('[Match] 加入匹配池失败:', e.message)
    alert(e.message)
  }
}

async function handleLeave() {
  try {
    await leaveMatch()
    resetToIdle()
  } catch (e) {
    alert(e.message)
  }
}

async function handleConfirm() {
  try {
    await confirmMatch()
    confirmed.value = true
    confirmText.value = '已确认，等待对方...'
  } catch (e) {
    alert(e.message)
  }
}

onMounted(() => {
  connect()
})

onUnmounted(() => {
  disconnect()
  stopPolling()
})
</script>
