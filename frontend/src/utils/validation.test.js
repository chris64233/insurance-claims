import { describe, expect, it, vi, afterEach } from 'vitest'
import { hasErrors, todayString, validateClaimForm } from './validation'

function validForm(overrides = {}) {
  return {
    policyNumber: 'P20260001',
    insuredName: '张三',
    contactInfo: '13800138000',
    accidentType: '交通事故',
    accidentDate: '2026-09-01',
    claimAmount: '5000.00',
    description: '追尾',
    ...overrides
  }
}

describe('validateClaimForm', () => {
  afterEach(() => vi.useRealTimers())

  it('完整合法的表单不产生错误', () => {
    expect(hasErrors(validateClaimForm(validForm()))).toBe(false)
  })

  it('必填项为空时给出逐项错误提示', () => {
    const errors = validateClaimForm({
      policyNumber: '  ',
      insuredName: '',
      contactInfo: '',
      accidentType: '',
      accidentDate: '',
      claimAmount: '',
      description: ''
    })

    expect(errors.policyNumber).toBe('请输入保单号')
    expect(errors.insuredName).toBe('请输入被保险人姓名')
    expect(errors.contactInfo).toBe('请输入联系方式')
    expect(errors.accidentType).toBe('请选择事故类型')
    expect(errors.accidentDate).toBe('请选择事故日期')
    expect(errors.claimAmount).toBe('请输入申请金额')
  })

  it('事故日期晚于当前日期时拒绝', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-20T08:00:00'))
    expect(todayString()).toBe('2026-09-20')

    const errors = validateClaimForm(validForm({ accidentDate: '2026-09-21' }))
    expect(errors.accidentDate).toBe('事故日期不能晚于当前日期')
  })

  it('事故日期为当天时允许', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-20T08:00:00'))

    const errors = validateClaimForm(validForm({ accidentDate: '2026-09-20' }))
    expect(errors.accidentDate).toBeUndefined()
  })

  it.each([
    ['0', '申请金额必须大于 0'],
    ['-100', '申请金额必须大于 0'],
    ['abc', '申请金额必须大于 0']
  ])('申请金额为 %s 时拒绝', (amount, message) => {
    const errors = validateClaimForm(validForm({ claimAmount: amount }))
    expect(errors.claimAmount).toBe(message)
  })
})
