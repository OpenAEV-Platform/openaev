// Shared XTM One agent API helpers — used by TTP extraction,
// remediation detection, and text AI features to call XTM One
// agents through the OpenAEV proxy endpoints.

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
  const response = await fetch('/api/chatbot/agent', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ agent_slug: agentSlug, content }),
  });
  if (!response.ok) {
    return { content: '', status: 'error', error: `Agent call failed: ${response.statusText}`, code: response.status };
  }
  const data = await response.json();
  return {
    content: data.content ?? '',
    status: data.status ?? 'success',
    error: data.error,
    code: data.code,
  };
};

/** Call an agent with file attachments (for TTP extraction). */
export const callAgentWithFiles = async (agentSlug: string, files: File[], text: string): Promise<AgentResponse> => {
  // Convert files to inline base64 objects matching copilot's PlatformFileInput schema.
  const fileInputs: { filename: string; content_type: string; data: string }[] = [];
  for (const file of files) {
    const buffer = await file.arrayBuffer();
    const base64 = btoa(
      new Uint8Array(buffer).reduce((data, byte) => data + String.fromCharCode(byte), ''),
    );
    fileInputs.push({ filename: file.name, content_type: file.type || 'application/octet-stream', data: base64 });
  }

  const response = await fetch('/api/chatbot/agent', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ agent_slug: agentSlug, content: text, files: fileInputs }),
  });
  if (!response.ok) {
    return { content: '', status: 'error', error: `Agent call failed: ${response.statusText}`, code: response.status };
  }
  const data = await response.json();
  return {
    content: data.content ?? '',
    status: data.status ?? 'success',
    error: data.error,
    code: data.code,
  };
};
