export function formatDate(dateString) {
  if (!dateString) return '-'
  return dateString.slice(0, 10)
}

export function formatDateTime(dateTimeString) {
  if (!dateTimeString) return '-'
  return dateTimeString.replace('T', ' ').slice(0, 16)
}

export function formatAmount(amount) {
  const value = Number(amount)
  if (Number.isNaN(value)) return '-'
  return `¥${value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}
