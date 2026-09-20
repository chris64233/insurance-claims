<script setup>
import { reactive } from 'vue'
import { hasErrors, todayString, validateClaimForm } from '../utils/validation'

const props = defineProps({
  submitting: {
    type: Boolean,
    default: false
  },
  serverErrors: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['submit'])

const accidentTypes = ['交通事故', '意外受伤', '财产损失', '医疗费用', '其他']
const maxDate = todayString()

const form = reactive({
  policyNumber: '',
  insuredName: '',
  contactInfo: '',
  accidentType: '',
  accidentDate: '',
  claimAmount: '',
  description: ''
})

const clientErrors = reactive({})

function fieldError(field) {
  return clientErrors[field] || props.serverErrors[field]
}

function clearError(field) {
  delete clientErrors[field]
}

function handleSubmit() {
  const errors = validateClaimForm(form)
  Object.keys(clientErrors).forEach((key) => delete clientErrors[key])
  Object.assign(clientErrors, errors)
  if (hasErrors(errors)) return

  emit('submit', {
    policyNumber: form.policyNumber.trim(),
    insuredName: form.insuredName.trim(),
    contactInfo: form.contactInfo.trim(),
    accidentType: form.accidentType,
    accidentDate: form.accidentDate,
    claimAmount: form.claimAmount,
    description: form.description.trim()
  })
}

function resetForm() {
  Object.keys(form).forEach((key) => {
    form[key] = ''
  })
  Object.keys(clientErrors).forEach((key) => delete clientErrors[key])
}

defineExpose({ resetForm })
</script>

<template>
  <form class="claim-form" novalidate @submit.prevent="handleSubmit">
    <div class="form-grid">
      <label class="field">
        <span class="label">保单号 <i>*</i></span>
        <input
          v-model="form.policyNumber"
          name="policyNumber"
          type="text"
          placeholder="例如 P20260001"
          :disabled="submitting"
          @input="clearError('policyNumber')"
        />
        <small v-if="fieldError('policyNumber')" class="error">{{ fieldError('policyNumber') }}</small>
      </label>

      <label class="field">
        <span class="label">被保险人姓名 <i>*</i></span>
        <input
          v-model="form.insuredName"
          name="insuredName"
          type="text"
          placeholder="请输入姓名"
          :disabled="submitting"
          @input="clearError('insuredName')"
        />
        <small v-if="fieldError('insuredName')" class="error">{{ fieldError('insuredName') }}</small>
      </label>

      <label class="field">
        <span class="label">联系方式 <i>*</i></span>
        <input
          v-model="form.contactInfo"
          name="contactInfo"
          type="text"
          placeholder="手机号码或座机"
          :disabled="submitting"
          @input="clearError('contactInfo')"
        />
        <small v-if="fieldError('contactInfo')" class="error">{{ fieldError('contactInfo') }}</small>
      </label>

      <label class="field">
        <span class="label">事故类型 <i>*</i></span>
        <select
          v-model="form.accidentType"
          name="accidentType"
          :disabled="submitting"
          @change="clearError('accidentType')"
        >
          <option value="" disabled>请选择事故类型</option>
          <option v-for="type in accidentTypes" :key="type" :value="type">{{ type }}</option>
        </select>
        <small v-if="fieldError('accidentType')" class="error">{{ fieldError('accidentType') }}</small>
      </label>

      <label class="field">
        <span class="label">事故日期 <i>*</i></span>
        <input
          v-model="form.accidentDate"
          name="accidentDate"
          type="date"
          :max="maxDate"
          :disabled="submitting"
          @change="clearError('accidentDate')"
        />
        <small v-if="fieldError('accidentDate')" class="error">{{ fieldError('accidentDate') }}</small>
      </label>

      <label class="field">
        <span class="label">申请金额（元） <i>*</i></span>
        <input
          v-model="form.claimAmount"
          name="claimAmount"
          type="number"
          min="0.01"
          step="0.01"
          placeholder="请输入大于 0 的金额"
          :disabled="submitting"
          @input="clearError('claimAmount')"
        />
        <small v-if="fieldError('claimAmount')" class="error">{{ fieldError('claimAmount') }}</small>
      </label>
    </div>

    <label class="field field-full">
      <span class="label">事故说明</span>
      <textarea
        v-model="form.description"
        name="description"
        rows="3"
        maxlength="1000"
        placeholder="请简要描述事故经过、损失情况等（选填）"
        :disabled="submitting"
      />
    </label>

    <div class="form-actions">
      <button type="button" class="btn btn-ghost" :disabled="submitting" @click="resetForm">
        重置
      </button>
      <button type="submit" class="btn btn-primary" :disabled="submitting">
        {{ submitting ? '提交中…' : '提交报案' }}
      </button>
    </div>
  </form>
</template>
