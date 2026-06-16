<template>
  <div class="register-page">
    <div class="brand-panel login-brand">
      <div>
        <div class="brand-mark">C</div>
        <p class="eyebrow">COMBO ARENA</p>
        <h1>欢迎回来</h1>
        <p class="brand-copy">登录你的竞技场账号，继续战斗。</p>
      </div>
      <div class="rank-strip">
        <span>BRONZE — 青铜</span>
        <span>SILVER — 白银</span>
        <span>GOLD — 黄金</span>
        <span>DIAMOND — 钻石</span>
      </div>
    </div>

    <div class="form-panel">
      <div class="form-heading">
        <p class="eyebrow">PLAYER LOGIN</p>
        <h2>登录</h2>
        <p>输入账号ID和密码</p>
      </div>

      <form @submit.prevent="handleLogin">
        <label>账号 ID</label>
        <input v-model.number="form.id" type="number" placeholder="你的数字ID" />
        <p class="field-error">{{ errors.id }}</p>

        <label>密码</label>
        <input v-model="form.password" type="password" placeholder="输入密码" />
        <p class="field-error">{{ errors.password }}</p>

        <button type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div v-if="message.text" :class="['message', message.type]">{{ message.text }}</div>

      <router-link to="/" class="text-link">没有账号，去注册</router-link>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '../stores/player'

const router = useRouter()
const store = usePlayerStore()
const loading = ref(false)
const message = reactive({ text: '', type: '' })

const form = reactive({ id: null, password: '' })
const errors = reactive({ id: '', password: '' })

function validate() {
  let valid = true
  errors.id = ''
  errors.password = ''

  if (!form.id || form.id <= 0 || !Number.isInteger(form.id)) {
    errors.id = '请输入有效的账号ID'; valid = false
  }
  if (!form.password) {
    errors.password = '密码不能为空'; valid = false
  }
  return valid
}

async function handleLogin() {
  message.text = ''
  if (!validate()) {
    message.text = '请先修正表单中的提示'
    message.type = 'error'
    return
  }

  loading.value = true
  try {
    await store.login({ id: form.id, password: form.password })
    router.push('/home')
  } catch (e) {
    message.text = e.message
    message.type = 'error'
  } finally {
    loading.value = false
  }
}
</script>
