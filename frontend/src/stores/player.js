import { defineStore } from 'pinia'
import { getMe, login as apiLogin, logout as apiLogout, register as apiRegister } from '../api/player'

export const usePlayerStore = defineStore('player', {
  state: () => ({
    player: null,
    initialized: false
  }),

  getters: {
    isLoggedIn: (state) => state.player !== null,
    playerId: (state) => state.player?.id ?? null
  },

  actions: {
    async fetchPlayer() {
      try {
        this.player = await getMe()
      } catch {
        this.player = null
      } finally {
        this.initialized = true
      }
    },

    async login(data) {
      this.player = await apiLogin(data)
    },

    async register(data) {
      return await apiRegister(data)
    },

    async logout() {
      try {
        await apiLogout()
      } finally {
        this.player = null
      }
    },

    updatePlayer(partial) {
      if (this.player) {
        Object.assign(this.player, partial)
      }
    }
  }
})
