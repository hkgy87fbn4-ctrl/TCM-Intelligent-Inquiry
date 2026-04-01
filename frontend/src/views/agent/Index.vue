<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiClient } from '@/api/client'
import type { ApiResult } from '@/types/api'
import type { KnowledgeBase } from '@/types/knowledge'
import type { AgentRunResponse } from '@/types/agent'

const health = ref('加载中…')
const task = ref('请根据图像与知识库摘录，说明该药材可能的名称及使用注意。')
/** 空字符串表示不用知识库 */
const kbSelection = ref<string>('')
const bases = ref<KnowledgeBase[]>([])
const ragTopK = ref(4)
const ragThreshold = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const result = ref<AgentRunResponse | null>(null)

async function refreshHealth() {
  try {
    const { data } = await apiClient.get<ApiResult<string>>('/v1/agent/health')
    health.value = `code=${data.code} ${data.message}`
  } catch (e) {
    health.value = e instanceof Error ? e.message : '请求失败'
  }
}

async function loadBases() {
  try {
    const { data } = await apiClient.get<ApiResult<KnowledgeBase[]>>('/v1/knowledge/bases')
    if (data.code !== 0) throw new Error(data.message)
    bases.value = data.data ?? []
    if (kbSelection.value === '' && bases.value.length > 0) {
      kbSelection.value = String(bases.value[0].id)
    }
  } catch {
    /* 知识库不可用时仍可跑纯对话/识图 */
  }
}

async function runJsonOnly() {
  error.value = null
  result.value = null
  loading.value = true
  try {
    const body: Record<string, unknown> = { task: task.value.trim() }
    if (kbSelection.value !== '') {
      body.knowledgeBaseId = Number(kbSelection.value)
      body.ragTopK = ragTopK.value
      body.ragSimilarityThreshold = ragThreshold.value
    }
    const { data } = await apiClient.post<ApiResult<AgentRunResponse>>('/v1/agent/run', body)
    if (data.code !== 0) throw new Error(data.message)
    result.value = data.data ?? null
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function onImageChange(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  input.value = ''
  if (!f || !task.value.trim()) {
    error.value = '请先填写任务描述，再选择图片'
    return
  }
  error.value = null
  result.value = null
  loading.value = true
  try {
    const fd = new FormData()
    fd.append('task', task.value.trim())
    if (kbSelection.value !== '') {
      fd.append('knowledgeBaseId', kbSelection.value)
      fd.append('ragTopK', String(ragTopK.value))
      fd.append('ragSimilarityThreshold', String(ragThreshold.value))
    }
    fd.append('image', f)
    const { data } = await apiClient.post<ApiResult<AgentRunResponse>>('/v1/agent/run', fd)
    if (data.code !== 0) throw new Error(data.message)
    result.value = data.data ?? null
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await refreshHealth()
  await loadBases()
})
</script>

<template>
  <div class="page">
    <h2>中医智能体</h2>
    <p class="health">{{ health }}</p>
    <p class="hint">
      文本对话走默认 Ollama Chat 模型；上传图片时使用配置的视觉模型（如 qwen3-vl）。可选勾选知识库，将先做向量检索再把摘录与任务一并交给模型。
    </p>

    <section class="card">
      <h3>任务</h3>
      <textarea v-model="task" rows="4" class="ta" placeholder="描述要让智能体做什么…" />
      <div class="row">
        <label v-if="bases.length">
          知识库（可选）
          <select v-model="kbSelection" class="sel">
            <option value="">不使用知识库</option>
            <option v-for="b in bases" :key="b.id" :value="String(b.id)">
              {{ b.name }} (id={{ b.id }})
            </option>
          </select>
        </label>
        <template v-if="kbSelection !== ''">
          <label>
            RAG Top-K
            <input v-model.number="ragTopK" type="number" min="1" max="20" class="num" />
          </label>
          <label>
            相似度阈值
            <input v-model.number="ragThreshold" type="number" min="0" max="1" step="0.05" class="num" />
          </label>
        </template>
      </div>
      <div class="actions">
        <button type="button" class="btn primary" :disabled="loading" @click="runJsonOnly">
          {{ loading ? '运行中…' : '仅文本运行' }}
        </button>
        <label class="file-btn">
          选择图片并运行（多模态）
          <input type="file" accept="image/*" :disabled="loading" @change="onImageChange" />
        </label>
      </div>
      <p v-if="error" class="err">{{ error }}</p>
      <div v-if="result" class="out">
        <p class="meta">
          <span class="badge">{{ result.mode }}</span>
          <span v-if="result.knowledgeSources?.length" class="src">
            知识库来源：{{ result.knowledgeSources.join('、') }}
          </span>
        </p>
        <div class="body">{{ result.assistant }}</div>
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
}
.hint {
  font-size: 0.8rem;
  color: #6b7280;
  line-height: 1.45;
  margin-bottom: 1rem;
}
.card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 1rem;
  background: #fff;
}
.ta {
  width: 100%;
  border-radius: 10px;
  border: 1px solid #d1d5db;
  padding: 0.6rem 0.75rem;
  font-family: inherit;
  margin-bottom: 0.75rem;
}
.row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
  margin-bottom: 0.75rem;
}
.sel,
.num {
  margin-left: 0.35rem;
  padding: 0.25rem 0.5rem;
  border-radius: 8px;
  border: 1px solid #d1d5db;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
}
.btn {
  padding: 0.5rem 1rem;
  border-radius: 8px;
  border: 1px solid #d1d5db;
  background: #fff;
  cursor: pointer;
}
.btn.primary {
  background: #0d9488;
  border-color: #0f766e;
  color: #fff;
}
.file-btn {
  font-size: 0.9rem;
  padding: 0.5rem 1rem;
  border-radius: 8px;
  border: 1px dashed #94a3b8;
  cursor: pointer;
}
.file-btn input {
  display: none;
}
.err {
  color: #b91c1c;
  font-size: 0.9rem;
  margin-top: 0.5rem;
}
.out {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #f3f4f6;
}
.meta {
  font-size: 0.8rem;
  color: #647488;
  margin-bottom: 0.5rem;
}
.badge {
  display: inline-block;
  background: #ecfdf5;
  color: #047857;
  padding: 0.15rem 0.5rem;
  border-radius: 6px;
  margin-right: 0.5rem;
}
.src {
  display: inline-block;
}
.body {
  white-space: pre-wrap;
  line-height: 1.55;
  font-size: 0.95rem;
}
</style>
