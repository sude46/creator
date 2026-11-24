export class ApiFailure extends Error {
  constructor(public status: number, message: string, public code?: string) {
    super(message)
  }
}

type CsrfToken = { headerName: string; token: string }
let csrf: CsrfToken | undefined
let csrfPromise: Promise<CsrfToken> | undefined

export function clearCsrf() {
  csrf = undefined
  csrfPromise = undefined
}

export async function csrfHeader(): Promise<CsrfToken> {
  if (csrf) return csrf
  csrfPromise ??= request<CsrfToken>('/auth/csrf').then(token => {
    csrf = token
    return token
  }).finally(() => { csrfPromise = undefined })
  return csrfPromise
}

export async function request<Result>(path: string, options: RequestInit = {}): Promise<Result> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await csrfHeader()
    headers.set(token.headerName, token.token)
  }
  let response: Response
  try {
    response = await fetch(`/api${path}`, { ...options, headers, credentials: 'same-origin', signal: options.signal ?? AbortSignal.timeout(30000) })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    if (error instanceof DOMException && error.name === 'TimeoutError') throw new ApiFailure(0, '请求等待超时，请先查询状态确认结果')
    throw new ApiFailure(0, '无法连接服务，请检查网络或稍后重试')
  }
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new Event('creator:unauthorized'))
    const body = await response.json().catch(() => ({}))
    if (response.status === 403) clearCsrf()
    const fields = Object.values(body.fieldErrors ?? {}).filter((value): value is string => typeof value === 'string')
    throw new ApiFailure(response.status, fields.join('；') || body.message || '请求失败，请稍后重试', body.code)
  }
  if (response.status === 204) return undefined as Result
  return response.json() as Promise<Result>
}
