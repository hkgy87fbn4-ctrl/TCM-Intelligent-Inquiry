/**
 * 将本地图片编码为 AgentRunRequest 所需的字段，供后端写入 ToolContext，
 * 从而触发 herb_image_recognition_tool（与 multipart 直连视觉模型路径区分）。
 */

const DATA_URL_RE = /^data:([^;]+);base64,(.+)$/s

/**
 * 解析 readAsDataURL 得到的字符串，提取 MIME 与纯 Base64 段。
 */
export function parseDataUrlPayload(dataUrl: string): {
  mime: string
  base64: string
} | null {
  const m = DATA_URL_RE.exec(dataUrl.trim())
  if (!m) return null
  return { mime: m[1], base64: m[2] }
}

/**
 * 读取浏览器 File，产出 herbImageBase64 / herbImageMimeType。
 * MIME 优先 file.type，其次 data URL 推断，最后 image/jpeg。
 */
export function encodeImageFileToHerbPayload(
  file: File
): Promise<{ herbImageBase64: string; herbImageMimeType: string }> {
  return new Promise((resolve, reject) => {
    const fr = new FileReader()
    fr.onload = () => {
      const r = fr.result
      if (typeof r !== 'string') {
        reject(new Error('无法读取图片为 Data URL'))
        return
      }
      const parsed = parseDataUrlPayload(r)
      if (!parsed) {
        reject(new Error('图片 Data URL 格式异常'))
        return
      }
      const mime =
        file.type && file.type.trim() !== ''
          ? file.type.trim()
          : parsed.mime || 'image/jpeg'
      resolve({
        herbImageBase64: parsed.base64,
        herbImageMimeType: mime,
      })
    }
    fr.onerror = () => reject(fr.error ?? new Error('读取图片失败'))
    fr.readAsDataURL(file)
  })
}
