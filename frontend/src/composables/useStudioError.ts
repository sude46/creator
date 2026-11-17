import { useRouter } from 'vue-router'
import { ApiFailure } from '../api/http'
import { auth } from '../stores/auth'

export function useStudioError() {
  const router = useRouter()
  return (failure: unknown): string => {
    if (failure instanceof ApiFailure && failure.status === 401) {
      auth.expire()
      void router.replace('/login')
    }
    return failure instanceof Error ? failure.message : '操作失败，请稍后重试'
  }
}
