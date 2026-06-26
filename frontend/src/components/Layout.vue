<template>
  <div class="app-layout">
    <aside class="sidebar">
      <router-link to="/home" class="sidebar-logo">
        <div class="sidebar-logo-mark">C</div>
        <div class="sidebar-logo-text">COMBO</div>
      </router-link>

      <nav class="sidebar-nav">
        <router-link to="/home" class="sidebar-link">
          <span class="sidebar-link-indicator"></span>
          <span>主页</span>
        </router-link>
        <router-link to="/friends" class="sidebar-link">
          <span class="sidebar-link-indicator"></span>
          <span>好友</span>
        </router-link>
        <router-link to="/match" class="sidebar-link">
          <span class="sidebar-link-indicator"></span>
          <span>匹配</span>
        </router-link>
        <router-link to="/chat" class="sidebar-link">
          <span class="sidebar-link-indicator"></span>
          <span>聊天</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <button class="logout-btn secondary-button" style="width:100%;" @click="handleLogout">
          退出登录
        </button>
      </div>
    </aside>

    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { usePlayerStore } from '../stores/player'

const router = useRouter()
const store = usePlayerStore()

async function handleLogout() {
  await store.logout()
  router.push('/login')
}
</script>

<style scoped>
.sidebar-link {
    position: relative;
    padding-left: 18px;
}
.sidebar-link-indicator {
    position: absolute;
    left: 5px;
    top: 50%;
    transform: translateY(-50%);
    width: 2px;
    height: 0;
    background: var(--text);
    border-radius: 1px;
    transition: height 0.15s ease;
}
.sidebar-link.router-link-active .sidebar-link-indicator {
    height: 16px;
}
</style>
