// Helpers to robustly parse the JSON payload returned by XTM One agents for the
// phishing "Generate with AI" features. Agents may wrap their JSON in markdown
// code fences (```json ... ```), so we strip those before parsing and fall back
// to extracting the first {...} block if a direct parse fails.

export const stripJsonFences = (raw: string): string => {
  let s = (raw ?? '').trim();
  const fenceStart = /^```[a-zA-Z]*\s*/;
  const fenceEnd = /\s*```$/;
  if (fenceStart.test(s)) {
    s = s.replace(fenceStart, '').replace(fenceEnd, '');
  }
  return s.trim();
};

export const parseAgentJson = (raw: string): Record<string, unknown> | null => {
  const stripped = stripJsonFences(raw);
  try {
    return JSON.parse(stripped) as Record<string, unknown>;
  } catch {
    const match = stripped.match(/\{[\s\S]*\}/);
    if (match) {
      try {
        return JSON.parse(match[0]) as Record<string, unknown>;
      } catch {
        return null;
      }
    }
    return null;
  }
};
