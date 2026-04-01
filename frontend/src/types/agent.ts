export interface AgentRunResponse {
  assistant: string
  knowledgeSources: string[]
  mode: string
}

/** GET/PUT /v1/agent/config */
export interface AgentConfigView {
  displayName: string
  textSystemPrompt: string | null
  visionSystemPrompt: string | null
  visionModelName: string | null
  defaultKnowledgeBaseId: number | null
  updatedAt: string
}
