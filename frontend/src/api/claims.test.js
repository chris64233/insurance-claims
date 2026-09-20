import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, createClaim, listClaims } from './claims'

const validForm = {
  policyNumber: 'P20260001',
  insuredName: '张三',
  contactInfo: '13800138000',
  accidentType: '交通事故',
  accidentDate: '2026-09-01',
  claimAmount: '5000.00',
  description: '追尾'
}

afterEach(() => vi.unstubAllGlobals())

describe('listClaims', () => {
  it('成功时返回报案列表', async () => {
    const data = [{ id: 1, claimNo: 'CLM20260901ABCDEFGH' }]
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(data) })
    )

    await expect(listClaims()).resolves.toEqual(data)
  })

  it('网络失败时抛出带中文提示的错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('NetworkError')))

    await expect(listClaims()).rejects.toMatchObject({
      name: 'ApiError',
      message: '无法连接服务器，请确认后端服务已启动'
    })
  })

  it('服务端 500 时抛出错误信息', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: () => Promise.resolve({ message: '服务器内部错误' })
      })
    )

    await expect(listClaims()).rejects.toBeInstanceOf(ApiError)
    await expect(listClaims()).rejects.toMatchObject({
      status: 500,
      message: '服务器内部错误'
    })
  })
})

describe('createClaim', () => {
  it('成功时按约定字段提交并返回服务端数据', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: () =>
        Promise.resolve({
          id: 1,
          claimNo: 'CLM20260901ABCDEFGH',
          status: '待受理'
        })
    })
    vi.stubGlobal('fetch', fetchMock)

    const result = await createClaim(validForm)
    expect(result.claimNo).toBe('CLM20260901ABCDEFGH')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/claims')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toMatchObject({
      policyNumber: 'P20260001',
      accidentDate: '2026-09-01',
      claimAmount: 5000,
      description: '追尾'
    })
  })

  it('校验失败时透传字段级错误信息', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () =>
          Promise.resolve({
            message: '申请金额必须大于 0',
            fieldErrors: { claimAmount: '申请金额必须大于 0' }
          })
      })
    )

    await expect(createClaim(validForm)).rejects.toMatchObject({
      status: 400,
      message: '申请金额必须大于 0',
      fieldErrors: { claimAmount: '申请金额必须大于 0' }
    })
  })

  it('重复报案时返回 409 明确提示', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: () =>
          Promise.resolve({
            message: '该保单项下当日已存在相同事故类型的报案，请勿重复报案'
          })
      })
    )

    await expect(createClaim(validForm)).rejects.toMatchObject({
      status: 409,
      message: expect.stringContaining('重复报案')
    })
  })
})
