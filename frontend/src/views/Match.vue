<template>
  <div class="home-body">
    <div class="home-page">
      <div class="home-header">
        <div>
          <p class="eyebrow">MATCHMAKING</p>
          <h1>竞技匹配</h1>
        </div>
        <div class="header-actions">
          <span class="ws-status" v-if="connected">
            <span class="ws-dot connected"></span> 匹配服务已连接
          </span>
          <span class="ws-status" v-else>
            <span class="ws-dot disconnected"></span> 连接中...
          </span>
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
            <div class="match-icon">VS</div>
            <p class="match-hint">准备好了吗？寻找你的对手</p>
            <button class="match-button" @click="handleJoin" :disabled="!connected">
              开始匹配
            </button>
            <p v-if="!connected" class="match-hint" style="color:var(--error);">
              正在连接匹配服务...
            </p>
          </div>

          <!-- 等待中 -->
          <div v-if="state === 'waiting'" class="match-panel">
            <div class="match-icon spinning">◌</div>
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
            <button class="match-button match-cancel secondary-button" @click="handleLeave">
              取消匹配
            </button>
          </div>

          <!-- 匹配成功 -->
          <div v-if="state === 'found'" class="match-panel">
            <div class="match-icon">OK</div>
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
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '../stores/player'
import { useWebSocket } from '../composables/useWebSocket'
import { joinMatch, leaveMatch, getMatchStatus, confirmMatch } from '../api/match'

const router = useRouter()
const store = usePlayerStore()
const { player: storePlayer } = storeToRefs(store)
const player = computed(() => storePlayer.value || {})

const rankNames = {
  BRONZE: '青铜', SILVER: '白银', GOLD: '黄金',
  PLATINUM: '铂金', DIAMOND: '钻石', MASTER: '大师'
}
const rankName = computed(() => rankNames[player.value.rank] || '青铜')

const state = ref('idle')
const poolSize = ref(0)
const elapsed = ref(0)
const remaining = ref(60)
const confirmed = ref(false)
const confirmText = ref('等待双方确认...')
const opponent = ref({ id: 0, name: '', score: 0 })

let pollTimer = null
let joinTime = 0

function resetToIdle() {
  state.value = 'idle'
  confirmed.value = false
  opponent.value = { id: 0, name: '', score: 0 }
  stopPolling()
}

function switchToFound(opponentInfo, yourConfirmed = false, opponentConfirmed = false) {
  if (state.value === 'found') return
  state.value = 'found'
  opponent.value = opponentInfo
  confirmed.value = yourConfirmed
  confirmText.value = opponentConfirmed ? '对方已确认，等待你的确认...' : '等待双方确认...'
  stopPolling()
}

const { connected, connect, disconnect } = useWebSocket(`/ws/friend/${store.playerId}`, {
  onMessage(data) {
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
      resetToIdle()
    } else if (data.type === 'CONFIRM_TIMEOUT') {
      resetToIdle()
      alert(data.message || '确认超时，匹配已取消，请重新匹配')
    }
  },
  onOpen() {
    if (state.value === 'waiting' || state.value === 'found') {
      checkMatchStatusFallback()
    }
  }
})

async function checkMatchStatusFallback() {
  try {
    const status = await getMatchStatus(store.playerId)
    applyMatchStatus(status)
  } catch (e) {
    console.error('[Match] 重连后查询状态失败:', e)
  }
}

function applyMatchStatus(status) {
  if (status.hasPendingMatch) {
    if (state.value !== 'found') {
      switchToFound(
        { id: status.opponentId, name: status.opponentName, score: status.opponentScore },
        status.yourConfirmed || false,
        status.opponentConfirmed || false
      )
    }
    return
  }

  if (!status.inPool && !status.hasPendingMatch) {
    if (state.value === 'waiting') {
      resetToIdle()
    }
    return
  }

  if (status.inPool) {
    poolSize.value = status.poolSize
  }
}

function startPolling() {
  joinTime = Date.now()
  pollTimer = setInterval(async () => {
    if (state.value !== 'waiting') {
      stopPolling()
      return
    }
    try {
      const status = await getMatchStatus(store.playerId)
      elapsed.value = Math.floor((Date.now() - joinTime) / 1000)
      const timeoutMs = Number(status.timeout) || 60000
      remaining.value = Math.max(0, Math.floor((timeoutMs - (Date.now() - joinTime)) / 1000))
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
    poolSize.value = status.poolSize
    state.value = 'waiting'
    startPolling()
  } catch (e) {
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
