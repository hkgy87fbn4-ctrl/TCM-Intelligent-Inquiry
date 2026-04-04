/**
 * 与后端 IngestionDocumentChunker 约定一致：
 * - overlap ≤ 0：Token 分块，chunkSize 为 token 语义；
 * - overlap > 0：码点滑动窗口，chunkSize 为窗口码点数，须 ≥ 64，且 overlap < chunkSize。
 */
export function validateIngestChunkParams(
  chunkSize: number,
  chunkOverlap: number
): string | null {
  const ov = Number(chunkOverlap)
  const cs = Number(chunkSize)
  if (!Number.isFinite(cs) || !Number.isFinite(ov)) {
    return '分块或重叠参数不是有效数字'
  }
  if (ov <= 0) return null
  if (cs < 64) {
    return '启用重叠切分时，分块约长须 ≥ 64（码点窗口长度）'
  }
  if (ov >= cs) {
    return '重叠须小于分块约长'
  }
  return null
}
