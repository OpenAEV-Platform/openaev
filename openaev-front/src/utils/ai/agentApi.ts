// Shared XTM One agent API helpers — used by TTP extraction,
// remediation detection, and text AI features to call XTM One
// agents through the OpenAEV proxy endpoints.

import { api, ensureCsrf, getCsrfToken } from '../../network';

export interface AgentOption {
  id: string;
  name: string;
  slug: string;
  description?: string;
}

export interface AgentResponse {
  content: string;
  status: 'success' | 'error';
  error?: string;
  code?: number;
}

// Bounded wait before giving up on the agents listing call so the UI never
// gets stuck on a spinner when XTM One is unreachable.
const AGENTS_FETCH_TIMEOUT_MS = 15_000;

export const fetchAgentsForIntent = async (intent: string): Promise<AgentOption[]> => {
  try {
    const { data } = await api().get(
      `/api/chatbot/agents?intent=${encodeURIComponent(intent)}`,
      { timeout: AGENTS_FETCH_TIMEOUT_MS },
    );
    return data;
  } catch {
    return [];
  }
};

/**
 * Stream an agent call via SSE. Calls `onChunk` with accumulated content
 * as each text chunk arrives. Returns the final AgentResponse.
 *
 * Uses raw fetch for ReadableStream support (axios doesn't support SSE streaming).
 * CSRF is bootstrapped through the shared `ensureCsrf` helper from `network.ts`.
 *
 * @param signal - optional AbortSignal to cancel the stream
 * @param intent - optional feature intent, forwarded for telemetry only: the backend maps known
 *   feature intents to the same backend-agnostic per-feature usage counter as the legacy AI
 *   endpoints. It never influences routing.
 */
export const callAgentStream = async (
  agentSlug: string,
  content: string,
  onChunk: (partialContent: string) => void,
  signal?: AbortSignal,
  intent?: string,
): Promise<AgentResponse> => {
  // Bootstrap the XSRF-TOKEN cookie if missing — reuses the shared network.ts helper so that
  // cookie name, decoding and bootstrap behaviour stay in sync with axios callers.
  await ensureCsrf();

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const csrf = getCsrfToken();
  if (csrf) headers['X-XSRF-TOKEN'] = csrf;

  const response = await fetch('/api/chatbot/agent/stream', {
    method: 'POST',
    headers,
    credentials: 'include',
    body: JSON.stringify({
      agent_slug: agentSlug,
      content,
      ...(intent ? { intent } : {}),
    }),
    signal,
  });

  if (!response.ok) {
    return {
      content: '',
      status: 'error',
      error: `Agent call failed: ${response.statusText}`,
      code: response.status,
    };
  }

  const reader = response.body?.getReader();
  if (!reader) {
    return {
      content: '',
      status: 'error',
      error: 'No response stream',
    };
  }

  const decoder = new TextDecoder();
  let accumulated = '';
  let buffer = '';
  let lastError: string | undefined;

  try {
    for (;;) {
      // eslint-disable-next-line no-await-in-loop
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      // Parse SSE lines: "data: {...}\n\n"
      const lines = buffer.split('\n');
      // Keep the last potentially incomplete line in the buffer
      buffer = lines.pop() ?? '';

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || !trimmed.startsWith('data: ')) continue;

        try {
          const event = JSON.parse(trimmed.slice(6));
          if (event.type === 'stream' && typeof event.content === 'string') {
            accumulated += event.content;
            onChunk(accumulated);
          } else if (event.type === 'done' && typeof event.content === 'string') {
            // Final message — use authoritative full content
            accumulated = event.content;
            onChunk(accumulated);
          } else if (event.type === 'error') {
            lastError = event.content ?? 'Unknown error';
          }
          // Ignore status events (thinking, tool_start, etc.)
        } catch {
          // Skip malformed lines
        }
      }
    }
  } finally {
    reader.releaseLock();
  }

  if (lastError) {
    return {
      content: lastError,
      status: 'error',
      error: lastError,
    };
  }
  return {
    content: accumulated,
    status: 'success',
  };
};
