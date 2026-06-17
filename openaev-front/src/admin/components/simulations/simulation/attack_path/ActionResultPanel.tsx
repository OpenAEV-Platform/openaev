import {
  AccountTreeOutlined,
  BugReportOutlined,
  Close as CloseIcon,
  DevicesOutlined,
  FolderOpenOutlined,
  GroupOutlined,
  KeyOutlined,
  LanOutlined,
  PersonOutlined,
  ScheduleOutlined,
  ShieldOutlined,
  WarningAmberOutlined,
} from '@mui/icons-material';
import { Box, Chip, Divider, Drawer, IconButton, Tab, Tabs, Typography } from '@mui/material';
import React, { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import {
  type AttackPathExpectation,
  type AttackPathNode,
  getNodeStatus,
  STATUS_COLORS,
} from './attackPathUtils';

interface ActionResultPanelProps {
  node: AttackPathNode | null;
  allNodes: AttackPathNode[];
  onClose: () => void;
}

// ── Small reusable layout pieces ────────────────────────────────────────────

const SectionTitle: FunctionComponent<{ icon: React.ReactNode; label: string }> = ({ icon, label }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '14px 0 8px' }}>
    <span style={{ display: 'flex', opacity: 0.6, fontSize: 16 }}>{icon}</span>
    <Typography variant="overline" sx={{ lineHeight: 1, fontSize: 10, fontWeight: 700, opacity: 0.6, letterSpacing: 1 }}>
      {label}
    </Typography>
  </div>
);

const InfoRow: FunctionComponent<{ label: string; value: React.ReactNode }> = ({ label, value }) => (
  <div style={{ display: 'flex', gap: 8, padding: '3px 0', fontSize: 12 }}>
    <span style={{ opacity: 0.45, minWidth: 100, flexShrink: 0, fontSize: 12 }}>{label}</span>
    <span style={{ opacity: 0.9, wordBreak: 'break-all', fontSize: 12 }}>{value}</span>
  </div>
);

// ── Tab: Result ───────────────────────────────────────────────────────────────

const ResultTab: FunctionComponent<{ node: AttackPathNode; prevNode: AttackPathNode | null; nextNode: AttackPathNode | null }> = ({
  node, prevNode, nextNode,
}) => {
  const { t, fldt } = useFormatter();
  const status = getNodeStatus(node);
  const colors = STATUS_COLORS[status];

  return (
    <div style={{ padding: '0 16px 16px', overflowY: 'auto', flex: 1 }}>

      {/* ── Target Information ─────────────────────────── */}
      <SectionTitle icon={<DevicesOutlined fontSize="inherit" />} label={t('Target Information')} />
      {node.node_hostname && <InfoRow label={t('Hostname')} value={node.node_hostname} />}
      {node.node_ip && <InfoRow label={t('IP Address')} value={<span style={{ fontFamily: 'monospace' }}>{node.node_ip}</span>} />}
      {node.node_platform && <InfoRow label={t('Platform')} value={node.node_platform} />}
      {node.node_user_privileges && (
        <InfoRow
          label={t('User / Privileges')}
          value={(
            <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <PersonOutlined style={{ fontSize: 13, opacity: 0.7 }} />
              <span style={{ color: '#ff9800' }}>{node.node_user_privileges}</span>
            </span>
          )}
        />
      )}
      {node.node_executed_at && <InfoRow label={t('Executed At')} value={fldt(node.node_executed_at)} />}

      {/* ── Outcome ──────────────────────────────────────── */}
      <Divider sx={{ my: 1, opacity: 0.15 }} />
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 0' }}>
        <div style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: colors.fill, flexShrink: 0 }} />
        <Typography variant="body2" sx={{ fontWeight: 600, fontSize: 12 }}>
          {t('Outcome')}
        </Typography>
        <Chip
          label={status.charAt(0).toUpperCase() + status.slice(1)}
          size="small"
          sx={{
            ml: 'auto',
            height: 20,
            fontSize: 10,
            fontWeight: 700,
            backgroundColor: `${colors.fill}28`,
            color: colors.fill,
            border: `1px solid ${colors.fill}60`,
          }}
        />
      </div>

      {/* ── Expectations ─────────────────────────────────── */}
      {node.node_expectations && node.node_expectations.length > 0 && (
        <>
          <Divider sx={{ my: 1, opacity: 0.15 }} />
          <SectionTitle icon={<ShieldOutlined fontSize="inherit" />} label={t('Expectations')} />
          {node.node_expectations.map((exp: AttackPathExpectation) => {
            const expColor = exp.expectation_status === 'SUCCESS' ? '#4caf50'
              : exp.expectation_status === 'FAILED' ? '#f44336' : '#9e9e9e';
            return (
              <div key={exp.expectation_id} style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '5px 8px',
                marginBottom: 4,
                borderRadius: 4,
                backgroundColor: `${expColor}0d`,
                border: `1px solid ${expColor}28`,
              }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: expColor, flexShrink: 0 }} />
                <span style={{ flex: 1, fontSize: 12, opacity: 0.85 }}>{exp.expectation_type}</span>
                <Chip
                  label={exp.expectation_status}
                  size="small"
                  sx={{
                    height: 18,
                    fontSize: 10,
                    fontWeight: 600,
                    backgroundColor: `${expColor}20`,
                    color: expColor,
                  }}
                />
                {exp.expectation_score !== null && (
                  <span style={{ opacity: 0.45, fontSize: 10, whiteSpace: 'nowrap' }}>
                    {exp.expectation_score}/{exp.expectation_expected_score}
                  </span>
                )}
              </div>
            );
          })}
        </>
      )}

      {/* ── Accessed Files ─────────────────────────────── */}
      {node.node_accessed_files && node.node_accessed_files.length > 0 && (
        <>
          <Divider sx={{ my: 1, opacity: 0.15 }} />
          <SectionTitle icon={<FolderOpenOutlined fontSize="inherit" />} label={t('Accessed Files')} />
          {node.node_accessed_files.map((file) => (
            <div key={file} style={{
              fontSize: 11,
              fontFamily: 'monospace',
              padding: '3px 8px',
              marginBottom: 3,
              backgroundColor: 'rgba(255,152,0,0.07)',
              borderLeft: '2px solid rgba(255,152,0,0.5)',
              borderRadius: 2,
              wordBreak: 'break-all',
            }}>
              {file}
            </div>
          ))}
        </>
      )}

      {/* ── Credentials Found ──────────────────────────── */}
      {node.node_credentials_found && node.node_credentials_found.length > 0 && (
        <>
          <Divider sx={{ my: 1, opacity: 0.15 }} />
          <SectionTitle icon={<KeyOutlined fontSize="inherit" />} label={t('Credentials Found')} />
          {node.node_credentials_found.map((cred) => (
            <div key={cred} style={{
              fontSize: 11,
              fontFamily: 'monospace',
              padding: '3px 8px',
              marginBottom: 3,
              backgroundColor: 'rgba(244,67,54,0.07)',
              borderLeft: '2px solid rgba(244,67,54,0.4)',
              borderRadius: 2,
              wordBreak: 'break-all',
            }}>
              {cred}
            </div>
          ))}
        </>
      )}

      {/* ── Ports Discovered ───────────────────────────── */}
      {node.node_ports_found && node.node_ports_found.length > 0 && (
        <>
          <Divider sx={{ my: 1, opacity: 0.15 }} />
          <SectionTitle icon={<LanOutlined fontSize="inherit" />} label={t('Ports Discovered')} />
          {node.node_ports_found.map((port) => (
            <div key={port} style={{
              fontSize: 11,
              fontFamily: 'monospace',
              padding: '3px 8px',
              marginBottom: 3,
              backgroundColor: 'rgba(6,182,212,0.07)',
              borderLeft: '2px solid rgba(6,182,212,0.5)',
              borderRadius: 2,
              wordBreak: 'break-all',
            }}>
              {port}
            </div>
          ))}
        </>
      )}

      {/* ── Users Discovered ───────────────────────────── */}
      {node.node_users_found && node.node_users_found.length > 0 && (
        <>
          <Divider sx={{ my: 1, opacity: 0.15 }} />
          <SectionTitle icon={<GroupOutlined fontSize="inherit" />} label={t('Users Discovered')} />
          {node.node_users_found.map((user) => (
            <div key={user} style={{
              fontSize: 11,
              fontFamily: 'monospace',
              padding: '3px 8px',
              marginBottom: 3,
              backgroundColor: 'rgba(168,85,247,0.07)',
              borderLeft: '2px solid rgba(168,85,247,0.4)',
              borderRadius: 2,
              wordBreak: 'break-all',
            }}>
              {user}
            </div>
          ))}
        </>
      )}

      {/* ── CVEs Identified ────────────────────────────── */}
      {node.node_cves_found && node.node_cves_found.length > 0 && (
        <>
          <Divider sx={{ my: 1, opacity: 0.15 }} />
          <SectionTitle icon={<BugReportOutlined fontSize="inherit" />} label={t('CVEs Identified')} />
          {node.node_cves_found.map((cve) => (
            <div key={cve} style={{
              fontSize: 11,
              fontFamily: 'monospace',
              padding: '3px 8px',
              marginBottom: 3,
              backgroundColor: 'rgba(239,68,68,0.07)',
              borderLeft: '2px solid rgba(239,68,68,0.5)',
              borderRadius: 2,
              wordBreak: 'break-all',
            }}>
              {cve}
            </div>
          ))}
        </>
      )}

      {/* ── Chain Context ──────────────────────────────── */}
      {(prevNode || nextNode) && (
        <>
          <Divider sx={{ my: 1, opacity: 0.15 }} />
          <SectionTitle icon={<AccountTreeOutlined fontSize="inherit" />} label={t('Chaining Context')} />
          <div style={{ display: 'flex', gap: 8 }}>
            {prevNode && (
              <div style={{
                flex: 1,
                padding: '8px 10px',
                borderRadius: 4,
                backgroundColor: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
              }}>
                <div style={{ fontSize: 9, opacity: 0.45, marginBottom: 4, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.8 }}>
                  {t('Previous')}
                </div>
                <div style={{ fontSize: 12, fontWeight: 600 }}>{prevNode.node_label}</div>
                {prevNode.node_hostname && <div style={{ fontSize: 10, opacity: 0.5 }}>→ {prevNode.node_hostname}</div>}
                <Chip
                  label={getNodeStatus(prevNode)}
                  size="small"
                  sx={{
                    mt: 0.5,
                    height: 16,
                    fontSize: 9,
                    backgroundColor: `${STATUS_COLORS[getNodeStatus(prevNode)].fill}22`,
                    color: STATUS_COLORS[getNodeStatus(prevNode)].fill,
                  }}
                />
              </div>
            )}
            {nextNode && (
              <div style={{
                flex: 1,
                padding: '8px 10px',
                borderRadius: 4,
                backgroundColor: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
              }}>
                <div style={{ fontSize: 9, opacity: 0.45, marginBottom: 4, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.8 }}>
                  {t('Next')}
                </div>
                <div style={{ fontSize: 12, fontWeight: 600 }}>{nextNode.node_label}</div>
                {nextNode.node_hostname && <div style={{ fontSize: 10, opacity: 0.5 }}>→ {nextNode.node_hostname}</div>}
                <Chip
                  label={getNodeStatus(nextNode)}
                  size="small"
                  sx={{
                    mt: 0.5,
                    height: 16,
                    fontSize: 9,
                    backgroundColor: `${STATUS_COLORS[getNodeStatus(nextNode)].fill}22`,
                    color: STATUS_COLORS[getNodeStatus(nextNode)].fill,
                  }}
                />
              </div>
            )}
          </div>
        </>
      )}

    </div>
  );
};

// ── Terminal tab helpers ──────────────────────────────────────────────────────

/** Returns the base executable name from the payload (e.g. "netexec smb", "nmap"). */
function getBaseCommand(node: AttackPathNode): string {
  const payload = (node.node_payload_name ?? node.node_label ?? '').toLowerCase();
  if (payload.includes('nmap')) return 'nmap';
  if (payload.includes('netexec') || payload.includes('nxc')) {
    if (payload.includes('smb')) return 'netexec smb';
    if (payload.includes('ssh')) return 'netexec ssh';
    if (payload.includes('ldap')) return 'netexec ldap';
    if (payload.includes('rdp')) return 'netexec rdp';
    if (payload.includes('wmi')) return 'netexec wmi';
    return 'netexec';
  }
  if (payload.includes('nuclei')) return 'nuclei';
  if (payload.includes('mimikatz')) return 'mimikatz.exe';
  if (payload.includes('secretsdump') || payload.includes('ntds')) return 'impacket-secretsdump';
  if (payload.includes('bloodhound')) return 'bloodhound-python';
  if (payload.includes('pass-the-hash') || payload.includes('pth')) return 'netexec smb';
  if (payload.includes('kerberoast')) return 'netexec ldap';
  return node.node_payload_name ?? node.node_label ?? 'unknown';
}

/**
 * Full command string shown in the Terminal tab.
 *
 * Priority:
 *  1. node_command  — verbatim if set
 *  2. node_arguments — prepend base executable
 *  3. Synthesize from available fields (ip, hostname, credentials, payload module)
 */
export function synthesizeCommand(node: AttackPathNode): string {
  if (node.node_command) return node.node_command;
  const base = getBaseCommand(node);

  // If explicit arguments are present, they already contain the target
  if (node.node_arguments) {
    return `${base} ${node.node_arguments}`;
  }

  // Synthesize from available data
  const parts: string[] = [base];
  const target = node.node_ip ?? node.node_hostname ?? '';
  if (target) parts.push(target);

  // Add credential flags if available
  if (node.node_credentials_found?.length) {
    const cred = node.node_credentials_found[0];
    const colonIdx = cred.indexOf(':');
    if (colonIdx > 0) {
      const user = cred.slice(0, colonIdx);
      const pass = cred.slice(colonIdx + 1);
      // NTLM hash detection (32-char hex or LM:NT format)
      const isHash = /^[0-9a-f]{32}$/i.test(pass) || /^[0-9a-f]{32}:[0-9a-f]{32}$/i.test(pass) || pass.includes('aad3b435');
      parts.push(`-u "${user}"`);
      parts.push(isHash ? `-H "${pass}"` : `-p "${pass}"`);
    }
  } else if (node.node_user_privileges) {
    const match = node.node_user_privileges.match(/^([^\s(→\n]+)/);
    if (match) parts.push(`-u "${match[1]}"`);
  }

  // Add module from payload name (e.g. "netexec – SMB credential spray" → no module)
  // but "netexec – gpp_password" → -M gpp_password
  if (node.node_payload_name) {
    const sep = node.node_payload_name.split(/\s[–-]\s/);
    const mod = sep[1]?.trim();
    if (mod && !mod.toLowerCase().includes('spray') && !mod.toLowerCase().includes('scan') && !mod.toLowerCase().includes('discovery')) {
      parts.push(`-M ${mod}`);
    }
  }

  return parts.join(' ');
}

export interface ArgEntry { key: string; value: string; sensitive: boolean }

/** Flags whose values should be masked (password-style). Context-sensitive: not masked for nmap. */
function isSensitiveFlag(flag: string, node: AttackPathNode): boolean {
  const payload = (node.node_payload_name ?? '').toLowerCase();
  const isNmap = payload.includes('nmap');
  // nmap uses -p for ports — never mask
  if (isNmap) return false;
  return ['-p', '-P', '--password', '--pass', '--hash', '-H'].includes(flag);
}

/**
 * Parses node_arguments into structured key/value pairs for the Arguments section.
 * Only shows what was actually passed as input arguments — nothing inferred.
 */
export function synthesizeArguments(node: AttackPathNode): ArgEntry[] {
  if (!node.node_arguments) return [];

  const str = node.node_arguments;
  const args: ArgEntry[] = [];

  // Tokenize respecting double-quoted and single-quoted values
  const tokens = str.match(/(?:[^\s"']+|"[^"]*"|'[^']*')+/g) ?? [];

  let i = 0;
  while (i < tokens.length) {
    const tok = tokens[i];

    if (tok.startsWith('-')) {
      if (tok.includes('=')) {
        // --key=value form
        const eq = tok.indexOf('=');
        const key = tok.slice(0, eq);
        const val = tok.slice(eq + 1).replace(/^["']|["']$/g, '');
        const sensitive = isSensitiveFlag(key, node);
        args.push({ key, value: sensitive ? '••••••••' : val, sensitive });
      } else if (i + 1 < tokens.length && !tokens[i + 1].startsWith('-')) {
        // -flag value form
        const key = tok;
        const val = tokens[i + 1].replace(/^["']|["']$/g, '');
        const sensitive = isSensitiveFlag(key, node);
        args.push({ key, value: sensitive ? '••••••••' : val, sensitive });
        i++;
      } else {
        // standalone flag (e.g. -sS, -sV, --ntds)
        args.push({ key: tok, value: '', sensitive: false });
      }
    }
    // positional args (IPs, hostnames) are already shown in the Command line — skip here

    i++;
  }

  return args;
}

// ── Tab: Terminal ─────────────────────────────────────────────────────────────

const BOX_STYLE: React.CSSProperties = {
  backgroundColor: 'rgba(15,18,30,0.9)',
  border: '1px solid rgba(255,255,255,0.08)',
  borderRadius: 8,
  padding: '12px 14px',
  marginBottom: 10,
};

const TerminalTab: FunctionComponent<{ node: AttackPathNode }> = ({ node }) => {
  const command = synthesizeCommand(node);
  const args    = synthesizeArguments(node);

  return (
    <div style={{ padding: '12px 16px 16px', flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 0 }}>

      {/* Command box */}
      <div style={BOX_STYLE}>
        <div style={{ fontSize: 10, fontWeight: 700, color: 'rgba(255,255,255,0.35)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>
          Command
        </div>
        <div style={{
          fontFamily: '"JetBrains Mono", "Fira Code", monospace',
          fontSize: 12,
          color: '#58a6ff',
          background: '#0d1117',
          borderRadius: 5,
          padding: '8px 12px',
          wordBreak: 'break-all',
        }}>
          {command}
        </div>
      </div>

      {/* Arguments box */}
      <div style={BOX_STYLE}>
        <div style={{ fontSize: 10, fontWeight: 700, color: 'rgba(255,255,255,0.35)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>
          Arguments
        </div>
        {args.length === 0 ? (
          <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.3)', fontStyle: 'italic' }}>No arguments recorded</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {args.map((arg, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{
                  fontSize: 10, fontWeight: 700, color: 'rgba(255,255,255,0.4)',
                  minWidth: 80, textAlign: 'right', textTransform: 'lowercase',
                  fontFamily: 'monospace',
                }}>
                  {arg.key}
                </span>
                <span style={{ color: 'rgba(255,255,255,0.2)', fontSize: 12 }}>:</span>
                <span style={{
                  fontFamily: '"JetBrains Mono", "Fira Code", monospace',
                  fontSize: 12,
                  color: arg.sensitive ? '#f87171' : '#e3b341',
                  background: 'rgba(255,255,255,0.04)',
                  borderRadius: 4,
                  padding: '1px 8px',
                  wordBreak: 'break-all',
                }}>
                  {arg.value}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Terminal output */}
      {node.node_terminal_output && (
        <div style={{
          backgroundColor: 'rgba(15,18,30,0.9)',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 8,
          padding: '12px 14px',
        }}>
          <div style={{ fontSize: 10, fontWeight: 700, color: 'rgba(255,255,255,0.35)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 8 }}>
            Terminal Output
          </div>
          <pre style={{
            margin: 0,
            padding: '10px 12px',
            backgroundColor: '#0d1117',
            color: '#39d353',
            fontFamily: '"JetBrains Mono", "Fira Code", "Cascadia Code", monospace',
            fontSize: 10.5,
            borderRadius: 5,
            overflowY: 'auto',
            maxHeight: 280,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
            lineHeight: 1.6,
            border: '1px solid rgba(57,211,83,0.12)',
          }}>
            {node.node_terminal_output}
          </pre>
        </div>
      )}

    </div>
  );
};

// ── Main Panel Component ──────────────────────────────────────────────────────

const ActionResultPanel: FunctionComponent<ActionResultPanelProps> = ({ node, allNodes, onClose }) => {
  const { t } = useFormatter();
  const [activeTab, setActiveTab] = useState(0);

  const status = node ? getNodeStatus(node) : 'pending';
  const colors = STATUS_COLORS[status];

  const prevNode = node?.node_chain_previous
    ? allNodes.find((n) => n.node_id === node.node_chain_previous) ?? null
    : null;
  const nextNode = node?.node_chain_next
    ? allNodes.find((n) => n.node_id === node.node_chain_next) ?? null
    : null;

  return (
    <Drawer
      anchor="right"
      open={!!node}
      onClose={onClose}
      sx={{ zIndex: 9999 }}
      ModalProps={{ style: { zIndex: 9999 } }}
      PaperProps={{
        sx: {
          width: 480,
          maxWidth: '90vw',
          backgroundColor: 'background.paper',
          backgroundImage: 'none',
          height: '100vh',
          top: 0,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          zIndex: 9999,
        },
      }}
    >
      {node && (<>
      {/* ── Header ─────────────────────────────────────── */}
      <div style={{
        padding: '12px 16px',
        borderBottom: '1px solid rgba(255,255,255,0.08)',
        flexShrink: 0,
      }}>
        {/* Title row */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
          <div style={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            backgroundColor: colors.fill,
            flexShrink: 0,
            boxShadow: `0 0 6px ${colors.fill}`,
          }} />
          <Typography variant="subtitle2" sx={{ flex: 1, fontWeight: 700, fontSize: 13, lineHeight: 1.3 }}>
            {node.node_label}
          </Typography>
          <Chip
            label={status.charAt(0).toUpperCase() + status.slice(1)}
            size="small"
            sx={{
              height: 20,
              fontSize: 10,
              fontWeight: 700,
              backgroundColor: `${colors.fill}22`,
              color: colors.fill,
              border: `1px solid ${colors.fill}50`,
            }}
          />
          <IconButton onClick={onClose} size="small" sx={{ color: 'rgba(255,255,255,0.5)', '&:hover': { color: '#fff' } }}>
            <CloseIcon style={{ fontSize: 16 }} />
          </IconButton>
        </div>

        {/* Inject / payload chip */}
        {node.node_payload_name && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 2 }}>
            <ScheduleOutlined style={{ fontSize: 12, opacity: 0.45 }} />
            <span style={{ fontSize: 11, opacity: 0.55, fontFamily: 'monospace' }}>{node.node_payload_name}</span>
          </div>
        )}

        {/* Target line */}
        {(node.node_hostname || node.node_ip) && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
            <DevicesOutlined style={{ fontSize: 12, opacity: 0.45 }} />
            <span style={{ fontSize: 11, opacity: 0.65 }}>
              {node.node_hostname}
              {node.node_ip && <span style={{ opacity: 0.55 }}> · {node.node_ip}</span>}
            </span>
            {node.node_platform && (
              <Chip
                label={node.node_platform}
                size="small"
                sx={{ height: 16, fontSize: 9, opacity: 0.6, ml: 0.5 }}
              />
            )}
          </div>
        )}

        {/* Warning if no credentials / files found */}
        {(!node.node_credentials_found?.length && !node.node_accessed_files?.length
          && !node.node_ports_found?.length && !node.node_users_found?.length
          && !node.node_cves_found?.length && status === 'undetected') && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
            <WarningAmberOutlined sx={{ fontSize: 12, color: '#ff9800' }} />
            <Typography variant="caption" sx={{ color: '#ff9800', fontSize: 10 }}>
              {t('Undetected — no artifacts captured')}
            </Typography>
          </Box>
        )}
      </div>

      {/* ── Tabs ───────────────────────────────────────── */}
      <Tabs
        value={activeTab}
        onChange={(_e, v) => setActiveTab(v)}
        sx={{
          minHeight: 36,
          flexShrink: 0,
          borderBottom: '1px solid rgba(255,255,255,0.08)',
          '& .MuiTab-root': { minHeight: 36, fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.8 },
          '& .MuiTabs-indicator': { backgroundColor: colors.fill },
        }}
      >
        <Tab label={t('Result')} />
        <Tab label={t('Terminal')} />
      </Tabs>

      {/* ── Tab Content ────────────────────────────────── */}
      <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
        {activeTab === 0 && <ResultTab node={node} prevNode={prevNode} nextNode={nextNode} />}
        {activeTab === 1 && <TerminalTab node={node} />}
      </div>
      </>)}
    </Drawer>
  );
};

export default ActionResultPanel;
