<template>
  <div class="register-page">
    <div class="brand-panel">
      <div>
        <div class="brand-mark">C</div>
        <p class="eyebrow">COMBO ARENA</p>
        <h1>网吧竞技<br>对战平台</h1>
        <p class="brand-copy">注册账号，加入竞技场，与好友实时对战。</p>
      </div>
      <div class="station-visual">
        <div class="screen"><span></span><span></span><span></span></div>
        <div class="keyboard"></div>
        <div class="glow-line"></div>
      </div>
    </div>

    <div class="form-panel">
      <div class="form-heading">
        <p class="eyebrow">PLAYER REGISTRATION</p>
        <h2>创建账号</h2>
        <p>填写以下信息完成注册</p>
      </div>

      <form @submit.prevent="handleRegister">
        <label>账号 ID</label>
        <input v-model.number="form.id" type="number" placeholder="自定义数字ID" />
        <p class="field-error">{{ errors.id }}</p>

        <label>密码</label>
        <input v-model="form.password" type="password" placeholder="4-32位密码" />
        <p class="field-error">{{ errors.password }}</p>

        <label>用户名</label>
        <input v-model="form.username" placeholder="游戏内显示名称" />
        <p class="field-error">{{ errors.username }}</p>

        <label>真实姓名</label>
        <input v-model="form.realName" placeholder="实名认证" />
        <p class="field-error">{{ errors.realName }}</p>

        <label>手机号</label>
        <input v-model="form.phone" placeholder="11位手机号" />
        <p class="field-error">{{ errors.phone }}</p>

        <label>身份证号</label>
        <input v-model="form.idCard" placeholder="18位身份证号" />
        <p class="field-error">{{ errors.idCard }}</p>

        <button type="submit" :disabled="loading">
          {{ loading ? '提交中...' : '创建账号' }}
        </button>
      </form>

      <div v-if="message.text" :class="['message', message.type]">{{ message.text }}</div>

      <router-link to="/login" class="text-link">已有账号，去登录</router-link>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { usePlayerStore } from '../stores/player'

const store = usePlayerStore()
const loading = ref(false)
const message = reactive({ text: '', type: '' })

const form = reactive({
  id: null, password: '', username: '',
  realName: '', phone: '', idCard: ''
})

const errors = reactive({
  id: '', password: '', username: '',
  realName: '', phone: '', idCard: ''
})

const rankNames = {
  BRONZE: '青铜', SILVER: '白银', GOLD: '黄金',
  PLATINUM: '铂金', DIAMOND: '钻石', MASTER: '大师'
}

function validate() {
  let valid = true
  Object.keys(errors).forEach(k => errors[k] = '')

  if (!form.id || form.id <= 0 || !Number.isInteger(form.id)) {
    errors.id = '账号ID必须为正整数'; valid = false
  }
  if (!form.password || form.password.length < 4) {
    errors.password = '密码长度不能少于4位'; valid = false
  }
  if (!form.username) {
    errors.username = '用户名不能为空'; valid = false
  }
  if (!form.realName) {
    errors.realName = '真实姓名不能为空'; valid = false
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    errors.phone = '请输入正确的11位手机号'; valid = false
  }
  if (!form.idCard) {
    errors.idCard = '身份证号不能为空'; valid = false
  }
  return valid
}

async function handleRegister() {
  message.text = ''
  if (!validate()) {
    message.text = '请先修正表单中的提示'
    message.type = 'error'
    return
  }

  loading.value = true
  try {
    const data = await store.register({ ...form })
    const rank = rankNames[data.rank] || data.rank
    message.text = `注册成功，账号ID ${data.id}（${data.username}），初始段位 ${rank}，积分 ${data.rankScore}`
    message.type = 'success'
  } catch (e) {
    message.text = e.message
    message.type = 'error'
  } finally {
    loading.value = false
  }
}
</script>
