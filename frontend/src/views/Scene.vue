<template>
  <div class="scene-body">
    <div class="scene-page">
      <div class="scene-header">
        <h1>竞技场</h1>
        <button class="secondary-button" style="color:#fff; border-color:rgba(255,255,255,0.3);"
          @click="handleExit">
          退出场景
        </button>
      </div>

      <div class="scene-layout">
        <canvas ref="canvasRef" id="gameCanvas" width="800" height="600"></canvas>

        <div class="scene-info-panel">
          <div v-for="p in playerPositions" :key="p.playerId" class="scene-player-card">
            <h3>{{ p.playerName }} {{ p.playerId === playerId ? '(你)' : '' }}</h3>
            <div class="coord-display">
              <span>X: <strong>{{ Math.round(p.x) }}</strong></span>
              <span>Y: <strong>{{ Math.round(p.y) }}</strong></span>
              <span>方向: <strong>{{ p.direction }}</strong></span>
            </div>
          </div>

          <div class="scene-controls-hint">
            <h3>操作说明</h3>
            <p>⬆⬇⬅➡ 方向键移动<br>松开方向键停止</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 场景结束遮罩 -->
    <div v-if="overlay.show" class="scene-overlay">
      <div class="overlay-content">
        <h2>{{ overlay.title }}</h2>
        <p>{{ overlay.message }}</p>
        <router-link to="/home" class="match-button btn-lg"
          style="display:inline-block; text-decoration:none; line-height:50px;">
          返回主页
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
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

let sceneWidth = 800
let sceneHeight = 600
const roleColors = { 1: '#e74c3c', 2: '#3498db', 3: '#2ecc71', 4: '#e67e22', 5: '#9b59b6' }

if (!sceneId) {
  alert('缺少场景ID')
  router.push('/home')
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
    if (!overlay.show) {
      overlay.title = '连接断开'
      overlay.message = '与场景服务器的连接已断开'
      overlay.show = true
    }
  },
  onError() {
    overlay.title = '连接失败'
    overlay.message = '无法连接到场景服务器'
    overlay.show = true
  }
})

function drawScene() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')

  ctx.fillStyle = '#1a1a2e'
  ctx.fillRect(0, 0, sceneWidth, sceneHeight)

  // 网格
  ctx.strokeStyle = 'rgba(255,255,255,0.06)'
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
    const color = roleColors[p.roleId] || '#0f8b8d'
    const x = p.x
    const y = p.y

    // 发光效果
    if (isMe) {
      ctx.beginPath()
      ctx.arc(x, y, 24, 0, Math.PI * 2)
      ctx.fillStyle = color + '33'
      ctx.fill()
    }

    // 玩家圆
    ctx.beginPath()
    ctx.arc(x, y, 16, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
    ctx.strokeStyle = 'rgba(255,255,255,0.6)'
    ctx.lineWidth = 2
    ctx.stroke()

    // 方向箭头
    const dirs = { UP: [0, -1], DOWN: [0, 1], LEFT: [-1, 0], RIGHT: [1, 0] }
    const d = dirs[p.direction]
    if (d) {
      ctx.beginPath()
      ctx.moveTo(x + d[0] * 20, y + d[1] * 20)
      ctx.lineTo(x + d[0] * 28, y + d[1] * 28)
      ctx.strokeStyle = '#fff'
      ctx.lineWidth = 3
      ctx.stroke()
    }

    // 名字
    ctx.fillStyle = '#fff'
    ctx.font = '12px "Microsoft YaHei", sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(p.playerName + (isMe ? ' (你)' : ''), x, y - 24)
  }
}

function handleExit() {
  send({ type: 'EXIT' })
  setTimeout(() => router.push('/home'), 500)
}

// 键盘事件
function onKeyDown(e) {
  const dirMap = { ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT' }
  const dir = dirMap[e.key]
  if (dir) {
    e.preventDefault()
    send({ type: 'MOVE', direction: dir })
  }
}

function onKeyUp(e) {
  const dirMap = { ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT' }
  if (dirMap[e.key]) {
    // 检查是否还有其他方向键按下
    // 简单处理：松开就停止
    send({ type: 'STOP' })
  }
}

onMounted(() => {
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
