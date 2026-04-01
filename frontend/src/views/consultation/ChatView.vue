<script setup lang="ts">
import { inject, onMounted, ref, watch, nextTick } from 'vue'
import { apiClient } from '@/api/client'
import { getErrorMessage } from '@/api/errors'
import type { ApiResult } from '@/types/api'
import type { AgentConfigView } from '@/types/agent'
import type { KnowledgeBase } from '@/types/knowledge'
import type { LiteratureFileView } from '@/types/literature'
import ChatBubble from '@/components/ChatBubble.vue'
import { useOmniChatContext } from '@/composables/useOmniChatContext'
import {
  formatHealthStatus,
  isHealthStatusErr,
  isHealthStatusOk,
} from '@/utils/formatHealthStatus'
import { CONSULT_CHAT_KEY } from '@/views/consultation/consultChatKey'

const chat = inject(CONSULT_CHAT_KEY)
if (!chat) {
  throw new Error('ConsultChatView must be mounted under ConsultationLayout')
}

const {
  sessionId,
  messages,
  loading,
  error,
  streamingContent,
  ragMeta,
  sendOmni,
  stop,
} = chat

const {
  mode,
  knowledgeBaseId,
  literatureCollectionId,
  visionUseKnowledgeBase,
  visionKnowledgeBaseId,
  pendingImages,
  addImagesFromInput,
  removeImageAt,
  clearPendingImages,
} = useOmniChatContext()

const health = ref<string>('加载中…')
const threadEl = ref<HTMLElement | null>(null)
const input = ref('')
const temperature = ref(0.7)
const maxHistoryTurns = ref(10)
const knowledgeBases = ref<KnowledgeBase[]>([])
const ragTopK = ref(4)
const ragSimilarityThreshold = ref(0)
const literatureTopK = ref(4)
const literatureThreshold = ref(0)
const literatureCollections = ref<{ id: string; label: string }[]>([])
const attachInput = ref<HTMLInputElement | null>(null)

async function loadKnowledgeBases() {
  try {
    const { data } = await apiClient.get<ApiResult<KnowledgeBase[]>>(
      '/v1/knowledge/bases'
    )
    if (data.code !== 0) return
    knowledgeBases.value = data.data ?? []
  } catch {
    /* 知识库不可用时仍可纯问诊 */
  }
}

async function loadLiteratureCollections() {
  try {
    const { data } = await apiClient.get<ApiResult<LiteratureFileView[]>>(
      '/v1/literature/uploads'
    )
    if (data.code !== 0) return
    const files = data.data ?? []
    const seen = new Set<string>()
    const rows: { id: string; label: string }[] = []
    for (const f of files) {
      const cid = f.tempCollectionId?.trim()
      if (!cid || seen.has(cid)) continue
      seen.add(cid)
      const short = cid.length > 12 ? `${cid.slice(0, 10)}…` : cid
      rows.push({ id: cid, label: `文献库 ${short}` })
    }
    literatureCollections.value = rows
  } catch {
    literatureCollections.value = []
  }
}

async function loadAgentDefaults() {
  try {
    const { data } = await apiClient.get<ApiResult<AgentConfigView>>(
      '/v1/agent/config'
    )
    if (data.code !== 0 || !data.data) return
    const kb = data.data.defaultKnowledgeBaseId
    if (kb != null && visionKnowledgeBaseId.value == null) {
      visionKnowledgeBaseId.value = kb
    }
  } catch {
    /* optional */
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = threadEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(
  () => [messages.value, streamingContent.value],
  () => scrollToBottom(),
  { deep: true }
)

watch(
  () => mode.value,
  (m) => {
    if (m === 'literature') void loadLiteratureCollections()
  }
)

onMounted(async () => {
  try {
    const { data } = await apiClient.get<ApiResult<string>>('/v1/consultation/health')
    health.value = formatHealthStatus(data.code, data.message ?? '')
  } catch (e) {
    health.value = `后端不可用: ${getErrorMessage(e)}`
  }
  void loadKnowledgeBases()
  void loadLiteratureCollections()
  void loadAgentDefaults()
})

function onAttachClick() {
  attachInput.value?.click()
}

function onAttachChange(e: Event) {
  const el = e.target as HTMLInputElement
  addImagesFromInput(el.files)
  el.value = ''
}

async function onSend() {
  const text = input.value.trim()
  if (!text || loading.value) return

  const m = mode.value
  const lit =
    literatureCollectionId.value.trim() === ''
      ? null
      : literatureCollectionId.value.trim()

  if (m === 'vision') {
    const first = pendingImages.value[0] ?? null
    input.value = ''
    await sendOmni(text, {
      mode: 'vision',
      knowledgeBaseId: knowledgeBaseId.value,
      literatureCollectionId: lit,
      visionUseKb: visionUseKnowledgeBase.value,
      visionKbId: visionKnowledgeBaseId.value,
      literatureTopK: literatureTopK.value,
      literatureThreshold: literatureThreshold.value,
      visionImage: first,
      temperature: temperature.value,
      maxHistoryTurns: maxHistoryTurns.value,
      ragTopK: ragTopK.value,
      ragSimilarityThreshold: ragSimilarityThreshold.value,
      scrollRoot: threadEl.value,
    })
    if (!error.value) clearPendingImages()
    return
  }

  input.value = ''
  await sendOmni(text, {
    mode: m,
    knowledgeBaseId: knowledgeBaseId.value,
    literatureCollectionId: lit,
    visionUseKb: visionUseKnowledgeBase.value,
    visionKbId: visionKnowledgeBaseId.value,
    literatureTopK: literatureTopK.value,
    literatureThreshold: literatureThreshold.value,
    visionImage: null,
    temperature: temperature.value,
    maxHistoryTurns: maxHistoryTurns.value,
    ragTopK: ragTopK.value,
    ragSimilarityThreshold: ragSimilarityThreshold.value,
    scrollRoot: threadEl.value,
  })
}

function canSend() {
  if (!input.value.trim() || loading.value) return false
  if (mode.value === 'knowledge' && knowledgeBaseId.value == null) {
    return false
  }
  if (mode.value === 'literature' && literatureCollectionId.value.trim() === '') {
    return false
  }
  return true
}
</script>

<template>
  <div class="consult-chat ds-main__grow">
    <header class="consult-header">
      <div class="consult-header__top">
        <div class="consult-header__main">
          <h2 class="ds-h2 consult-header__title">
            中医智能问诊
          </h2>
          <p
            class="ds-status consult-header__status"
            :class="
              isHealthStatusErr(health)
                ? 'ds-status--err'
                : isHealthStatusOk(health)
                  ? 'ds-status--ok'
                  : ''
            "
          >
            {{ health }}
          </p>

          <div class="omni-bar">
            <span class="omni-bar__label">会话模式</span>
            <div
              class="omni-bar__modes"
              role="group"
              aria-label="会话模式"
            >
              <button
                type="button"
                class="omni-chip"
                :class="mode === 'standard' ? 'omni-chip--active' : ''"
                :disabled="loading"
                @click="mode = 'standard'"
              >
                标准问诊
              </button>
              <button
                type="button"
                class="omni-chip"
                :class="mode === 'knowledge' ? 'omni-chip--active' : ''"
                :disabled="loading"
                @click="mode = 'knowledge'"
              >
                知识库 RAG
              </button>
              <button
                type="button"
                class="omni-chip"
                :class="mode === 'literature' ? 'omni-chip--active' : ''"
                :disabled="loading"
                @click="mode = 'literature'"
              >
                文献库
              </button>
              <button
                type="button"
                class="omni-chip"
                :class="mode === 'vision' ? 'omni-chip--active' : ''"
                :disabled="loading"
                @click="mode = 'vision'"
              >
                视觉智能体
              </button>
            </div>

            <div
              v-if="mode === 'knowledge' && knowledgeBases.length > 0"
              class="omni-bar__mount"
            >
              <label class="omni-mount-label">
                挂载知识库
                <select
                  v-model.number="knowledgeBaseId"
                  class="ds-select omni-select"
                  :disabled="loading"
                >
                  <option
                    :value="null"
                    disabled
                  >
                    请选择
                  </option>
                  <option
                    v-for="b in knowledgeBases"
                    :key="b.id"
                    :value="b.id"
                  >
                    {{ b.name }}
                  </option>
                </select>
              </label>
            </div>
            <div
              v-else-if="mode === 'knowledge' && knowledgeBases.length === 0"
              class="omni-hint"
            >
              暂无知识库，请先在「知识库」页创建。
            </div>

            <div
              v-if="mode === 'literature'"
              class="omni-bar__mount"
            >
              <label class="omni-mount-label">
                文献集合
                <select
                  v-model="literatureCollectionId"
                  class="ds-select omni-select"
                  :disabled="loading"
                >
                  <option value="">
                    请选择
                  </option>
                  <option
                    v-for="c in literatureCollections"
                    :key="c.id"
                    :value="c.id"
                  >
                    {{ c.label }}
                  </option>
                </select>
              </label>
              <button
                type="button"
                class="ds-btn ds-btn--ghost omni-refresh"
                :disabled="loading"
                @click="loadLiteratureCollections"
              >
                刷新列表
              </button>
            </div>

            <div
              v-if="mode === 'vision'"
              class="omni-bar__mount omni-bar__mount--wrap"
            >
              <p class="omni-vision-note">
                非流式调用；可在下方附加图片。默认 System / 视觉模型在「智能体」配置页修改。
              </p>
              <label
                v-if="knowledgeBases.length > 0"
                class="omni-check"
              >
                <input
                  v-model="visionUseKnowledgeBase"
                  type="checkbox"
                  :disabled="loading"
                >
                同时挂载知识库
              </label>
              <label
                v-if="visionUseKnowledgeBase && knowledgeBases.length > 0"
                class="omni-mount-label"
              >
                知识库
                <select
                  v-model.number="visionKnowledgeBaseId"
                  class="ds-select omni-select"
                  :disabled="loading"
                >
                  <option :value="null">
                    请选择
                  </option>
                  <option
                    v-for="b in knowledgeBases"
                    :key="b.id"
                    :value="b.id"
                  >
                    {{ b.name }}
                  </option>
                </select>
              </label>
            </div>
          </div>

          <details class="consult-adv">
            <summary class="consult-adv__summary">
              模型、RAG 参数与上下文
            </summary>
            <div class="consult-adv__body">
              <div class="ds-row consult-controls consult-controls--wrap">
                <label class="ds-field">
                  Temperature
                  <input
                    v-model.number="temperature"
                    class="ds-input ds-input--narrow"
                    type="number"
                    inputmode="decimal"
                    min="0"
                    max="2"
                    step="0.1"
                    :disabled="loading"
                  >
                </label>
                <label class="ds-field">
                  历史轮数上限
                  <input
                    v-model.number="maxHistoryTurns"
                    class="ds-input ds-input--narrow"
                    type="number"
                    inputmode="numeric"
                    min="1"
                    max="50"
                    step="1"
                    :disabled="loading"
                  >
                </label>
                <template v-if="mode === 'knowledge' || (mode === 'vision' && visionUseKnowledgeBase)">
                  <label class="ds-field">
                    RAG topK
                    <input
                      v-model.number="ragTopK"
                      class="ds-input ds-input--narrow"
                      type="number"
                      min="1"
                      max="20"
                      step="1"
                      :disabled="loading"
                    >
                  </label>
                  <label class="ds-field">
                    知识库相似度阈值
                    <input
                      v-model.number="ragSimilarityThreshold"
                      class="ds-input ds-input--narrow"
                      type="number"
                      inputmode="decimal"
                      min="0"
                      max="1"
                      step="0.05"
                      :disabled="loading"
                    >
                  </label>
                </template>
                <template v-if="mode === 'literature'">
                  <label class="ds-field">
                    文献 topK
                    <input
                      v-model.number="literatureTopK"
                      class="ds-input ds-input--narrow"
                      type="number"
                      min="1"
                      max="20"
                      step="1"
                      :disabled="loading"
                    >
                  </label>
                  <label class="ds-field">
                    文献相似度（0=不过滤）
                    <input
                      v-model.number="literatureThreshold"
                      class="ds-input ds-input--narrow"
                      type="number"
                      inputmode="decimal"
                      min="0"
                      max="1"
                      step="0.05"
                      :disabled="loading"
                    >
                  </label>
                </template>
              </div>
            </div>
          </details>
        </div>
        <div class="consult-header__side">
          <button
            v-if="loading"
            type="button"
            class="ds-btn ds-btn--warn consult-header__stop"
            @click="stop"
          >
            停止
          </button>
          <p
            v-if="sessionId != null"
            class="consult-meta"
            title="调试/技术支持用会话标识"
          >
            会话 #{{ sessionId }}
          </p>
        </div>
      </div>
    </header>

    <p
      v-if="error"
      class="ds-msg--error"
    >
      {{ error }}
    </p>
    <p
      v-if="ragMeta"
      class="ds-hint consult-rag-meta"
    >
      <template v-if="ragMeta.literatureCollectionId">
        本回合已注入文献库（ID {{ ragMeta.literatureCollectionId }}），检索到
        {{ ragMeta.retrievedChunks }} 条相关片段<span
          v-if="ragMeta.sources.length"
        >；来源：{{ ragMeta.sources.join('、') }}</span>。
      </template>
      <template v-else-if="ragMeta.knowledgeBaseId != null">
        本回合已注入知识库 #{{ ragMeta.knowledgeBaseId }}，检索到
        {{ ragMeta.retrievedChunks }} 条相关片段<span
          v-if="ragMeta.sources.length"
        >；来源：{{ ragMeta.sources.join('、') }}</span>。
      </template>
      <template v-else>
        检索到 {{ ragMeta.retrievedChunks }} 条相关片段<span
          v-if="ragMeta.sources.length"
        >；来源：{{ ragMeta.sources.join('、') }}</span>。
      </template>
    </p>
    <p
      v-if="loading && !streamingContent && mode !== 'vision'"
      class="ds-hint consult-thinking"
    >
      助手思考中…
    </p>
    <p
      v-if="loading && mode === 'vision'"
      class="ds-hint consult-thinking"
    >
      视觉智能体处理中（非流式）…
    </p>

    <div
      ref="threadEl"
      class="ds-thread consult-thread"
      role="region"
      aria-label="对话内容"
    >
      <div
        v-if="messages.length === 0 && !loading && !streamingContent"
        class="ds-thread-empty"
      >
        <p class="ds-thread-empty__title">
          开始一次问诊
        </p>
        <p class="ds-thread-empty__hint">
          顶部选择模式与挂载项；Enter 发送。「视觉智能体」模式下可上传图片，其它模式请用纯文本。
        </p>
      </div>
      <ChatBubble
        v-for="(m, i) in messages"
        :key="i"
        :role="m.role"
        :content="m.content"
      />
      <ChatBubble
        v-if="loading && streamingContent"
        role="assistant"
        :content="streamingContent"
      />
    </div>

    <form
      class="consult-composer"
      @submit.prevent="onSend"
    >
      <div
        v-if="mode === 'vision' && pendingImages.length > 0"
        class="consult-attachments"
      >
        <span
          v-for="(f, idx) in pendingImages"
          :key="idx + f.name"
          class="consult-attachments__chip"
        >
          {{ f.name }}
          <button
            type="button"
            class="consult-attachments__x"
            :disabled="loading"
            @click="removeImageAt(idx)"
          >
            ×
          </button>
        </span>
        <span
          v-if="pendingImages.length > 1"
          class="ds-hint"
        >将使用第一张图调用接口</span>
      </div>
      <div class="consult-composer__shell">
        <input
          ref="attachInput"
          type="file"
          class="consult-composer__file"
          accept="image/*"
          multiple
          :disabled="loading || mode !== 'vision'"
          @change="onAttachChange"
        >
        <button
          type="button"
          class="ds-btn ds-btn--ghost consult-composer__attach"
          :disabled="loading || mode !== 'vision'"
          title="上传图片（仅视觉智能体模式）"
          aria-label="上传附件或图片"
          @click="onAttachClick"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.8"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H3A1.5 1.5 0 0 0 1.5 6v12a1.5 1.5 0 0 0 1.5 1.5Zm10.5-11.25h.008v.008H12V8.25Z"
            />
          </svg>
        </button>
        <textarea
          v-model="input"
          class="consult-composer__input"
          rows="3"
          placeholder="描述症状、上传处方图说明、或基于文献提问…"
          :disabled="loading"
          @keydown.enter.exact.prevent="onSend"
        />
        <button
          type="submit"
          class="ds-btn ds-btn--primary ds-btn--icon consult-composer__send"
          :disabled="!canSend()"
          aria-label="发送"
          title="发送"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="2"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M6 12 3.269 3.125A59.769 59.769 0 0 1 21.485 12 59.768 59.768 0 0 1 3.27 20.875L5.999 12Zm0 0h7.5"
            />
          </svg>
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.consult-chat {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}

.consult-header {
  flex-shrink: 0;
  margin-bottom: 0.2rem;
}

.consult-header__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.consult-header__main {
  min-width: 0;
  flex: 1;
  text-align: left;
}

.consult-header__title {
  margin: 0 0 0.25rem;
}

.consult-header__status {
  margin: 0 0 0.35rem;
}

.omni-chip {
  font-size: 0.75rem;
  padding: 0.35rem 0.65rem;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: var(--transition-fast);
}

.omni-chip:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.omni-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.omni-chip--active {
  background: rgba(124, 58, 237, 0.12);
  border-color: var(--color-primary);
  color: var(--color-primary-hover);
  font-weight: 600;
}

.omni-bar {
  margin: 0.35rem 0 0.2rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
}

.omni-bar__label {
  font-size: 0.6875rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--color-muted);
}

.omni-bar__modes {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.omni-bar__mount {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
}

.omni-bar__mount--wrap {
  flex-direction: column;
  align-items: flex-start;
}

.omni-mount-label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

.omni-select {
  min-width: 12rem;
  max-width: min(100%, 22rem);
}

.omni-hint {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--color-muted);
}

.omni-vision-note {
  margin: 0;
  font-size: 0.75rem;
  color: var(--color-muted);
  max-width: 40rem;
}

.omni-check {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8125rem;
  cursor: pointer;
}

.omni-refresh {
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
}

.consult-header__side {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 0.6rem;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.consult-header__stop {
  font-size: 0.8125rem;
  padding-left: 0.75rem;
  padding-right: 0.75rem;
}

.consult-meta {
  margin: 0;
  font-size: 0.6875rem;
  color: var(--color-muted);
  font-variant-numeric: tabular-nums;
}

.consult-adv {
  margin: 0;
  margin-top: 0.1rem;
  padding: 0;
  border: none;
  background: transparent;
}

.consult-adv__summary {
  list-style: none;
  cursor: pointer;
  width: fit-content;
  max-width: 100%;
  margin: 0;
  padding: 0.1rem 0;
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--color-muted);
  border: none;
  background: transparent;
}

.consult-adv__summary::-webkit-details-marker {
  display: none;
}

.consult-adv__summary::after {
  content: ' ▾';
  font-size: 0.65rem;
  opacity: 0.8;
}

.consult-adv[open] > .consult-adv__summary {
  color: var(--color-text-secondary);
}

.consult-adv__summary:hover {
  color: var(--color-primary-hover);
}

.consult-adv__body {
  margin-top: 0.35rem;
  padding-top: 0.4rem;
  border-top: 1px dashed var(--color-border);
}

.consult-adv__select {
  min-width: 10rem;
  max-width: min(100%, 20rem);
}

.consult-controls {
  margin-top: 0;
}

.consult-controls--wrap {
  flex-wrap: wrap;
}

.consult-rag-meta {
  margin-top: 0.25rem;
  margin-bottom: 0;
  font-size: 0.8125rem;
}

.consult-thinking {
  margin-top: 0.15rem;
  margin-bottom: 0;
}

.consult-thread {
  flex: 1;
  min-height: 0;
}

.consult-composer {
  margin-top: 0.75rem;
  flex-shrink: 0;
}

.consult-attachments {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.4rem;
}

.consult-attachments__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  padding: 0.2rem 0.45rem;
  border-radius: var(--radius-sm);
  background: rgba(124, 58, 237, 0.08);
  border: 1px solid var(--color-border);
}

.consult-attachments__x {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0 0.15rem;
  line-height: 1;
  color: var(--color-muted);
}

.consult-attachments__x:hover:not(:disabled) {
  color: var(--color-danger);
}

.consult-composer__shell {
  position: relative;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: var(--transition-fast);
  overflow: hidden;
}

.consult-composer__shell:focus-within {
  border-color: var(--color-secondary);
  box-shadow: var(--focus-ring);
}

.consult-composer__file {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
}

.consult-composer__attach {
  position: absolute;
  bottom: 0.65rem;
  left: 0.55rem;
  z-index: 1;
  width: var(--ds-control-height);
  height: var(--ds-control-height);
  min-width: var(--ds-control-height);
  padding: 0;
  border-radius: var(--radius-control);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.consult-composer__input {
  display: block;
  width: 100%;
  margin: 0;
  box-sizing: border-box;
  padding: 12px 60px 12px 52px;
  min-height: 5.25rem;
  font-family: var(--font-body);
  font-size: 0.9375rem;
  line-height: 1.5;
  color: var(--color-text);
  background: var(--color-surface);
  border: none;
  border-radius: var(--radius-md);
  outline: none;
  resize: none !important;
  overflow-y: auto;
}

.consult-composer__input::-webkit-resizer {
  display: none;
  appearance: none;
}

.consult-composer__input::placeholder {
  color: var(--color-muted);
}

.consult-composer__input:focus {
  outline: none;
}

.consult-composer__send {
  position: absolute;
  bottom: 0.65rem;
  right: 0.65rem;
  width: var(--ds-control-height);
  height: var(--ds-control-height);
  border-radius: var(--radius-control);
}

@media (max-width: 52rem) {
  .consult-header__top {
    flex-direction: column;
    align-items: stretch;
  }

  .consult-header__side {
    justify-content: space-between;
  }
}
</style>
