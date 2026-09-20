<script setup>
import { onMounted, ref } from 'vue'
import { createClaim, listClaims } from './api/claims'
import ClaimForm from './components/ClaimForm.vue'
import ClaimList from './components/ClaimList.vue'

const claimForm = ref(null)
const claims = ref([])
const listLoading = ref(false)
const listError = ref('')
const submitting = ref(false)
const formError = ref('')
const serverFieldErrors = ref({})
const successMessage = ref('')

async function loadClaims() {
  listLoading.value = true
  listError.value = ''
  try {
    claims.value = await listClaims()
  } catch (err) {
    listError.value = err.message || '报案列表加载失败'
  } finally {
    listLoading.value = false
  }
}

async function handleSubmit(formData) {
  submitting.value = true
  formError.value = ''
  serverFieldErrors.value = {}
  successMessage.value = ''
  try {
    const created = await createClaim(formData)
    successMessage.value = `报案成功，理赔编号：${created.claimNo}，当前状态：${created.status}`
    claimForm.value?.resetForm()
    await loadClaims()
  } catch (err) {
    formError.value = err.message || '报案提交失败，请稍后重试'
    if (err.fieldErrors) {
      serverFieldErrors.value = err.fieldErrors
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadClaims)
</script>

<template>
  <main class="page">
    <header class="page-header">
      <h1>保险理赔报案登记</h1>
      <p>填写出险信息完成报案，提交后可在下方列表中查看受理进度。</p>
    </header>

    <div class="layout">
      <section class="card">
        <h2>填写报案信息</h2>

        <div v-if="successMessage" class="alert alert-success" role="status">
          <span class="alert-icon">✓</span>
          <span>{{ successMessage }}</span>
        </div>

        <div v-if="formError" class="alert alert-error" role="alert">
          <span class="alert-icon">!</span>
          <span>{{ formError }}</span>
        </div>

        <ClaimForm
          ref="claimForm"
          :submitting="submitting"
          :server-errors="serverFieldErrors"
          @submit="handleSubmit"
        />
      </section>

      <section class="card">
        <ClaimList
          :claims="claims"
          :loading="listLoading"
          :error="listError"
          @retry="loadClaims"
        />
      </section>
    </div>
  </main>
</template>
