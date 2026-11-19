export const MAX_UPLOAD_BYTES = 200 * 1024 * 1024

export function validateVideoFile(file: File): string | null {
  if (!/\.mp4$/i.test(file.name) || (file.type && file.type !== 'video/mp4')) {
    return '请选择 MP4 文件，实际编码与内容还需服务端检查。'
  }
  if (file.size === 0 || file.size > MAX_UPLOAD_BYTES) {
    return '文件不能为空，且不能超过 200 MiB。'
  }
  return null
}
