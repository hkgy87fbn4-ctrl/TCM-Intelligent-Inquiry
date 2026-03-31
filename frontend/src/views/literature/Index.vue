<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { apiClient } from '@/api/client'
import type { ApiResult } from '@/types/api'
import type { LiteratureFileView, LiteratureQueryResponse } from '@/types/literature'

const health = ref('加载中…')
const collectionId = ref<string | null>(null)
const files = ref<LiteratureFileView[]>([])
const loadingFiles = ref(false)
const uploading = ref(false)
const msg = ref('')
const chunkSize = ref(512)
const queryText = ref('请概括文献中与食疗或体质相关的内容要点。')
const topK = ref(4)
const threshold = ref(0)
const ragAnswer = ref('')
const ragSources = ref<string[]>([])
const ragLoading = ref(false)
const ragError = ref<string | null>(null)

async function refreshHealth() {
  try {
    const { data } = await apiClient.get<ApiResult<string>>('/v1/literature/health')
    health.value = `code=${data.code} ${data.message}`
  } catch (e) {
    health.value = e instanceof Error ? e.message : '请求失败'
  }
}

async function loadFiles() {
  if (!collectionId.value) {
    files.value = []
    return
  }
  loadingFiles.value = true
  try {
    const { data } = await apiClient.get<ApiResult<LiteratureFileView[]>>(
      `/v1/literature/collections/${encodeURIComponent(collectionId.value)}/files`
    )
    if (data.code !== 0) throw new Error(data.message)
    files.value = data.data ?? []
  } finally {
    loadingFiles.value = false
  }
}

watch(collectionId, () => {
  void loadFiles()
})

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  input.value = ''
  if (!f) return
  uploading.value = true
  msg.value = ''
  try {
    const fd = new FormData()
    fd.append('file', f)
    if (collectionId.value) {
      fd.append('collectionId', collectionId.value)
    }
    if (chunkSize.value > 32) {
      fd.append('chunkSize', String(chunkSize.value))
    }
    const { data } = await apiClient.post<ApiResult<LiteratureFileView>>(
      '/v1/literature/uploads',
      fd
    )
    if (data.code !== 0) throw new Error(data.message)
    const row = data.data
    if (row) {
      collectionId.value = row.tempCollectionId
      msg.value = `已解析入库：${row.originalFilename}`
    }
    await loadFiles()
  } catch (e) {
    msg.value = e instanceof Error ? e.message : '上传失败'
  } finally {
    uploading.value = false
  }
}

async function removeFile(fileUuid: string) {
  if (!collectionId.value) return
  await apiClient.delete(
    `/v1/literature/collections/${encodeURIComponent(collectionId.value)}/documents/${encodeURIComponent(fileUuid)}`
  )
  await loadFiles()
}

async function purgeCollection() {
  if (!collectionId.value) return
  if (!confirm('确定删除当前临时文献库及其向量？')) return
  await apiClient.delete(
    `/v1/literature/collections/${encodeURIComponent(collectionId.value)}`
  )
  collectionId.value = null
  files.value = []
  ragAnswer.value = ''
  ragSources.value = []
  msg.value = '已清空临时库'
}

async function newCollection() {
  collectionId.value = null
  files.value = []
  ragAnswer.value = ''
  ragSources.value = []
  msg.value = '请上传首个文件，将自动新建临时文献库'
}

async function runQuery() {
  if (!collectionId.value || !queryText.value.trim()) return
  ragLoading.value = true
  ragError.value = null
  ragAnswer.value = ''
  ragSources.value = []
  try {
    const { data } = await apiClient.post<ApiResult<LiteratureQueryResponse>>(
      `/v1/literature/collections/${encodeURIComponent(collectionId.value)}/query`,
      {
        message: queryText.value.trim(),
        topK: topK.value,
        similarityThreshold: threshold.value,
      }
    )
    if (data.code !== 0) throw new Error(data.message)
    const r = data.data
    if (r) {
      ragAnswer.value = r.answer
      ragSources.value = r.sources ?? []
    }
  } catch (e) {
    ragError.value = e instanceof Error ? e.message : String(e)
  } finally {
    ragLoading.value = false
  }
}

onMounted(async () => {
  await refreshHealth()
})
</script>

<template>
  <div class="page">
    <h2>医学文献问答（临时 RAG · Ollama）</h2>
    <p class="health">{{ health }}</p>
    <p class="hint">
      上传的文献解析、分块与向量化与「知识库」相同，向量元数据使用独立字段，仅在本临时库内检索；可多次向同一库追加文件。
    </p>

    <section class="card">
      <h3>临时文献库</h3>
      <p v-if="collectionId" class="meta">
        当前 collectionId：
        <code>{{ collectionId }}</code>
        <button type="button" class="btn ghost" @click="newCollection">新建空库（仅前端切换）</button>
        <button type="button" class="btn danger" @click="purgeCollection">删除服务端整库</button>
      </p>
      <p v-else class="muted">尚未上传：首次上传会自动分配临时库 ID。</p>
    </section>

    <section class="card">
      <h3>上传文献</h3>
      <div class="row">
        <label>
          分块约长（chunkSize）
          <input v-model.number="chunkSize" type="number" min="128" max="2048" step="64" />
        </label>
        <label class="file-wrap">
          选择文件
          <input type="file" :disabled="uploading" @change="onFileChange" />
        </label>
      </div>
      <p v-if="msg" class="msg">{{ msg }}</p>
      <p v-if="loadingFiles" class="muted">加载列表…</p>
      <ul v-else class="file-list">
        <li v-for="f in files" :key="f.fileUuid || f.id">
          <span>{{ f.originalFilename }}</span>
          <span class="muted">{{ (f.sizeBytes / 1024).toFixed(1) }} KB</span>
          <span class="badge">{{ f.status }}</span>
          <button
            v-if="f.fileUuid"
            type="button"
            class="link"
            @click="removeFile(f.fileUuid)"
          >
            删除
          </button>
        </li>
        <li v-if="files.length === 0 && collectionId" class="muted">库内暂无文件记录</li>
      </ul>
    </section>

    <section class="card">
      <h3>文献问答</h3>
      <textarea v-model="queryText" rows="3" class="ta" placeholder="基于已上传文献提问…" />
      <div class="row">
        <label>
          Top-K
          <input v-model.number="topK" type="number" min="1" max="20" />
        </label>
        <label>
          相似度阈值（0=不过滤）
          <input v-model.number="threshold" type="number" min="0" max="1" step="0.05" />
        </label>
        <button
          type="button"
          class="btn primary"
          :disabled="ragLoading || !collectionId"
          @click="runQuery"
        >
          {{ ragLoading ? '生成中…' : '检索并生成' }}
        </button>
      </div>
      <p v-if="ragError" class="err">{{ ragError }}</p>
      <div v-if="ragAnswer" class="answer">
        <h4>回答</h4>
        <p class="body">{{ ragAnswer }}</p>
        <p v-if="ragSources.length" class="sources">
          <strong>来源：</strong>{{ ragSources.join('、') }}
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  max-width: 720px;
}
h2 {
  margin-top: 0;
}
h3 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
}
.health {
  font-size: 0.85rem;
  color: #4b5563;
  margin-bottom: 0.5rem;
}
.hint {
  font-size: 0.8rem;
  color: #6b7280;
  margin-bottom: 1rem;
  line-height: 1.45;
}
.meta {
  font-size: 0.85rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}
.meta code {
  font-size: 0.75rem;
  background: #f3f4f6;
  padding: 0.2rem 0.4rem;
  border-radius: 6px;
}
.card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 1rem;
  margin-bottom: 1rem;
  background: #fff;
}
.row {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: center;
  margin-top: 0.5rem;
}
.file-wrap input[type='file'] {
  font-size: 0.85rem;
}
.muted {
  color: #9ca3af;
  font-size: 0.85rem;
}
.msg {
  font-size: 0.9rem;
  color: #047857;
  margin-top: 0.5rem;
}
.file-list {
  list-style: none;
  padding: 0;
  margin: 0.5rem 0 0;
}
.file-list li {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  padding: 0.35rem 0;
  border-bottom: 1px solid #f3f4f6;
}
.badge {
  font-size: 0.7rem;
  background: #eef2ff;
  color: #4338ca;
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
}
.link {
  margin-left: auto;
  background: none;
  border: none;
  color: #dc2626;
  cursor: pointer;
  font-size: 0.85rem;
}
.ta {
  width: 100%;
  border-radius: 10px;
  border: 1px solid #d1d5db;
  padding: 0.6rem 0.75rem;
  font-family: inherit;
  margin-bottom: 0.5rem;
}
.btn {
  padding: 0.45rem 0.9rem;
  border-radius: 8px;
  border: 1px solid #d1d5db;
  background: #fff;
  cursor: pointer;
  font-size: 0.85rem;
}
.btn.primary {
  background: #4f46e5;
  border-color: #4338ca;
  color: #fff;
}
.btn.ghost {
  border-style: dashed;
}
.btn.danger {
  color: #b91c1c;
  border-color: #fecaca;
}
.err {
  color: #b91c1c;
  font-size: 0.9rem;
}
.answer .body {
  white-space: pre-wrap;
  line-height: 1.5;
  margin: 0.5rem 0;
}
.sources {
  font-size: 0.85rem;
  color: #4b5563;
}
</style>
