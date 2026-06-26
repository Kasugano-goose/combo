import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/players/': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/friends/': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/match/': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/scene/': {
        target: 'http://localhost:8090',
        changeOrigin: true
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
