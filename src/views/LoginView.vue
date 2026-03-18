<template>
  <div class="login-page">
    <div class="bg"></div>
    <div class="grid-bg"></div>
    <div class="particles">
      <span
        v-for="n in 35"
        :key="n"
        class="particle"
        :style="particleStyle(n)"
      />
    </div>

    <div class="left-panel">
      <div class="company-tag">WEIJIAMAO COAL &amp; POWER</div>
      <div class="company-name">北方魏家峁煤电</div>
      <div class="company-sub">有限责任公司</div>
      <div class="divider"></div>
      <div class="subtitle">
        运输皮带三维测量与智能检测系统
      </div>
    </div>

    <section class="login-card">
      <div class="card-header">
        <div class="card-eyebrow">Secure Access</div>
        <h1 class="card-title">系统登录</h1>
      </div>

      <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label class="form-label" for="username">用户名</label>
          <input
            id="username"
            v-model.trim="username"
            type="text"
            placeholder="请输入账号"
            autocomplete="username"
          />
        </div>

        <div class="form-group">
          <label class="form-label" for="password">密码</label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>

        <div class="form-options">
          <label class="remember">
            <input v-model="rememberMe" type="checkbox" />
            <span>记住账号</span>
          </label>
        </div>

        <button class="btn-login" :disabled="loading" type="submit">
          {{ loading ? '验证中...' : '立即登录' }}
        </button>
      </form>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const VALID_USER = 'admin'
const VALID_PASS = 'weijiamao123'

const router = useRouter()
const username = ref('')
const password = ref('')
const rememberMe = ref(false)
const loading = ref(false)
const errorMsg = ref('')

onMounted(() => {
  const remembered = localStorage.getItem('rememberUser')
  if (remembered) {
    username.value = remembered
    rememberMe.value = true
  }
})

function particleStyle(seed) {
  const left = (seed * 73) % 100
  const delay = (seed % 8) * 0.9
  const duration = 7 + (seed % 9)
  return {
    left: `${left}%`,
    animationDelay: `${delay}s`,
    animationDuration: `${duration}s`
  }
}

function persistUser() {
  if (rememberMe.value && username.value) {
    localStorage.setItem('rememberUser', username.value)
  } else {
    localStorage.removeItem('rememberUser')
  }
}

async function handleLogin() {
  errorMsg.value = ''

  if (!username.value) {
    errorMsg.value = '请输入用户名'
    return
  }

  if (!password.value) {
    errorMsg.value = '请输入密码'
    return
  }

  loading.value = true
  await new Promise((resolve) => setTimeout(resolve, 600))

  if (username.value === VALID_USER && password.value === VALID_PASS) {
    persistUser()
    sessionStorage.setItem('auth', 'true')
    sessionStorage.setItem('username', username.value)
    await router.replace('/dashboard')
    return
  }

  loading.value = false
  password.value = ''
  errorMsg.value = '用户名或密码错误，请重试'
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 8vw;
  overflow: hidden;
}

.bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(1000px 500px at 70% 20%, rgba(0, 110, 210, 0.22), transparent 60%),
    linear-gradient(160deg, rgba(5, 10, 20, 0.92), rgba(8, 14, 28, 0.88), rgba(4, 8, 18, 0.95));
}

.grid-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background-image:
    linear-gradient(rgba(0, 120, 200, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 120, 200, 0.08) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse 85% 80% at 55% 50%, black 30%, transparent 100%);
}

.particles {
  position: fixed;
  inset: 0;
  z-index: 0;
}

.particle {
  position: absolute;
  bottom: -10px;
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: var(--accent);
  opacity: 0;
  animation-name: float-up;
  animation-timing-function: ease-in;
  animation-iteration-count: infinite;
}

.left-panel {
  position: fixed;
  left: 8vw;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1;
}

.company-tag {
  font-family: var(--font-head);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 4px;
  color: var(--accent);
  text-transform: uppercase;
  margin-bottom: 20px;
}

.company-name {
  font-size: 38px;
  font-weight: 700;
  color: #e8f4ff;
  line-height: 1.2;
}

.company-sub {
  font-size: 26px;
  font-weight: 300;
  color: var(--text-sec);
  margin-bottom: 28px;
  letter-spacing: 2px;
}

.divider {
  width: 56px;
  height: 2px;
  background: linear-gradient(90deg, var(--accent), transparent);
  margin-bottom: 24px;
}

.subtitle {
  font-size: 15px;
  color: var(--text-sec);
  letter-spacing: 1.5px;
}

.login-card {
  position: relative;
  z-index: 2;
  width: 420px;
  background: rgba(10, 16, 30, 0.85);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 4px;
  padding: 48px 44px;
  backdrop-filter: blur(20px);
  box-shadow:
    0 0 0 1px rgba(0, 180, 255, 0.07),
    0 32px 80px rgba(0, 0, 0, 0.65);
}

.card-eyebrow {
  font-family: var(--font-head);
  font-size: 13px;
  letter-spacing: 3px;
  color: var(--accent);
  text-transform: uppercase;
  margin-bottom: 12px;
}

.card-title {
  font-size: 26px;
  font-weight: 500;
  letter-spacing: 2px;
  margin-bottom: 30px;
}

.error-msg {
  border: 1px solid rgba(255, 77, 109, 0.36);
  background: rgba(255, 77, 109, 0.1);
  color: #ff7d92;
  border-radius: 4px;
  padding: 12px 14px;
  margin-bottom: 16px;
  font-size: 13px;
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: 12px;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  color: var(--text-sec);
  margin-bottom: 10px;
}

input {
  width: 100%;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  padding: 14px 16px;
  font-family: var(--font-ui);
  font-size: 15px;
  color: var(--text-pri);
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

input:focus {
  border-color: rgba(0, 180, 255, 0.5);
  background: rgba(0, 180, 255, 0.04);
  box-shadow: 0 0 0 3px rgba(0, 180, 255, 0.08);
}

.form-options {
  margin-bottom: 26px;
}

.remember {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-sec);
}

.remember input[type='checkbox'] {
  width: 15px;
  height: 15px;
  accent-color: var(--accent);
}

.btn-login {
  width: 100%;
  padding: 15px;
  border: none;
  border-radius: 2px;
  font-family: var(--font-ui);
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 3px;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #0077cc, #00b4ff);
  box-shadow: 0 4px 24px rgba(0, 180, 255, 0.32);
  transition: opacity 0.2s;
}

.btn-login:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

@keyframes float-up {
  0% {
    opacity: 0;
    transform: translateY(0) scale(1);
  }
  12% {
    opacity: 0.6;
  }
  100% {
    opacity: 0;
    transform: translateY(-110vh) scale(0.2);
  }
}

@media (max-width: 1100px) {
  .login-page {
    justify-content: center;
    padding-right: 0;
  }

  .left-panel {
    display: none;
  }

  .login-card {
    width: min(92vw, 420px);
  }
}
</style>
