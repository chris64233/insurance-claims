import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ClaimList from './ClaimList.vue'

const claims = [
  {
    id: 1,
    claimNo: 'CLM20260901ABCDEFGH',
    policyNumber: 'P20260001',
    insuredName: '张三',
    contactInfo: '13800138000',
    accidentType: '交通事故',
    accidentDate: '2026-09-01',
    claimAmount: 5000,
    description: '追尾',
    status: '待受理',
    createdAt: '2026-09-01T10:20:30'
  }
]

describe('ClaimList', () => {
  it('加载中显示加载提示', () => {
    const wrapper = mount(ClaimList, {
      props: { claims: [], loading: true, error: '' }
    })
    expect(wrapper.text()).toContain('正在加载报案列表')
    expect(wrapper.find('.table').exists()).toBe(false)
  })

  it('空数据显示空状态', () => {
    const wrapper = mount(ClaimList, {
      props: { claims: [], loading: false, error: '' }
    })
    expect(wrapper.text()).toContain('暂无报案记录')
  })

  it('请求失败显示错误信息并可重试', async () => {
    const wrapper = mount(ClaimList, {
      props: { claims: [], loading: false, error: '报案列表加载失败' }
    })
    expect(wrapper.text()).toContain('报案列表加载失败')

    await wrapper.findAll('button')[0].trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('有数据时渲染表格内容', () => {
    const wrapper = mount(ClaimList, {
      props: { claims, loading: false, error: '' }
    })
    expect(wrapper.find('.table').exists()).toBe(true)
    expect(wrapper.text()).toContain('CLM20260901ABCDEFGH')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('待受理')
    expect(wrapper.text()).toContain('¥5,000.00')
  })
})
