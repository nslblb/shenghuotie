<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  phone: '',
  password: '',
})

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await userStore.login(form.phone, form.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    // 错误由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand-header">
        <div class="brand-icon">生</div>
        <h2>登录生活帖</h2>
        <p class="brand-desc">记录生活，分享美好</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" size="large" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="submit-btn"
          :loading="loading"
          @click="handleSubmit"
        >
          登录
        </el-button>
      </el-form>
      <div class="footer-links">
        <router-link :to="`/reset-password?phone=${form.phone}`" class="forgot-link">忘记密码？</router-link>
        <div class="register-link">
          还没有账号？
          <router-link to="/register">去注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh);
  background: linear-gradient(135deg, #fff5f5 0%, #f5f0ff 50%, #f0f7ff 100%);
}

.login-card {
  width: 380px;
  padding: 32px 32px;
  background: white;
  border-radius: 20px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
}

.brand-header {
  text-align: center;
  margin-bottom: 24px;
}

.brand-icon {
  width: 48px;
  height: 48px;
  line-height: 48px;
  border-radius: 16px;
  background: linear-gradient(135deg, #ff2442, #ff6b81);
  color: white;
  font-size: 24px;
  font-weight: 700;
  margin: 0 auto 16px;
}

.brand-header h2 {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
}

.brand-desc {
  margin: 0;
  font-size: 14px;
  color: #999;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
}

.footer-links {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.forgot-link {
  font-size: 14px;
  color: #999;
  text-decoration: none;
  transition: color 0.2s;
}

.forgot-link:hover {
  color: #ff2442;
}

.register-link {
  font-size: 14px;
  color: #999;
}

.register-link a {
  color: #ff2442;
  text-decoration: none;
  font-weight: 600;
}

.register-link a:hover {
  text-decoration: underline;
}
</style>
