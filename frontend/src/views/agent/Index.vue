<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiClient } from '@/api/client'
import { getErrorMessage } from '@/api/errors'
import type { ApiResult } from '@/types/api'
import type { AgentConfigView } from '@/types/agent'
import type { KnowledgeBase } from '@/types/knowledge'
import {
  formatHealthStatus,
  isHealthStatusErr,
  isHealthStatusOk,
} from '@/utils/formatHealthStatus'

const health = ref('加载中…')
const bases = ref<KnowledgeBase[]>([])
const loading = ref(false)
const loadErr = ref<string | null>(null)
const saveMsg = ref<string | null>(null)
const saveErr = ref<string | null>(null)

const displayName = ref('')
const textSystemPrompt = ref('')
const visionSystemPrompt = ref('')
const visionModelName = ref('')
const defaultKnowledgeBaseId = ref<number | null>(null)

async function refreshHealth() {
  try {
    const { data } = await apiClient.get<ApiResult<string>>('/v1/agent/health')
    health.value = formatHealthStatus(data.code, data.message ?? '')
  } catch (e) {
    health.value = getErrorMessage(e)
  }
}

async function loadBases() {
  try {
    const { data } = await apiClient.get<ApiResult<KnowledgeBase[]>>('/v1/knowledge/bases')
    if (data.code !== 0) throw new Error(data.message)
    bases.value = data.data ?? []
  } catch {
    bases.value = []
  }
}

async function loadConfig() {
  loadErr.value = null
  loading.value = true
  try {
    const { data } = await apiClient.get<ApiResult<AgentConfigView>>('/v1/agent/config')
    if (data.code !== 0) throw new Error(data.message)
    const c = data.data
    if (c) {
      displayName.value = c.displayName ?? ''
      textSystemPrompt.value = c.textSystemPrompt ?? ''
      visionSystemPrompt.value = c.visionSystemPrompt ?? ''
      visionModelName.value = c.visionModelName ?? ''
      defaultKnowledgeBaseId.value = c.defaultKnowledgeBaseId ?? null
    }
  } catch (e) {
    loadErr.value = getErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function saveConfig() {
  saveMsg.value = null
  saveErr.value = null
  loading.value = true
  try {
    const { data } = await apiClient.put<ApiResult<AgentConfigView>>('/v1/agent/config', {
      displayName: displayName.value.trim() || '中医智能体',
      textSystemPrompt: textSystemPrompt.value.trim() || null,
      visionSystemPrompt: visionSystemPrompt.value.trim() || null,
      visionModelName: visionModelName.value.trim() || null,
      defaultKnowledgeBaseId: defaultKnowledgeBaseId.value,
    })
    if (data.code !== 0) throw new Error(data.message)
    saveMsg.value = '已保存'
    await loadConfig()
  } catch (e) {
    saveErr.value = getErrorMessage(e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await refreshHealth()
  await loadBases()
  await loadConfig()
})
</script>

<template>
  <div
    class="ds-page agent-page"
  >
    <h2 class="ds-h2">
      智能体配置
    </h2>
    <p class="ds-lead agent-lead">
      编排视觉 / 文本智能体的 System Prompt 与默认模型；执行与多模态对话请在「智能问诊」中选择「视觉智能体」。
    </p>
    <p
      class="ds-status agent-health"
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
      v-if="loadErr"
      class="ds-msg--error"
    >
      {{ loadErr }}
    </p>

    <section class="ds-card">
      <h3 class="ds-h3 ds-card__title">
        基本与模型
      </h3>
      <div class="agent-fields">
        <label class="ds-field">
          显示名称
          <input
            v-model="displayName"
            class="ds-input"
            type="text"
            maxlength="200"
            :disabled="loading"
          >
        </label>
        <label class="ds-field">
          默认视觉模型（Ollama）
          <input
            v-model="visionModelName"
            class="ds-input"
            type="text"
            placeholder="例如 qwen3-vl:2b"
            :disabled="loading"
          >
        </label>
        <label
          v-if="bases.length"
          class="ds-field"
        >
          默认关联知识库（问诊视觉模式可预填）
          <select
            v-model.number="defaultKnowledgeBaseId"
            class="ds-select"
            :disabled="loading"
          >
            <option :value="null">
              不默认
            </option>
            <option
              v-for="b in bases"
              :key="b.id"
              :value="b.id"
            >
              {{ b.name }} (id={{ b.id }})
            </option>
          </select>
        </label>
      </div>
    </section>

    <section class="ds-card">
      <h3 class="ds-h3 ds-card__title">
        System Prompt
      </h3>
      <p class="ds-hint">
        留空则使用服务端内置默认文案。文本路径对应无图任务；视觉路径对应带图多模态调用。
      </p>
      <label class="ds-field agent-prompt-field">
        文本智能体 System
        <textarea
          v-model="textSystemPrompt"
          class="ds-textarea"
          rows="8"
          :disabled="loading"
        />
      </label>
      <label class="ds-field agent-prompt-field">
        视觉智能体 System
        <textarea
          v-model="visionSystemPrompt"
          class="ds-textarea"
          rows="10"
          :disabled="loading"
        />
      </label>
    </section>

    <div class="agent-footer">
      <button
        type="button"
        class="ds-btn ds-btn--primary"
        :disabled="loading"
        @click="saveConfig"
      >
        {{ loading ? '处理中…' : '保存配置' }}
      </button>
      <button
        type="button"
        class="ds-btn ds-btn--secondary"
        :disabled="loading"
        @click="loadConfig"
      >
        重新加载
      </button>
    </div>
    <p
      v-if="saveMsg"
      class="ds-msg--success"
    >
      {{ saveMsg }}
    </p>
    <p
      v-if="saveErr"
      class="ds-msg--error"
    >
      {{ saveErr }}
    </p>
  </div>
</template>

<style scoped>
.agent-page {
  max-width: 46rem;
}
.agent-lead {
  margin-top: -0.25rem;
  margin-bottom: 0.65rem;
  max-width: 40rem;
}
.agent-health {
  margin-bottom: 0.75rem;
}
.agent-fields {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.agent-prompt-field {
  margin-top: 0.75rem;
}
.agent-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1.25rem;
}
</style>
