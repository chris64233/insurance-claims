<script setup>
import { formatAmount, formatDate, formatDateTime } from '../utils/format'

defineProps({
  claims: {
    type: Array,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['retry'])
</script>

<template>
  <section class="claim-list">
    <div class="list-header">
      <h2>报案列表</h2>
      <button
        class="btn btn-link"
        :disabled="loading"
        @click="emit('retry')"
      >
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </div>

    <div v-if="loading" class="state state-loading">
      <span class="spinner" aria-hidden="true" />
      <span>正在加载报案列表…</span>
    </div>

    <div v-else-if="error" class="state state-error">
      <p>{{ error }}</p>
      <button class="btn btn-ghost" @click="emit('retry')">重新加载</button>
    </div>

    <div v-else-if="claims.length === 0" class="state state-empty">
      <p>暂无报案记录，提交第一笔报案吧。</p>
    </div>

    <table v-else class="table">
      <thead>
        <tr>
          <th>理赔编号</th>
          <th>保单号</th>
          <th>被保险人</th>
          <th>事故类型</th>
          <th>事故日期</th>
          <th class="num">申请金额</th>
          <th>状态</th>
          <th>创建时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="claim in claims" :key="claim.id">
          <td class="mono">{{ claim.claimNo }}</td>
          <td>{{ claim.policyNumber }}</td>
          <td>{{ claim.insuredName }}</td>
          <td>{{ claim.accidentType }}</td>
          <td>{{ formatDate(claim.accidentDate) }}</td>
          <td class="num">{{ formatAmount(claim.claimAmount) }}</td>
          <td><span class="badge">{{ claim.status }}</span></td>
          <td class="muted">{{ formatDateTime(claim.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
