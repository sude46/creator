import type { SignedMedia } from './creator'

type CoverEntry = { url: string; expiresAt: number }

const cache = new Map<string, CoverEntry>()
const inflight = new Map<string, Promise<CoverEntry>>()
const waiting: Array<() => void> = []
const MAX_CONCURRENT = 4
let active = 0

function acquire(): Promise<void> {
  if (active < MAX_CONCURRENT) {
    active += 1
    return Promise.resolve()
  }
  return new Promise(resolve => waiting.push(() => { active += 1; resolve() }))
}

function release() {
  active -= 1
  waiting.shift()?.()
}

export async function cachedCover(key: string, request: () => Promise<SignedMedia>): Promise<CoverEntry> {
  const cached = cache.get(key)
  if (cached && cached.expiresAt - 30_000 > Date.now()) return cached
  const running = inflight.get(key)
  if (running) return running
  const task = (async () => {
    await acquire()
    try {
      const signed = await request()
      const entry = { url: signed.url, expiresAt: new Date(signed.expiresAt).getTime() }
      cache.set(key, entry)
      return entry
    } finally {
      release()
    }
  })()
  inflight.set(key, task)
  try { return await task } finally { inflight.delete(key) }
}

export function clearCoverCache() {
  cache.clear()
  inflight.clear()
}
