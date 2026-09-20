import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ClaimForm from './ClaimForm.vue'

async function fillForm(wrapper) {
  const values = {
    policyNumber: 'P20260001',
    insuredName: '张三',
    contactInfo: '13800138000',
    accidentType: '交通事故',
    accidentDate: '2026-09-01',
      claimAmount: 5000,
    description: '追尾'
  }
  for (const [field, value] of Object.entries(values)) {
    const element = wrapper.find(`[name="${field}"]`)
    if (element.exists()) {
      await element.setValue(value)
    }
  }
}

describe('ClaimForm', () => {
  it('必填项为空时显示校验错误且不提交', async () => {
    const wrapper = mount(ClaimForm)

    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.text()).toContain('请输入保单号')
    expect(wrapper.text()).toContain('请选择事故日期')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('提交中禁用提交按钮并显示提交中文案', () => {
    const wrapper = mount(ClaimForm, {
      props: { submitting: true }
    })
    const button = wrapper.find('button[type="submit"]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.text()).toContain('提交中')
  })

  it('表单合法时提交规整后的数据', async () => {
    const wrapper = mount(ClaimForm)
    await fillForm(wrapper)

    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('submit')).toHaveLength(1)
    expect(wrapper.emitted('submit')[0][0]).toMatchObject({
      policyNumber: 'P20260001',
      insuredName: '张三',
      accidentType: '交通事故',
      accidentDate: '2026-09-01',
      claimAmount: 5000
    })
  })

  it('重置后清空表单字段', async () => {
    const wrapper = mount(ClaimForm)
    await fillForm(wrapper)

    wrapper.vm.resetForm()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('input').element.value).toBe('')
  })

  it('服务端字段错误直接展示在对应字段下', () => {
    const wrapper = mount(ClaimForm, {
      props: { serverErrors: { policyNumber: '服务端校验：保单号不能为空' } }
    })
    expect(wrapper.text()).toContain('服务端校验：保单号不能为空')
  })
})
