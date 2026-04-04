import { describe, expect, it } from 'vitest'

import { validateIngestChunkParams } from './chunkUploadParams'

describe('validateIngestChunkParams', () => {
  it('allows any chunkSize when overlap is 0', () => {
    expect(validateIngestChunkParams(512, 0)).toBeNull()
    expect(validateIngestChunkParams(128, 0)).toBeNull()
  })

  it('rejects overlap >= chunkSize', () => {
    expect(validateIngestChunkParams(128, 128)).not.toBeNull()
    expect(validateIngestChunkParams(128, 200)).not.toBeNull()
  })

  it('rejects small window with positive overlap', () => {
    expect(validateIngestChunkParams(50, 10)).not.toBeNull()
  })

  it('accepts valid sliding window', () => {
    expect(validateIngestChunkParams(512, 64)).toBeNull()
  })
})
