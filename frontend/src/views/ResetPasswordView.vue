<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { sendResetCode, resetPassword } from '@/api/user'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)

const form = reactive({
  phone: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

onMounted(() => {
  const phoneParam = route.query.phone as string
  if (phoneParam && /^1[3-9]\d{9}$/.test(phoneParam)) {
    form.phone = phoneParam
  }
})

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

const canSendCode = computed(() => {
  return /^1[3-9]\d{9}$/.test(form.phone) && countdown.value === 0
})

async function handleSendCode() {
  if (!canSendCode.value) return
  
  sendingCode.value = true
  try {
    await sendResetCode(form.phone)
    ElMessage.success('验证码已发送')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch {
  } finally {
    sendingCode.value = false
  }
}

async function handleSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await resetPassword(form.phone, form.code, form.newPassword)
    ElMessage.success('密码重置成功，请登录')
    router.push('/login')
  } catch {
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="reset-password-page">
    <div class="reset-card">
      <div class="brand-header">
        <div class="brand-icon">生</div>
        <h2>找回密码</h2>
        <p class="brand-desc">验证手机号后重置密码</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入注册手机号" size="large" maxlength="11" />
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <div class="code-input">
            <el-input v-model="form.code" placeholder="请输入验证码" size="large" maxlength="6" />
            <el-button
              size="large"
              :disabled="!canSendCode"
              :loading="sendingCode"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="请输入新密码（至少6位）"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
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
          重置密码
        </el-button>
      </el-form>
      <div class="footer-link">
        想起密码了？
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.reset-password-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh);
  background: linear-gradient(135deg, #fff5f5 0%, #f5f0ff 50%, #f0f7ff 100%);
}

.reset-card {
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

.code-input {
  display: flex;
  gap: 12px;
}

.code-input .el-input {
  flex: 1;
}

.code-input .el-button {
  width: 120px;
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
