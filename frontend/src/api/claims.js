const BASE_URL = '/api/claims'

export class ApiError extends Error {
  constructor(message, status, fieldErrors = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function parseError(response) {
  let body = null
  try {
    body = await response.json()
  } catch {
    // 响应体不是 JSON（如网络网关返回的 HTML），使用通用提示
  }
  const message = body?.message || `请求失败（${response.status}），请稍后重试`
  return new ApiError(message, response.status, body?.fieldErrors ?? null)
}

export async function listClaims() {
  let response
  try {
    response = await fetch(BASE_URL)
  } catch {
    throw new ApiError('无法连接服务器，请确认后端服务已启动', 0)
  }
  if (!response.ok) {
    throw await parseError(response)
  }
  return response.json()
}

export async function createClaim(form) {
  const payload = {
    policyNumber: form.policyNumber,
    insuredName: form.insuredName,
    contactInfo: form.contactInfo,
    accidentType: form.accidentType,
    accidentDate: form.accidentDate || null,
    claimAmount:
      form.claimAmount === '' || form.claimAmount === null
        ? null
        : Number(form.claimAmount),
    description: form.description?.trim() || null
  }

  let response
  try {
    response = await fetch(BASE_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
  } catch {
    throw new ApiError('无法连接服务器，请确认后端服务已启动', 0)
  }
  if (!response.ok) {
    throw await parseError(response)
  }
  return response.json()
}
