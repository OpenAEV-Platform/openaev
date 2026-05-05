// Shared XTM One agent API helpers — used by TTP extraction,
// remediation detection, and text AI features to call XTM One
// agents through the OpenAEV proxy endpoints.

/** Read the Spring Security XSRF-TOKEN cookie value (needed for mutating requests). */
const getCsrfToken = (): string | null => {
  const match = document.cookie.split('; ').find(row => row.startsWith('XSRF-TOKEN='));
  return match ? decodeURIComponent(match.split('=')[1]) : null;
};

/** Ensure the XSRF-TOKEN cookie is set (same pattern as network.ts). */
let csrfBootstrapPromise: Promise<void> | null = null;
const ensureCsrfCookie = async (): Promise<void> => {
  if (getCsrfToken()) return;
  csrfBootstrapPromise ??= fetch('/csrf', { credentials: 'same-origin' })
    .then(() => undefined)
    .catch(() => undefined)
    .finally(() => { csrfBootstrapPromise = null; });
  await csrfBootstrapPromise;
};

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

export const fetchAgentsForIntent = async (intent: string): Promise<AgentOption[]> => {
  try {
    const response = await fetch(`/api/chatbot/agents?intent=${encodeURIComponent(intent)}`);
    if (!response.ok) return [];
    return await response.json();
  } catch {
    return [];
  }
};

export const callAgent = async (agentSlug: string, content: string): Promise<AgentResponse> => {
  await ensureCsrfCookie();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const csrf = getCsrfToken();
  if (csrf) headers['X-XSRF-TOKEN'] = csrf;

  const response = await fetch('/api/chatbot/agent', {
    method: 'POST',
    headers,
    body: JSON.stringify({
      agent_slug: agentSlug,
      content,
    }),
  });
  if (!response.ok) {
    return {
      content: '',
      status: 'error',
      error: `Agent call failed: ${response.statusText}`,
      code: response.status,
    };
  }
  const data = await response.json();
  return {
    content: data.content ?? '',
    status: data.status ?? 'success',
    error: data.error,
    code: data.code,
  };
};

/**
 * Stream an agent call via SSE. Calls `onChunk` with accumulated content
 * as each text chunk arrives. Returns the final AgentResponse.
 *
 * @param signal - optional AbortSignal to cancel the stream
 */
export const callAgentStream = async (
  agentSlug: string,
  content: string,
  onChunk: (partialContent: string) => void,
  signal?: AbortSignal,
): Promise<AgentResponse> => {
  await ensureCsrfCookie();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const csrf = getCsrfToken();
  if (csrf) headers['X-XSRF-TOKEN'] = csrf;

  const response = await fetch('/api/chatbot/agent/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify({
      agent_slug: agentSlug,
      content,
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
