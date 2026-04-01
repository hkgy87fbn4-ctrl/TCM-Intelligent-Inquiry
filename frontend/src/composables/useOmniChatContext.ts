import { ref, watch } from 'vue'

import type { OmniChatMode } from '@/types/omniChat'

const STORAGE_KEY = 'tcm-omni-chat-prefs'

type Persisted = {
  mode: OmniChatMode
  knowledgeBaseId: number | null
  /** 空字符串表示未选 */
  literatureCollectionId: string
  visionUseKb: boolean
  visionKbId: number | null
}

function loadPersisted(): Persisted | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    return JSON.parse(raw) as Persisted
  } catch {
    return null
  }
}

function savePersisted(p: Persisted) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(p))
  } catch {
    /* ignore */
  }
}

/**
 * Omni-Chat：模式与挂载项（与 ChatView 输入区绑定；轻量持久化便于用同一组合继续问）。
 */
export function useOmniChatContext() {
  const saved = loadPersisted()

  const mode = ref<OmniChatMode>(saved?.mode ?? 'standard')
  const knowledgeBaseId = ref<number | null>(saved?.knowledgeBaseId ?? null)
  const literatureCollectionId = ref<string>(
    saved?.literatureCollectionId ?? ''
  )
  const visionUseKnowledgeBase = ref(saved?.visionUseKb ?? false)
  const visionKnowledgeBaseId = ref<number | null>(saved?.visionKbId ?? null)
  const pendingImages = ref<File[]>([])

  function persistNow() {
    savePersisted({
      mode: mode.value,
      knowledgeBaseId: knowledgeBaseId.value,
      literatureCollectionId:
        literatureCollectionId.value.trim() === ''
          ? ''
          : literatureCollectionId.value.trim(),
      visionUseKb: visionUseKnowledgeBase.value,
      visionKbId: visionKnowledgeBaseId.value,
    })
  }

  watch(
    [
      mode,
      knowledgeBaseId,
      literatureCollectionId,
      visionUseKnowledgeBase,
      visionKnowledgeBaseId,
    ],
    persistNow,
    { deep: true }
  )

  function addImagesFromInput(fileList: FileList | null) {
    if (!fileList?.length) return
    const next: File[] = [...pendingImages.value]
    for (let i = 0; i < fileList.length; i++) {
      const f = fileList.item(i)
      if (f && f.type.startsWith('image/')) next.push(f)
    }
    pendingImages.value = next
  }

  function removeImageAt(index: number) {
    pendingImages.value = pendingImages.value.filter((_, i) => i !== index)
  }

  function clearPendingImages() {
    pendingImages.value = []
  }

  return {
    mode,
    knowledgeBaseId,
    literatureCollectionId,
    visionUseKnowledgeBase,
    visionKnowledgeBaseId,
    pendingImages,
    addImagesFromInput,
    removeImageAt,
    clearPendingImages,
  }
}
