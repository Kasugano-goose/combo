<template>
  <div class="home-body">
    <div class="home-page">
      <div class="home-header">
        <h1>玩家主页</h1>
        <div class="header-actions">
          <div class="nav-buttons">
            <router-link to="/friends" class="btn-outline secondary-button">好友</router-link>
            <router-link to="/match" class="btn-outline secondary-button">匹配</router-link>
            <router-link to="/chat" class="btn-outline secondary-button">聊天</router-link>
          </div>
          <button class="logout-btn secondary-button" @click="handleLogout">退出登录</button>
        </div>
      </div>

      <div class="profile-layout">
        <div class="profile-card main-profile">
          <p class="card-label">玩家信息</p>
          <h2>{{ player.username }}</h2>
          <div class="player-id-badge">
            <span>账号 ID</span>
            <strong>{{ player.id }}</strong>
            <small>（告诉好友这个ID来添加你）</small>
          </div>
          <dl>
            <div><dt>真实姓名</dt><dd>{{ player.realName }}</dd></div>
            <div><dt>手机号</dt><dd>{{ player.phone }}</dd></div>
            <div><dt>身份证号</dt><dd>{{ player.idCard }}</dd></div>
          </dl>
        </div>

        <div class="profile-card">
          <p class="card-label">段位</p>
          <strong>{{ rankName }}</strong>
          <span>积分 {{ player.rankScore }}</span>
        </div>

        <div class="profile-card">
          <p class="card-label">余额</p>
          <strong>¥{{ player.balance }}</strong>
          <span>账户余额</span>
        </div>

        <div class="profile-card">
          <p class="card-label">状态</p>
          <strong>{{ player.status === 'NORMAL' ? '正常' : '已停用' }}</strong>
          <span>账号状态</span>
        </div>
      </div>

      <!-- 功能入口 -->
      <div style="margin-top: 24px; display: flex; gap: 12px; flex-wrap: wrap;">
        <button class="action-button" style="width:auto; margin-top:0;" @click="showProfileModal = true">编辑资料</button>
        <button class="action-button" style="width:auto; margin-top:0;" @click="showPasswordModal = true">修改密码</button>
        <button class="action-button" style="width:auto; margin-top:0;" @click="showRoleModal = true">选择角色</button>
      </div>

      <!-- 编辑资料弹窗 -->
      <div v-if="showProfileModal" class="modal-overlay" @click.self="showProfileModal = false">
        <div class="modal-content">
          <h2>编辑资料</h2>
          <label>用户名</label>
          <input v-model="profileForm.username" />
          <label>真实姓名</label>
          <input v-model="profileForm.realName" />
          <label>手机号</label>
          <input v-model="profileForm.phone" />
          <label>身份证号</label>
          <input v-model="profileForm.idCard" />
          <div v-if="modalMsg.text" :class="['message', modalMsg.type]" style="margin-top:12px;">{{ modalMsg.text }}</div>
          <div class="modal-actions">
            <button class="secondary-button" @click="showProfileModal = false" style="background:transparent; color:var(--accent-dark);">取消</button>
            <button class="action-button" @click="handleUpdateProfile">保存</button>
          </div>
        </div>
      </div>

      <!-- 修改密码弹窗 -->
      <div v-if="showPasswordModal" class="modal-overlay" @click.self="showPasswordModal = false">
        <div class="modal-content">
          <h2>修改密码</h2>
          <label>旧密码</label>
          <input v-model="passwordForm.oldPassword" type="password" />
          <label>新密码</label>
          <input v-model="passwordForm.newPassword" type="password" />
          <div v-if="modalMsg.text" :class="['message', modalMsg.type]" style="margin-top:12px;">{{ modalMsg.text }}</div>
          <div class="modal-actions">
            <button class="secondary-button" @click="showPasswordModal = false" style="background:transparent; color:var(--accent-dark);">取消</button>
            <button class="action-button" @click="handleChangePassword">确认</button>
          </div>
        </div>
      </div>

      <!-- 选择角色弹窗 -->
      <div v-if="showRoleModal" class="modal-overlay" @click.self="showRoleModal = false">
        <div class="modal-content">
          <h2>选择角色</h2>
          <div style="display:grid; grid-template-columns: repeat(3, 1fr); gap:12px; margin: 16px 0;">
            <div v-for="r in roles" :key="r.id"
              :style="{
                padding:'16px', border: selectedRole === r.id ? '2px solid var(--accent)' : '1px solid var(--line)',
                borderRadius:'8px', textAlign:'center', cursor:'pointer',
                background: selectedRole === r.id ? 'rgba(15,139,141,0.08)' : '#fff'
              }"
              @click="selectedRole = r.id"
            >
              <div style="font-size:28px;">{{ r.icon }}</div>
              <div style="font-weight:700; margin-top:8px;">{{ r.name }}</div>
            </div>
          </div>
          <div v-if="modalMsg.text" :class="['message', modalMsg.type]">{{ modalMsg.text }}</div>
          <div class="modal-actions">
            <button class="secondary-button" @click="showRoleModal = false" style="background:transparent; color:var(--accent-dark);">取消</button>
            <button class="action-button" @click="handleSelectRole">确认选择</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '../stores/player'
import { updateProfile, changePassword, selectRole } from '../api/player'

const router = useRouter()
const store = usePlayerStore()
const player = ref(store.player || {})

const rankNames = {
  BRONZE: '青铜', SILVER: '白银', GOLD: '黄金',
  PLATINUM: '铂金', DIAMOND: '钻石', MASTER: '大师'
}

const rankName = rankNames[player.value.rank] || player.value.rank || '青铜'

const roles = [
  { id: 1, name: '战士', icon: '⚔️' },
  { id: 2, name: '法师', icon: '🔮' },
  { id: 3, name: '刺客', icon: '🗡️' },
  { id: 4, name: '射手', icon: '🏹' },
  { id: 5, name: '辅助', icon: '🛡️' }
]

const showProfileModal = ref(false)
const showPasswordModal = ref(false)
const showRoleModal = ref(false)
const selectedRole = ref(player.value.selectedRoleId || 1)
const modalMsg = reactive({ text: '', type: '' })

const profileForm = reactive({
  username: player.value.username || '',
  realName: player.value.realName || '',
  phone: player.value.phone || '',
  idCard: player.value.idCard || ''
})

const passwordForm = reactive({ oldPassword: '', newPassword: '' })

async function handleUpdateProfile() {
  modalMsg.text = ''
  try {
    const updated = await updateProfile(profileForm)
    store.updatePlayer(updated)
    Object.assign(player.value, updated)
    modalMsg.text = '资料更新成功'
    modalMsg.type = 'success'
    setTimeout(() => { showProfileModal.value = false; modalMsg.text = '' }, 1000)
  } catch (e) {
    modalMsg.text = e.message
    modalMsg.type = 'error'
  }
}

async function handleChangePassword() {
  modalMsg.text = ''
  try {
    await changePassword(passwordForm)
    modalMsg.text = '密码修改成功'
    modalMsg.type = 'success'
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    setTimeout(() => { showPasswordModal.value = false; modalMsg.text = '' }, 1000)
  } catch (e) {
    modalMsg.text = e.message
    modalMsg.type = 'error'
  }
}

async function handleSelectRole() {
  modalMsg.text = ''
  try {
    const updated = await selectRole(selectedRole.value)
    store.updatePlayer(updated)
    Object.assign(player.value, updated)
    modalMsg.text = '角色选择成功'
    modalMsg.type = 'success'
    setTimeout(() => { showRoleModal.value = false; modalMsg.text = '' }, 1000)
  } catch (e) {
    modalMsg.text = e.message
    modalMsg.type = 'error'
  }
}

async function handleLogout() {
  await store.logout()
  router.push('/login')
}
</script>
