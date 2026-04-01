<script setup lang="ts">
import { onMounted, ref, watch, nextTick } from 'vue'
import { apiClient } from '@/api/client'
import { getErrorMessage } from '@/api/errors'
import type { ApiResult } from '@/types/api'
import type { KnowledgeBase } from '@/types/knowledge'
import ChatBubble from '@/components/ChatBubble.vue'
import { CONSULTATION_LAST_SESSION_KEY, useChat } from '@/composables/useChat'
import {
  formatHealthStatus,
  isHealthStatusErr,
  isHealthStatusOk,
} from '@/utils/formatHealthStatus'

const health = ref<string>('加载中…')
const threadEl = ref<HTMLElement | null>(null)
const input = ref('')
const temperature = ref(0.7)
const maxHistoryTurns = ref(10)
const knowledgeBases = ref<KnowledgeBase[]>([])
/** 空字符串表示问诊不注入知识库 */
const kbSelection = ref<string>('')
const ragTopK = ref(4)

const {
  sessions,
  sessionId,
  messages,
  loading,
  error,
  streamingContent,
  fetchSessions,
  openSession,
  newSession,
  deleteSession,
  send,
  stop,
} = useChat()

function formatSessionTime(iso: string) {
  try {
    return new Date(iso).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return ''
  }
}

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

onMounted(async () => {
  try {
    const { data } = await apiClient.get<ApiResult<string>>('/v1/consultation/health')
    health.value = formatHealthStatus(data.code, data.message ?? '')
  } catch (e) {
    health.value = `后端不可用: ${getErrorMessage(e)}`
  }
  void loadKnowledgeBases()
  try {
    await fetchSessions()
    const raw = localStorage.getItem(CONSULTATION_LAST_SESSION_KEY)
    const lastId = raw ? parseInt(raw, 10) : NaN
    if (
      Number.isFinite(lastId) &&
      sessions.value.some((s) => s.id === lastId)
    ) {
      await openSession(lastId)
    } else {
      await newSession()
    }
  } catch (e) {
    error.value = getErrorMessage(e)
    try {
      await newSession()
    } catch (e2) {
      error.value = getErrorMessage(e2)
    }
  }
})

async function onSend() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  const kb =
    kbSelection.value === '' ? null : Number.parseInt(kbSelection.value, 10)
  await send(text, {
    temperature: temperature.value,
    maxHistoryTurns: maxHistoryTurns.value,
    scrollRoot: threadEl.value,
    knowledgeBaseId: Number.isFinite(kb) ? kb : null,
    ragTopK: ragTopK.value,
  })
}

function onNewChat() {
  if (loading.value) stop()
  newSession().catch((e) => {
    error.value = getErrorMessage(e)
  })
}

async function onPickSession(id: number) {
  if (loading.value) stop()
  if (sessionId.value === id) return
  try {
    await openSession(id)
  } catch (e) {
    error.value = getErrorMessage(e)
  }
}

async function onDeleteSession(id: number, ev: Event) {
  ev.stopPropagation()
  if (!confirm('确定删除此会话及其所有消息？')) return
  try {
    await deleteSession(id)
  } catch (e) {
    error.value = getErrorMessage(e)
  }
}
</script>

<template>
  <div class="consult-layout ds-page ds-page--chat ds-main__grow">
    <aside
      class="consult-sessions"
      aria-label="历史会话"
    >
      <div class="consult-sessions__head">
        <span class="consult-sessions__title">历史会话</span>
        <button
          type="button"
          class="ds-btn ds-btn--secondary"
          :disabled="loading"
          @click="onNewChat"
        >
          新建
        </button>
      </div>
      <ul
        class="consult-sessions__list"
        role="list"
      >
        <li
          v-if="sessions.length === 0"
          class="consult-sessions__empty"
        >
          暂无记录，发送消息后将出现在此。
        </li>
        <li
          v-for="s in sessions"
          :key="s.id"
          :class="[
            'consult-sessions__item',
            sessionId === s.id ? 'consult-sessions__item--active' : '',
          ]"
        >
          <button
            type="button"
            class="consult-sessions__pick"
            @click="onPickSession(s.id)"
          >
            <span class="consult-sessions__item-title">{{ s.title }}</span>
            <span class="consult-sessions__item-meta">{{
              formatSessionTime(s.updatedAt)
            }}</span>
          </button>
          <button
            type="button"
            class="consult-sessions__del"
            title="删除会话"
            @click="onDeleteSession(s.id, $event)"
          >
            ×
          </button>
        </li>
      </ul>
    </aside>

    <div class="consult-chat">
      <header>
        <h2 class="ds-h2">
          中医智能问诊
        </h2>
        <p
          class="ds-status"
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
        <p
          v-if="sessionId != null"
          class="consult-meta"
        >
          当前会话 ID：{{ sessionId }}
        </p>
      </header>

      <details class="ds-details">
        <summary>模型、知识库与上下文</summary>
        <div class="ds-details__body">
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
            <label
              v-if="knowledgeBases.length > 0"
              class="ds-field"
            >
              关联知识库（可选）
              <select
                v-model="kbSelection"
                class="ds-input"
                :disabled="loading"
              >
                <option value="">不使用知识库</option>
                <option
                  v-for="b in knowledgeBases"
                  :key="b.id"
                  :value="String(b.id)"
                >
                  {{ b.name }}
                </option>
              </select>
            </label>
            <label
              v-if="knowledgeBases.length > 0 && kbSelection !== ''"
              class="ds-field"
            >
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
          </div>
        </div>
      </details>

      <div class="ds-row consult-actions">
        <button
          type="button"
          class="ds-btn ds-btn--secondary"
          :disabled="loading"
          @click="onNewChat"
        >
          新建会话
        </button>
        <button
          v-if="loading"
          type="button"
          class="ds-btn ds-btn--warn"
          @click="stop"
        >
          停止
        </button>
      </div>

      <p
        v-if="error"
        class="ds-msg--error"
      >
        {{ error }}
      </p>
      <p
        v-if="loading && !streamingContent"
        class="ds-hint"
        style="margin-top: 0"
      >
        助手思考中…
      </p>

      <div
        ref="threadEl"
        class="ds-thread"
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
            用自然语言描述症状或体质疑问；Enter
            发送。左侧可切换历史会话。
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
        class="ds-composer"
        @submit.prevent="onSend"
      >
        <textarea
          v-model="input"
          class="ds-textarea"
          rows="3"
          placeholder="描述症状、体征或想咨询的问题…"
          :disabled="loading"
          @keydown.enter.exact.prevent="onSend"
        />
        <button
          type="submit"
          class="ds-btn ds-btn--primary"
          :disabled="loading || !input.trim()"
        >
          发送
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.consult-layout {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
  width: 100%;
  min-height: min(28rem, 52dvh);
}
.consult-sessions {
  flex: 0 0 13.5rem;
  max-height: min(70vh, 32rem);
  overflow: auto;
  padding: 0.5rem 0.65rem;
  border-radius: var(--radius-md, 8px);
  border: 1px solid var(--color-border, #e5e5e5);
  background: var(--color-surface-subtle, #fafafa);
}
.consult-sessions__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}
.consult-sessions__title {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-text-secondary, #555);
}
.consult-sessions__list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.consult-sessions__empty {
  font-size: 0.8125rem;
  color: var(--color-text-secondary, #666);
  padding: 0.35rem 0;
}
.consult-sessions__item {
  display: flex;
  align-items: stretch;
  gap: 0.15rem;
  margin-bottom: 0.35rem;
  border-radius: var(--radius-sm, 6px);
  overflow: hidden;
  border: 1px solid transparent;
}
.consult-sessions__item--active {
  border-color: var(--color-primary, #0d6efd);
  background: var(--color-surface, #fff);
}
.consult-sessions__pick {
  flex: 1;
  text-align: left;
  padding: 0.4rem 0.5rem;
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  color: inherit;
}
.consult-sessions__pick:hover {
  background: rgba(0, 0, 0, 0.04);
}
.consult-sessions__item-title {
  display: block;
  font-size: 0.8125rem;
  font-weight: 500;
  line-clamp: 2;
  overflow-wrap: anywhere;
}
.consult-sessions__item-meta {
  display: block;
  font-size: 0.6875rem;
  color: var(--color-text-secondary, #888);
  margin-top: 0.15rem;
}
.consult-sessions__del {
  flex: 0 0 1.75rem;
  border: none;
  background: transparent;
  color: var(--color-text-secondary, #888);
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
  padding: 0.25rem;
}
.consult-sessions__del:hover {
  color: var(--color-danger, #c00);
}
.consult-chat {
  flex: 1;
  min-width: 0;
}
.consult-meta {
  margin: 0.25rem 0 0;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}
.consult-controls {
  margin-top: 0;
}
.consult-controls--wrap {
  flex-wrap: wrap;
}
.consult-actions {
  margin-top: 0.35rem;
  margin-bottom: 0.25rem;
  gap: 0.65rem;
}
@media (max-width: 52rem) {
  .consult-layout {
    flex-direction: column;
  }
  .consult-sessions {
    flex: none;
    width: 100%;
    max-height: 11rem;
  }
}
</style>
