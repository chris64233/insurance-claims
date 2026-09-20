export function todayString() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

export function validateClaimForm(form) {
  const errors = {}
  const value = (key) => (form[key] ?? '').toString().trim()

  if (!value('policyNumber')) errors.policyNumber = '请输入保单号'
  if (!value('insuredName')) errors.insuredName = '请输入被保险人姓名'
  if (!value('contactInfo')) errors.contactInfo = '请输入联系方式'
  if (!value('accidentType')) errors.accidentType = '请选择事故类型'

  if (!value('accidentDate')) {
    errors.accidentDate = '请选择事故日期'
  } else if (form.accidentDate > todayString()) {
    errors.accidentDate = '事故日期不能晚于当前日期'
  }

  const amount = Number(form.claimAmount)
  if (value('claimAmount') === '') {
    errors.claimAmount = '请输入申请金额'
  } else if (Number.isNaN(amount) || amount <= 0) {
    errors.claimAmount = '申请金额必须大于 0'
  }

  return errors
}

export function hasErrors(errors) {
  return Object.keys(errors).length > 0
}
