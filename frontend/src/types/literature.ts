export type LiteratureUploadStatus = 'PENDING' | 'READY' | 'FAILED'

export interface LiteratureFileView {
  id: number
  tempCollectionId: string
  originalFilename: string
  fileUuid: string
  sizeBytes: number
  contentType: string
  status: LiteratureUploadStatus
  createdAt: string
}

export interface LiteratureQueryResponse {
  answer: string
  sources: string[]
  retrievedChunks: number
}
