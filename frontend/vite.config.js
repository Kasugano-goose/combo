import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 浏览器页面导航（Accept: text/html）跳过代理，让 Vite SPA 兜底返回 index.html
const bypassIfPageNav = (req) => {
  if (req.headers.accept && req.headers.accept.includes('text/html')) {
    return '/index.html'
  }
  return undefined
}

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '^/players(/.*)?$': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        bypass: bypassIfPageNav
      },
      '^/friends(/.*)?$': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        bypass: bypassIfPageNav
      },
      '^/match(/.*)?$': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        bypass: bypassIfPageNav
      },
      '^/scene(/.*)?$': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        bypass: bypassIfPageNav
      },
      '^/chat(/.*)?$': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        bypass: bypassIfPageNav
      },
      '/ws': {
        target: 'ws://localhost:8090',
        ws: true,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true
  }
})
