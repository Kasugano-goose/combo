<template>
  <div class="home-body">
    <div class="home-page">
      <div class="home-header">
        <div>
          <p class="eyebrow">PLAYER DASHBOARD</p>
          <h1>玩家主页</h1>
        </div>
        <div class="header-actions">
          <span style="color:var(--muted); font-size:14px;">欢迎回来，{{ player.username }}</span>
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
          <p class="card-label">当前段位</p>
          <strong>{{ rankName }}</strong>
          <span>积分 {{ player.rankScore }}</span>
        </div>

        <div class="profile-card">
          <p class="card-label">账户余额</p>
          <strong>¥{{ player.balance }}</strong>
          <span>可用于购买道具</span>
        </div>

        <div class="profile-card">
          <p class="card-label">账号状态</p>
          <strong :style="{ color: player.status === 'NORMAL' ? 'var(--success)' : 'var(--error)' }">
            {{ player.status === 'NORMAL' ? '正常' : '已停用' }}
          </strong>
          <span>系统状态</span>
        </div>
      </div>

      <div class="quick-actions">
        <button class="action-button" @click="showProfileModal = true">编辑资料</button>
        <button class="action-button" @click="showPasswordModal = true">修改密码</button>
        <button class="action-button" @click="showRoleModal = true">选择角色</button>
        <router-link to="/match" class="action-button" style="text-decoration:none; display:inline-flex; align-items:center; justify-content:center;">
          开始匹配
        </router-link>
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
          <div v-if="modalMsg.text" :class="['message', modalMsg.type]" style="margin-top:14px;">{{ modalMsg.text }}</div>
          <div class="modal-actions">
            <button class="secondary-button" @click="showProfileModal = false">取消</button>
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
          <div v-if="modalMsg.text" :class="['message', modalMsg.type]" style="margin-top:14px;">{{ modalMsg.text }}</div>
          <div class="modal-actions">
            <button class="secondary-button" @click="showPasswordModal = false">取消</button>
            <button class="action-button" @click="handleChangePassword">确认</button>
          </div>
        </div>
      </div>

      <!-- 选择角色弹窗 -->
      <div v-if="showRoleModal" class="modal-overlay" @click.self="showRoleModal = false">
        <div class="modal-content">
          <h2>选择角色</h2>
          <div class="role-grid">
            <div v-for="r in roles" :key="r.id"
              :class="['role-option', { selected: selectedRole === r.id }]"
              @click="selectedRole = r.id"
            >
              <div class="role-icon">{{ r.icon }}</div>
              <div class="role-name">{{ r.name }}</div>
            </div>
          </div>
          <div v-if="modalMsg.text" :class="['message', modalMsg.type]" style="margin-top:14px;">{{ modalMsg.text }}</div>
          <div class="modal-actions">
            <button class="secondary-button" @click="showRoleModal = false">取消</button>
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
  { id: 1, name: '战士', icon: '战' },
  { id: 2, name: '法师', icon: '法' },
  { id: 3, name: '刺客', icon: '刺' },
  { id: 4, name: '射手', icon: '射' },
  { id: 5, name: '辅助', icon: '辅' }
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
