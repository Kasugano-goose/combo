import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/players': 'http://localhost:8090',
      '/friends': 'http://localhost:8090',
      '/match': 'http://localhost:8090',
      '/scene': 'http://localhost:8090',
      '/ws': {
        target: 'ws://localhost:8090',
        ws: true
      }
    }
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true
  }
})
