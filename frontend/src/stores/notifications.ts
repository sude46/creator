import { ref } from 'vue'
import { notificationApi } from '../api/notifications'

const unread = ref(0)
let active: AbortController | undefined

async function refresh() {
  active?.abort()
  const controller = new AbortController()
  active = controller
  try {
    const result = await notificationApi.unread(controller.signal)
    if (!controller.signal.aborted) unread.value = result.unread
  } catch {
    // the badge is best-effort; the notification page still loads its own count
  }
}

function set(value: number) { unread.value = Math.max(0, value) }
function decrement(amount = 1) { unread.value = Math.max(0, unread.value - amount) }
function clear() { unread.value = 0 }

export const notificationState = { unread, refresh, set, decrement, clear }
