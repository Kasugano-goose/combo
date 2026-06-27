<template>
  <div class="arena-body">
    <div class="arena-shell">
      <!-- 顶部状态条 -->
      <div class="arena-topbar">
        <div class="arena-title">
          <p class="eyebrow">ARENA · 实时竞技</p>
          <h1>竞技场 <span>#{{ sceneId }}</span></h1>
        </div>
        <div class="arena-topbar-right">
          <span class="arena-conn" :class="connected ? 'on' : 'off'">
            <span class="dot"></span>{{ connected ? '已连接' : '连接中…' }}
          </span>
          <button class="arena-exit" @click="handleExit">退出场景</button>
        </div>
      </div>

      <!-- VS 对战 HUD -->
      <div class="arena-vs">
        <div class="vs-card me" :style="{ '--c': meColor }">
          <div class="vs-role" :style="{ background: meColor }">{{ meRole }}</div>
          <div class="vs-meta">
            <span class="vs-tag you">YOU</span>
            <h3>{{ me?.playerName || '你' }}</h3>
            <div class="vs-coord">
              <span>X <b>{{ me ? Math.round(me.x) : '–' }}</b></span>
              <span>Y <b>{{ me ? Math.round(me.y) : '–' }}</b></span>
              <span class="vs-dir">{{ dirLabel(me?.direction) }}</span>
            </div>
          </div>
        </div>

        <div class="vs-mid"><span>VS</span></div>

        <div class="vs-card opp" :style="{ '--c': oppColor }">
          <div class="vs-meta right">
            <span class="vs-tag">对手</span>
            <h3>{{ opponent?.playerName || '等待对手…' }}</h3>
            <div class="vs-coord">
              <span class="vs-dir">{{ dirLabel(opponent?.direction) }}</span>
              <span>X <b>{{ opponent ? Math.round(opponent.x) : '–' }}</b></span>
              <span>Y <b>{{ opponent ? Math.round(opponent.y) : '–' }}</b></span>
            </div>
          </div>
          <div class="vs-role" :style="{ background: oppColor }">{{ oppRole }}</div>
        </div>
      </div>

      <!-- 舞台 -->
      <div class="arena-stage">
        <canvas ref="canvasRef" id="gameCanvas" width="800" height="600"></canvas>
      </div>

      <!-- 操作提示 -->
      <div class="arena-hint">
        <span class="key-group">
          <kbd>W</kbd><kbd>A</kbd><kbd>S</kbd><kbd>D</kbd>
          <span class="kbd-or">或</span>
          <kbd>↑</kbd><kbd>↓</kbd><kbd>←</kbd><kbd>→</kbd> 移动
        </span>
        <span class="dotsep"></span>
        <span>松开按键停止</span>
        <span class="dotsep"></span>
        <span>服务端 100ms 权威同步</span>
      </div>
    </div>

    <!-- 退出确认 -->
    <div v-if="showExitConfirm" class="arena-overlay" @click.self="cancelExit">
      <div class="arena-overlay-card">
        <div class="overlay-icon">🚪</div>
        <h2>退出场景？</h2>
        <p>退出后本局将结束，对手也会收到通知。</p>
        <div class="arena-confirm-actions">
          <button class="arena-cancel-btn" @click="cancelExit">取消</button>
          <button class="arena-exit-btn" @click="confirmExit">确定退出</button>
        </div>
      </div>
    </div>

    <!-- 场景结束遮罩 -->
    <div v-if="overlay.show" class="arena-overlay">
      <div class="arena-overlay-card">
        <div class="overlay-icon">⚔</div>
        <h2>{{ overlay.title }}</h2>
        <p>{{ overlay.message }}</p>
        <router-link to="/home" class="arena-home-btn">返回主页</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePlayerStore } from '../stores/player'
import { useWebSocket } from '../composables/useWebSocket'

const route = useRoute()
const router = useRouter()
const store = usePlayerStore()
const playerId = store.playerId
const sceneId = route.query.sceneId

const canvasRef = ref(null)
const playerPositions = ref([])
const overlay = reactive({ show: false, title: '', message: '' })
const showExitConfirm = ref(false)
let exiting = false // 主动退出标记：避免退出时 onClose 再弹「连接断开」

let sceneWidth = 800
let sceneHeight = 600
const roleColors = { 1: '#ff3b3b', 2: '#00f0ff', 3: '#39ff14', 4: '#ffaa00', 5: '#a855f7' }
const roleNames = { 1: '战士', 2: '法师', 3: '刺客', 4: '坦克', 5: '辅助' }

// VS HUD 派生数据：区分「我方」与「对手」
const me = computed(() => playerPositions.value.find(p => p.playerId === playerId) || null)
const opponent = computed(() => playerPositions.value.find(p => p.playerId !== playerId) || null)
const meColor = computed(() => roleColors[me.value?.roleId] || '#2563eb')
const oppColor = computed(() => roleColors[opponent.value?.roleId] || '#dc2626')
const meRole = computed(() => roleNames[me.value?.roleId] || '?')
const oppRole = computed(() => roleNames[opponent.value?.roleId] || '?')
function dirLabel(d) {
  return ({ UP: '↑ 上', DOWN: '↓ 下', LEFT: '← 左', RIGHT: '→ 右', STOP: '■ 静止' })[d] || '· 待命'
}

const { connected, connect, disconnect, send } = useWebSocket(`/ws/scene/${sceneId}/${playerId}`, {
  onMessage(data) {
    if (data.type === 'SCENE_START') {
      sceneWidth = data.sceneWidth || 800
      sceneHeight = data.sceneHeight || 600
      const p1 = data.player1
      const p2 = data.player2
      playerPositions.value = [p1, p2]
      nextTick(() => drawScene())
    } else if (data.type === 'STATE') {
      const p1 = data.player1
      const p2 = data.player2
      playerPositions.value = [p1, p2]
      drawScene()
    } else if (data.type === 'SCENE_END') {
      overlay.title = '场景结束'
      overlay.message = data.reason || '游戏已结束'
      overlay.show = true
    }
  },
  onClose() {
    if (!overlay.show && !exiting) {
      overlay.title = '连接断开'
      overlay.message = '与场景服务器的连接已断开，场景已结束'
      overlay.show = true
    }
  },
  onError() {
    if (!overlay.show) {
      overlay.title = '连接失败'
      overlay.message = '无法连接到场景服务器'
      overlay.show = true
    }
  }
})

function drawScene() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')

  ctx.fillStyle = '#13131f'
  ctx.fillRect(0, 0, sceneWidth, sceneHeight)

  // 网格
  ctx.strokeStyle = 'rgba(0, 240, 255, 0.08)'
  ctx.lineWidth = 1
  for (let x = 0; x < sceneWidth; x += 40) {
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, sceneHeight); ctx.stroke()
  }
  for (let y = 0; y < sceneHeight; y += 40) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(sceneWidth, y); ctx.stroke()
  }

  // 玩家
  for (const p of playerPositions.value) {
    const isMe = p.playerId === playerId
    const color = roleColors[p.roleId] || '#00f0ff'
    const x = p.x
    const y = p.y

    // 发光效果
    if (isMe) {
      ctx.beginPath()
      ctx.arc(x, y, 26, 0, Math.PI * 2)
      ctx.fillStyle = color + '33'
      ctx.fill()

      ctx.beginPath()
      ctx.arc(x, y, 34, 0, Math.PI * 2)
      ctx.strokeStyle = color + '22'
      ctx.lineWidth = 2
      ctx.stroke()
    }

    // 玩家圆
    ctx.beginPath()
    ctx.arc(x, y, 16, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
    ctx.strokeStyle = 'rgba(255,255,255,0.8)'
    ctx.lineWidth = 2
    ctx.stroke()

    // 方向箭头
    const dirs = { UP: [0, -1], DOWN: [0, 1], LEFT: [-1, 0], RIGHT: [1, 0] }
    const d = dirs[p.direction]
    if (d) {
      ctx.beginPath()
      ctx.moveTo(x + d[0] * 20, y + d[1] * 20)
      ctx.lineTo(x + d[0] * 30, y + d[1] * 30)
      ctx.strokeStyle = '#fff'
      ctx.lineWidth = 3
      ctx.stroke()
    }

    // 名字
    ctx.fillStyle = '#fff'
    ctx.font = 'bold 12px "Noto Sans SC", sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(p.playerName + (isMe ? ' (你)' : ''), x, y - 26)
  }
}

function handleExit() {
  // 先弹确认，避免误触直接退出
  showExitConfirm.value = true
}

function confirmExit() {
  exiting = true
  showExitConfirm.value = false
  send({ type: 'EXIT' })
  setTimeout(() => router.push('/home'), 300)
}

function cancelExit() {
  showExitConfirm.value = false
}

// 物理键码 → 方向：方向键与 WASD 都支持，用 e.code 避免大小写/输入法影响
function codeToDir(code) {
  switch (code) {
    case 'ArrowUp': case 'KeyW': return 'UP'
    case 'ArrowDown': case 'KeyS': return 'DOWN'
    case 'ArrowLeft': case 'KeyA': return 'LEFT'
    case 'ArrowRight': case 'KeyD': return 'RIGHT'
    default: return null
  }
}

const pressedKeys = new Set()
function onKeyDown(e) {
  if (e.repeat) return // 长按不重复发送
  const dir = codeToDir(e.code)
  if (dir) {
    e.preventDefault()
    pressedKeys.add(e.code)
    send({ type: 'MOVE', direction: dir })
  }
}

function onKeyUp(e) {
  const dir = codeToDir(e.code)
  if (dir) {
    pressedKeys.delete(e.code)
    if (pressedKeys.size === 0) {
      send({ type: 'STOP' })
    } else {
      // 还有其他方向键按下，发送剩余方向
      const remainingCode = pressedKeys.values().next().value
      const remainingDir = codeToDir(remainingCode)
      if (remainingDir) send({ type: 'MOVE', direction: remainingDir })
    }
  }
}

onMounted(() => {
  // 缺少场景ID时直接回主页（守卫放在 onMounted，避免把脚本逻辑包进 else 块
  // 导致 handleExit 等函数被块级作用域隐藏、无法暴露给模板）
  if (!sceneId) {
    alert('缺少场景ID')
    router.replace('/home')
    return
  }
  connect()
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('keyup', onKeyUp)
})

onUnmounted(() => {
  disconnect()
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('keyup', onKeyUp)
})
</script>
