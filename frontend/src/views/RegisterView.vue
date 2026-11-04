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
  confirmPassword: '',
  nickname: '',
})

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度6-20个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (value !== form.password) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度2-20个字符', trigger: 'blur' },
  ],
}

async function handleSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await userStore.register(form.phone, form.password, form.nickname)
    ElMessage.success('注册成功')
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
  <div class="register-page">
    <div class="register-card">
      <div class="brand-header">
        <div class="brand-icon">生</div>
        <h2>注册生活帖</h2>
        <p class="brand-desc">加入我们，开始记录生活</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入11位手机号" size="large" maxlength="11" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="你的显示名称" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="6-20个字符"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入密码"
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
          注册
        </el-button>
      </el-form>
      <div class="footer-link">
        已有账号？
        <router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh);
  background: linear-gradient(135deg, #f0f7ff 0%, #f5f0ff 50%, #fff5f5 100%);
}

.register-card {
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

.footer-link {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: #999;
}

.footer-link a {
  color: #ff2442;
  text-decoration: none;
  font-weight: 600;
}

.footer-link a:hover {
  text-decoration: underline;
}
</style>
