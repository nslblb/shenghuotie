<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import NavBar from './components/NavBar.vue'
import Sidebar from './components/Sidebar.vue'
import SliderCaptcha from './components/SliderCaptcha.vue'
import AiFloatingBall from './components/AiFloatingBall.vue'
import { computed, watch, onMounted, ref } from 'vue'
import { adminState } from '@/stores/admin'
import { getPublicConfigs } from '@/api/user'
import { initSpeedDetector, captchaPassed, captchaFailed } from '@/utils/speedDetector'

const route = useRoute()
const cachedViews = computed(() => ['home'])
const hideNav = computed(() => ['login', 'register', 'reset-password'].includes(route.name as string))
const showAdmin = computed(() => adminState.showAdminPanel)
const showSliderCaptcha = ref(false)

watch(() => route.path, (newPath) => {
  if (!newPath.startsWith('/admin')) {
    adminState.showAdminPanel = false
  }
})

onMounted(() => {
  getPublicConfigs().then(res => {
    const data = res.data.data
    if (data.siteName) {
      document.title = data.siteName
    }
  }).catch(() => {})

  initSpeedDetector(() => {
    showSliderCaptcha.value = true
  })
})

function handleCaptchaSuccess() {
  showSliderCaptcha.value = false
  captchaPassed()
}

function handleCaptchaFail() {
  captchaFailed()
}

function handleCaptchaClose() {
  showSliderCaptcha.value = false
}
</script>

<template>
  <div class="app-container">
    <NavBar v-if="!hideNav" />
    <Sidebar v-if="!hideNav" />
    <main class="main-content" :class="{ 'no-nav': hideNav, 'admin-active': showAdmin }">
      <RouterView v-slot="{ Component }">
        <keep-alive :include="cachedViews">
          <component :is="Component" :key="route.fullPath" />
        </keep-alive>
      </RouterView>
    </main>
    <SliderCaptcha
      :visible="showSliderCaptcha"
      @success="handleCaptchaSuccess"
      @fail="handleCaptchaFail"
      @close="handleCaptchaClose"
    />
    <AiFloatingBall v-if="!hideNav" />
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  background-color: var(--bg-page);
}

.main-content {
  margin-left: 108px;
  padding: 24px 20px;
  padding-top: 80px;
  max-width: 1400px;
  margin-right: auto;
  transition: margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.main-content.admin-active {
  margin-left: 108px;
}

.main-content.no-nav {
  margin-left: 0;
  padding: 0;
  max-width: none;
}
</style>
