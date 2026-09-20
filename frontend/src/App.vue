<template>
  <main class="shell">
    <header class="page-header">
      <h1>保险理赔报案</h1>
      <p>在线填写报案信息，提交后生成理赔编号</p>
    </header>

    <section class="card">
      <h2>报案登记</h2>
      <form class="claim-form" @submit.prevent="submitClaim">
        <label>
          保单号
          <input v-model.trim="form.policyNo" type="text" placeholder="请输入保单号" required />
        </label>
        <label>
          被保险人姓名
          <input v-model.trim="form.insuredName" type="text" placeholder="请输入姓名" required />
        </label>
        <label>
          联系方式
          <input v-model.trim="form.contactPhone" type="text" placeholder="请输入手机号" required />
        </label>
        <label>
          事故类型
          <select v-model="form.accidentType" required>
            <option value="" disabled>请选择事故类型</option>
            <option v-for="type in accidentTypes" :key="type" :value="type">{{ type }}</option>
          </select>
        </label>
        <label>
          事故日期
          <input v-model="form.accidentDate" type="date" :max="today" required />
        </label>
        <label>
          申请金额（元）
          <input v-model="form.claimAmount" type="number" min="0.01" step="0.01" placeholder="0.00" required />
        </label>
        <label class="full-width">
          事故说明
          <textarea v-model.trim="form.description" rows="3" placeholder="请描述事故经过" required></textarea>
        </label>

        <p v-if="submitError" class="message error full-width">{{ submitError }}</p>
        <p v-if="submitSuccess" class="message success full-width">{{ submitSuccess }}</p>

        <div class="full-width">
          <button type="submit" :disabled="submitting">
            {{ submitting ? '提交中…' : '提交报案' }}
          </button>
        </div>
      </form>
    </section>

    <section class="card">
      <h2>报案列表</h2>
      <p v-if="loading" class="message">加载中…</p>
      <p v-else-if="loadError" class="message error">
        {{ loadError }}
        <button type="button" class="link-button" @click="loadClaims">重试</button>
      </p>
      <p v-else-if="claims.length === 0" class="message">暂无报案记录</p>
      <table v-else class="claim-table">
        <thead>
          <tr>
            <th>理赔编号</th>
            <th>保单号</th>
            <th>被保险人</th>
            <th>事故类型</th>
            <th>事故日期</th>
            <th>申请金额</th>
            <th>状态</th>
            <th>创建时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="claim in claims" :key="claim.id">
            <td>{{ claim.claimNo }}</td>
            <td>{{ claim.policyNo }}</td>
            <td>{{ claim.insuredName }}</td>
            <td>{{ claim.accidentType }}</td>
            <td>{{ claim.accidentDate }}</td>
            <td>{{ formatAmount(claim.claimAmount) }}</td>
            <td><span class="status">{{ claim.status }}</span></td>
            <td>{{ formatDateTime(claim.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'

const accidentTypes = ['车辆碰撞', '意外摔伤', '疾病医疗', '财产损失', '其他']

const today = new Date().toISOString().slice(0, 10)

const emptyForm = () => ({
  policyNo: '',
  insuredName: '',
  contactPhone: '',
  accidentType: '',
  accidentDate: '',
  claimAmount: '',
  description: ''
})

const form = reactive(emptyForm())
const claims = ref([])
const submitting = ref(false)
const loading = ref(false)
const submitError = ref('')
const submitSuccess = ref('')
const loadError = ref('')

async function request(url, options) {
  const response = await fetch(url, options)
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const message = body?.errors
      ? Object.values(body.errors).join('；')
      : body?.message || `请求失败（${response.status}）`
    throw new Error(message)
  }
  return body
}

async function loadClaims() {
  loading.value = true
  loadError.value = ''
  try {
    claims.value = await request('/api/claims')
  } catch (error) {
    loadError.value = `报案列表加载失败：${error.message}`
  } finally {
    loading.value = false
  }
}

async function submitClaim() {
  submitting.value = true
  submitError.value = ''
  submitSuccess.value = ''
  try {
    const created = await request('/api/claims', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...form, claimAmount: Number(form.claimAmount) })
    })
    submitSuccess.value = `报案成功，理赔编号：${created.claimNo}`
    Object.assign(form, emptyForm())
    await loadClaims()
  } catch (error) {
    submitError.value = `提交失败：${error.message}`
  } finally {
    submitting.value = false
  }
}

function formatAmount(amount) {
  return Number(amount).toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' })
}

function formatDateTime(value) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

onMounted(loadClaims)
</script>
