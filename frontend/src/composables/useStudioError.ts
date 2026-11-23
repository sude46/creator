import { ApiFailure } from '../api/http'

export function useStudioError() {
  return (failure: unknown): string => {
    if (failure instanceof ApiFailure && failure.status === 401) {
      return '请先登录后再操作。'
    }
    return failure instanceof Error ? failure.message : '操作失败，请稍后重试'
  }
}
