// Formats the structured JSON response from the XTM One remediation agent
// into the HTML format expected by the CKEditor, matching the legacy
// Java formatters (DetectionRemediationCrowdstrikeResponse, etc.).

interface FieldConfiguration {
  grandparent_image_filename?: string;
  grandparent_command_line?: string;
  parent_image_filename?: string;
  parent_command_line?: string;
  image_filename?: string;
  command_line?: string;
  file_path?: string | null;
  remote_ip_address?: string | null;
  remote_port?: string | null;
  connection_type?: string | null;
  domain_name?: string | null;
}

interface CrowdStrikeRule {
  rule_type?: string;
  action_to_take?: string;
  severity?: string;
  rule_name?: string;
  rule_description?: string;
  tactic_technique?: string;
  detection_strategy?: string;
  field_configuration?: FieldConfiguration;
}

interface SplunkRule {
  spl_query?: string;
}

interface AgentRulesResponse {
  rules?: (CrowdStrikeRule | SplunkRule)[];
  spl_query?: string;
}

const escapeHtml = (s: string) => s
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;');

const formatCrowdStrikeRules = (rules: CrowdStrikeRule[]): string => {
  const parts: string[] = [];
  rules.forEach((rule, i) => {
    parts.push('<p>================================</p>');
    parts.push(`<p>Rule ${i + 1}</p>`);
    parts.push(`<p>Rule Type: ${escapeHtml(rule.rule_type ?? '')}</p>`);
    parts.push(`<p>Action to take: ${escapeHtml(rule.action_to_take ?? '')}</p>`);
    parts.push(`<p>Severity: ${escapeHtml(rule.severity ?? '')}</p>`);
    parts.push(`<p>Rule name: ${escapeHtml(rule.rule_name ?? '')}</p>`);
    parts.push(`<p>Rule description: ${escapeHtml(rule.rule_description ?? '')}</p>`);
    parts.push(`<p>Tactic &amp; Technique: ${escapeHtml(rule.tactic_technique ?? '')}</p>`);
    parts.push(`<p>Detection Strategy: ${escapeHtml(rule.detection_strategy ?? '')}</p>`);

    const fc = rule.field_configuration;
    if (fc) {
      parts.push('<p>Field Configuration: </p>');
      parts.push('<ul>');
      parts.push(`<li>Grandparent Image Filename: ${escapeHtml(fc.grandparent_image_filename ?? '.*')}</li>`);
      parts.push(`<li>Grandparent Command Line: ${escapeHtml(fc.grandparent_command_line ?? '.*')}</li>`);
      parts.push(`<li>Parent Image Filename: ${escapeHtml(fc.parent_image_filename ?? '.*')}</li>`);
      parts.push(`<li>Parent Command Line: ${escapeHtml(fc.parent_command_line ?? '.*')}</li>`);
      parts.push(`<li>Image Filename: ${escapeHtml(fc.image_filename ?? '.*')}</li>`);
      parts.push(`<li>Command Line: ${escapeHtml(fc.command_line ?? '.*')}</li>`);
      if (fc.file_path) parts.push(`<li>File Path: ${escapeHtml(fc.file_path)}</li>`);
      if (fc.remote_ip_address) parts.push(`<li>Remote IP Address: ${escapeHtml(fc.remote_ip_address)}</li>`);
      if (fc.remote_port) parts.push(`<li>Remote TCP/UDP Port: ${escapeHtml(fc.remote_port)}</li>`);
      if (fc.connection_type) parts.push(`<li>Connection Type: ${escapeHtml(fc.connection_type)}</li>`);
      if (fc.domain_name) parts.push(`<li>Domain Name: ${escapeHtml(fc.domain_name)}</li>`);
      parts.push('</ul>');
    }
  });
  return parts.join('\n');
};

const formatSplunkRules = (rules: SplunkRule[], raw: AgentRulesResponse): string => {
  // Splunk: return the SPL query as plain text
  if (raw.spl_query) return raw.spl_query;
  const first = rules[0];
  if (first?.spl_query) return first.spl_query;
  // Fallback: join all spl_query values
  return rules.map((r) => r.spl_query ?? '').filter(Boolean).join('\n\n');
};

/**
 * Parse the agent's JSON content and format it into the HTML expected by
 * the CKEditor, matching the legacy Java formatter output.
 * Falls back to raw content if parsing fails.
 */
const formatAgentRules = (content: string, collectorType: string): string => {
  // Try to extract JSON from the content (agent may wrap it in markdown code fences)
  let jsonStr = content.trim();
  const fenceMatch = jsonStr.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/);
  if (fenceMatch) {
    jsonStr = fenceMatch[1].trim();
  }

  let parsed: AgentRulesResponse;
  try {
    parsed = JSON.parse(jsonStr);
  } catch {
    // Not valid JSON — return as-is (might already be formatted)
    return content;
  }

  const rules = parsed.rules;
  if (!rules || !Array.isArray(rules) || rules.length === 0) {
    // No rules array — return raw content
    return content;
  }

  if (collectorType.toLowerCase().includes('splunk')) {
    return formatSplunkRules(rules as SplunkRule[], parsed);
  }

  // Default: CrowdStrike-style HTML formatting
  return formatCrowdStrikeRules(rules as CrowdStrikeRule[]);
};

export default formatAgentRules;
