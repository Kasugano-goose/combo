<template>
  <Layout v-if="isAuthRoute" />
  <router-view v-else />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Layout from './components/Layout.vue'

const route = useRoute()
const isAuthRoute = computed(() => route.meta.auth === true)
</script>
