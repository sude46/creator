type AbortSignalStatics = {
  any?: (signals: AbortSignal[]) => AbortSignal
  timeout?: (milliseconds: number) => AbortSignal
}

/**
 * Safari before 17.4 and some in-app Chromium webviews lack AbortSignal.any/timeout.
 * The API layer relies on both, so without this fallback every media request throws
 * before it reaches the network on those mobile browsers.
 */
export function ensureAbortSignalSupport() {
  const statics = AbortSignal as unknown as AbortSignalStatics
  if (typeof statics.any !== 'function') {
    Object.defineProperty(AbortSignal, 'any', {
      configurable: true,
      writable: true,
      value: (signals: AbortSignal[]) => {
        const controller = new AbortController()
        const abort = (signal: AbortSignal) => controller.abort(signal.reason)
        for (const signal of signals) {
          if (signal.aborted) { abort(signal); break }
          signal.addEventListener('abort', () => abort(signal), { once: true })
        }
        return controller.signal
      },
    })
  }
  if (typeof statics.timeout !== 'function') {
    Object.defineProperty(AbortSignal, 'timeout', {
      configurable: true,
      writable: true,
      value: (milliseconds: number) => {
        const controller = new AbortController()
        globalThis.setTimeout(() => controller.abort(new DOMException('TimeoutError', 'TimeoutError')), milliseconds)
        return controller.signal
      },
    })
  }
}
