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
  PlayArrowOutlined,
  ScheduleOutlined,
  TerminalOutlined,
} from '@mui/icons-material';
import { Chip, Collapse, Divider, Drawer, IconButton, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import {
  synthesizeArguments,
  synthesizeCommand,
} from './ActionResultPanel';
import {
  type AttackPathEdge,
  type AttackPathNode,
  deriveEndpointEdges,
  getActionsForAssetFull,
  getNodeStatus,
  STATUS_COLORS,
} from './attackPathUtils';

interface EndpointDetailDialogProps {
  node: AttackPathNode | null;
  open: boolean;
  onClose: () => void;
  allNodes: AttackPathNode[];
  allEdges: AttackPathEdge[];
}

const SectionTitle: FunctionComponent<{ icon: React.ReactNode; label: string }> = ({ icon, label }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '16px 0 8px' }}>
    <span style={{ display: 'flex', opacity: 0.7 }}>{icon}</span>
    <Typography variant="overline" style={{ lineHeight: 1, fontSize: 11, fontWeight: 700, opacity: 0.7 }}>
      {label}
    </Typography>
  </div>
);

const InfoRow: FunctionComponent<{ label: string; value: string | React.ReactNode }> = ({ label, value }) => (
  <div style={{ display: 'flex', gap: 8, padding: '4px 0', fontSize: 13 }}>
    <span style={{ opacity: 0.5, minWidth: 110, flexShrink: 0 }}>{label}</span>
    <span style={{ opacity: 0.9, wordBreak: 'break-all' }}>{value}</span>
  </div>
);

const EndpointDetailDialog: FunctionComponent<EndpointDetailDialogProps> = ({
  node,
  open,
  onClose,
  allNodes,
  allEdges,
}) => {
  const { t, fldt } = useFormatter();
  const [expandedActionId, setExpandedActionId] = useState<string | null>(null);

  if (!node) return null;

  const status = getNodeStatus(node);
  const colors = STATUS_COLORS[status];

  // Find ALL actions for this endpoint (asset_link edges + hostname/IP fallback)
  const actions = getActionsForAssetFull(node, allNodes, allEdges)
    .sort((a, b) => (a.node_executed_at ?? '').localeCompare(b.node_executed_at ?? ''));

  // Derive endpoint-to-endpoint edges to show lateral movement context
  const endpointEdges = deriveEndpointEdges(allEdges);
  const sourceEndpoints = endpointEdges
    .filter((e) => e.edge_target === node.node_id)
    .map((e) => allNodes.find((n) => n.node_id === e.edge_source))
    .filter(Boolean) as AttackPathNode[];
  const targetEndpoints = endpointEdges
    .filter((e) => e.edge_source === node.node_id)
    .map((e) => allNodes.find((n) => n.node_id === e.edge_target))
    .filter(Boolean) as AttackPathNode[];

  const statusLabel = status.charAt(0).toUpperCase() + status.slice(1);

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      sx={{ zIndex: 9999 }}
      ModalProps={{ style: { zIndex: 9999 } }}
      PaperProps={{
        sx: {
          width: 480,
          maxWidth: '90vw',
          backgroundColor: 'background.paper',
          backgroundImage: 'none',
          p: 0,
          display: 'flex',
          flexDirection: 'column',
          height: '100vh',
          top: 0,
          justifyContent: 'flex-start',
          zIndex: 9999,
        },
      }}
    >
      {/* Header */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '14px 16px 12px',
        borderBottom: '1px solid rgba(255,255,255,0.1)',
      }}>
        <DevicesOutlined style={{ opacity: 0.7 }} />
        <span style={{ flex: 1, fontWeight: 700, fontSize: 16 }}>
          {node.node_hostname ?? node.node_label}
        </span>
        <Chip
          label={statusLabel}
          size="small"
          sx={{ backgroundColor: colors.fill, color: '#fff', fontWeight: 600, fontSize: 11, height: 22 }}
        />
        <IconButton onClick={onClose} size="small" sx={{ ml: 0.5 }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </div>

      {/* Scrollable body */}
      <div style={{ overflowY: 'auto', padding: '0 16px 24px', flex: 1 }}>

        {/* ── Endpoint Information ───────────────────────────── */}
        <SectionTitle icon={<DevicesOutlined fontSize="small" />} label={t('Endpoint Information')} />
        {node.node_hostname && <InfoRow label={t('Hostname')} value={node.node_hostname} />}
        {node.node_ip && <InfoRow label={t('IP Address')} value={node.node_ip} />}
        {node.node_platform && <InfoRow label={t('Platform')} value={node.node_platform} />}
        {node.node_user_privileges && (
          <InfoRow
            label={t('User Privileges')}
            value={(
              <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <PersonOutlined style={{ fontSize: 14, opacity: 0.7 }} />
                {node.node_user_privileges}
              </span>
            )}
          />
        )}
        {!node.node_hostname && !node.node_ip && !node.node_platform && !node.node_user_privileges && (
          <span style={{ fontSize: 12, opacity: 0.5 }}>{t('No endpoint metadata available')}</span>
        )}

        {/* ── Actions executed on this endpoint ─────────────── */}
        {actions.length > 0 && (
          <>
            <Divider sx={{ my: 1 }} />
            <SectionTitle icon={<PlayArrowOutlined fontSize="small" />} label={t('Actions Executed')} />
            {actions.map((action) => {
              const actionStatus = getNodeStatus(action);
              const actionColors = STATUS_COLORS[actionStatus];
              const isExpanded = expandedActionId === action.node_id;
              return (
                <div key={action.node_id}>
                  <div
                    onClick={() => setExpandedActionId(isExpanded ? null : action.node_id)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '7px 10px',
                      marginBottom: 2,
                      backgroundColor: isExpanded ? `${actionColors.fill}18` : 'rgba(255,255,255,0.03)',
                      borderRadius: 4,
                      border: `1px solid ${actionColors.fill}${isExpanded ? '55' : '22'}`,
                      borderLeft: `3px solid ${actionColors.fill}`,
                      cursor: 'pointer',
                      transition: 'all 0.15s',
                    }}
                    onMouseEnter={(e) => { if (!isExpanded) e.currentTarget.style.backgroundColor = `${actionColors.fill}10`; }}
                    onMouseLeave={(e) => { if (!isExpanded) e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.03)'; }}
                  >
                    <span style={{ width: 8, height: 8, borderRadius: '50%', flexShrink: 0, backgroundColor: actionColors.fill }} />
                    <span style={{ flex: 1, fontSize: 13, color: isExpanded ? actionColors.fill : undefined }}>{action.node_label}</span>
                    {action.node_executed_at && (
                      <span style={{ fontSize: 11, opacity: 0.4 }}>{fldt(action.node_executed_at)}</span>
                    )}
                    <Chip label={actionStatus} size="small" sx={{ height: 18, fontSize: 10, backgroundColor: `${actionColors.fill}33`, color: actionColors.fill }} />
                    <span style={{ fontSize: 10, opacity: 0.4, marginLeft: 2 }}>{isExpanded ? '▲' : '▼'}</span>
                  </div>

                  {/* Inline expanded detail — same content as execution feed */}
                  <Collapse in={isExpanded}>
                    <div style={{
                      margin: '0 0 6px 8px',
                      padding: '10px 12px',
                      backgroundColor: 'rgba(0,0,0,0.25)',
                      borderLeft: `2px solid ${actionColors.fill}40`,
                      borderRadius: '0 4px 4px 0',
                      fontSize: 12,
                    }}>
                      {/* Payload */}
                      {action.node_payload_name && (
                        <div style={{ marginBottom: 6, opacity: 0.65, fontSize: 11 }}>
                          <span style={{ opacity: 0.5, textTransform: 'uppercase', letterSpacing: 0.5, fontSize: 9 }}>Payload  </span>
                          {action.node_payload_name}
                        </div>
                      )}
                      {/* User privileges */}
                      {action.node_user_privileges && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 5 }}>
                          <PersonOutlined style={{ fontSize: 12, opacity: 0.4 }} />
                          <span style={{ opacity: 0.45, fontSize: 9, textTransform: 'uppercase', letterSpacing: 0.5 }}>User  </span>
                          <span style={{ color: '#ff9800', fontSize: 11 }}>{action.node_user_privileges}</span>
                        </div>
                      )}
                      {/* Timestamp */}
                      {action.node_executed_at && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 5 }}>
                          <ScheduleOutlined style={{ fontSize: 12, opacity: 0.4 }} />
                          <span style={{ opacity: 0.45, fontSize: 9, textTransform: 'uppercase', letterSpacing: 0.5 }}>Executed  </span>
                          <span style={{ fontSize: 11, opacity: 0.7 }}>{fldt(action.node_executed_at)}</span>
                        </div>
                      )}
                      {/* Command */}
                      {(() => {
                        const cmd = synthesizeCommand(action);
                        const args = synthesizeArguments(action);
                        return (
                          <>
                            <div style={{ backgroundColor: 'rgba(15,18,30,0.9)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 8, padding: '10px 12px', marginBottom: 8 }}>
                              <div style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 6 }}>Command</div>
                              <div style={{ fontFamily: '"JetBrains Mono","Fira Code",monospace', fontSize: 11, color: '#58a6ff', background: '#0d1117', borderRadius: 5, padding: '6px 10px', wordBreak: 'break-all' }}>
                                {cmd}
                              </div>
                            </div>
                            <div style={{ backgroundColor: 'rgba(15,18,30,0.9)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 8, padding: '10px 12px', marginBottom: 8 }}>
                              <div style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 6 }}>Arguments</div>
                              {args.length === 0
                                ? <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.3)', fontStyle: 'italic' }}>No arguments recorded</div>
                                : <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                                    {args.map((arg, i) => (
                                      <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                        <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.4)', minWidth: 70, textAlign: 'right', textTransform: 'lowercase', fontFamily: 'monospace' }}>{arg.key}</span>
                                        <span style={{ color: 'rgba(255,255,255,0.2)', fontSize: 11 }}>:</span>
                                        <span style={{ fontFamily: '"JetBrains Mono","Fira Code",monospace', fontSize: 11, color: arg.sensitive ? '#f87171' : '#e3b341', background: 'rgba(255,255,255,0.04)', borderRadius: 4, padding: '1px 6px', wordBreak: 'break-all' }}>{arg.value}</span>
                                      </div>
                                    ))}
                                  </div>
                              }
                            </div>
                          </>
                        );
                      })()}
                      {/* Terminal output */}
                      {action.node_terminal_output && (
                        <div style={{
                          backgroundColor: 'rgba(15,18,30,0.9)',
                          border: '1px solid rgba(255,255,255,0.08)',
                          borderRadius: 8,
                          padding: '10px 12px',
                          marginBottom: 6,
                        }}>
                          <div style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 6 }}>
                            Terminal Output
                          </div>
                          <pre style={{
                            margin: 0,
                            padding: '8px 10px',
                            backgroundColor: '#0d1117',
                            color: '#39d353',
                            fontFamily: '"JetBrains Mono", "Fira Code", "Cascadia Code", monospace',
                            fontSize: 10,
                            borderRadius: 5,
                            overflowY: 'auto',
                            maxHeight: 200,
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-all',
                            lineHeight: 1.6,
                            border: '1px solid rgba(57,211,83,0.12)',
                          }}>
                            {action.node_terminal_output}
                          </pre>
                        </div>
                      )}
                      {/* Files & creds */}
                      {(action.node_accessed_files?.length ?? 0) > 0 && (
                        <div style={{ marginTop: 6 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 3 }}>
                            <FolderOpenOutlined style={{ fontSize: 11, opacity: 0.4 }} />
                            <span style={{ opacity: 0.45, fontSize: 9, textTransform: 'uppercase', letterSpacing: 0.5 }}>Files</span>
                          </div>
                          {action.node_accessed_files!.map((f) => (
                            <div key={f} style={{ fontSize: 10, fontFamily: 'monospace', opacity: 0.65, paddingLeft: 16 }}>{f}</div>
                          ))}
                        </div>
                      )}
                      {(action.node_credentials_found?.length ?? 0) > 0 && (
                        <div style={{ marginTop: 6 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 3 }}>
                            <KeyOutlined style={{ fontSize: 11, opacity: 0.4 }} />
                            <span style={{ opacity: 0.45, fontSize: 9, textTransform: 'uppercase', letterSpacing: 0.5 }}>Credentials</span>
                          </div>
                          {action.node_credentials_found!.map((c) => (
                            <div key={c} style={{ fontSize: 10, fontFamily: 'monospace', color: '#f44336', opacity: 0.8, paddingLeft: 16 }}>{c}</div>
                          ))}
                        </div>
                      )}
                      {(action.node_ports_found?.length ?? 0) > 0 && (
                        <div style={{ marginTop: 6 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 3 }}>
                            <LanOutlined style={{ fontSize: 11, opacity: 0.4 }} />
                            <span style={{ opacity: 0.45, fontSize: 9, textTransform: 'uppercase', letterSpacing: 0.5 }}>Ports</span>
                          </div>
                          {action.node_ports_found!.map((p) => (
                            <div key={p} style={{ fontSize: 10, fontFamily: 'monospace', color: '#06b6d4', opacity: 0.8, paddingLeft: 16 }}>{p}</div>
                          ))}
                        </div>
                      )}
                      {(action.node_users_found?.length ?? 0) > 0 && (
                        <div style={{ marginTop: 6 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 3 }}>
                            <GroupOutlined style={{ fontSize: 11, opacity: 0.4 }} />
                            <span style={{ opacity: 0.45, fontSize: 9, textTransform: 'uppercase', letterSpacing: 0.5 }}>Users</span>
                          </div>
                          {action.node_users_found!.map((u) => (
                            <div key={u} style={{ fontSize: 10, fontFamily: 'monospace', color: '#a855f7', opacity: 0.8, paddingLeft: 16 }}>{u}</div>
                          ))}
                        </div>
                      )}
                      {(action.node_cves_found?.length ?? 0) > 0 && (
                        <div style={{ marginTop: 6 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 3 }}>
                            <BugReportOutlined style={{ fontSize: 11, opacity: 0.4 }} />
                            <span style={{ opacity: 0.45, fontSize: 9, textTransform: 'uppercase', letterSpacing: 0.5 }}>CVEs</span>
                          </div>
                          {action.node_cves_found!.map((c) => (
                            <div key={c} style={{ fontSize: 10, fontFamily: 'monospace', color: '#ef4444', opacity: 0.8, paddingLeft: 16 }}>{c}</div>
                          ))}
                        </div>
                      )}
                    </div>
                  </Collapse>
                </div>
              );
            })}
          </>
        )}

        {/* ── Captured Assets ───────────────────────────────── */}
        {((node.node_accessed_files && node.node_accessed_files.length > 0)
          || (node.node_credentials_found && node.node_credentials_found.length > 0)
          || (node.node_ports_found && node.node_ports_found.length > 0)
          || (node.node_users_found && node.node_users_found.length > 0)
          || (node.node_cves_found && node.node_cves_found.length > 0)) && (
          <>
            <Divider sx={{ my: 1 }} />
            <SectionTitle icon={<FolderOpenOutlined fontSize="small" />} label={t('Captured Assets')} />

            {node.node_accessed_files && node.node_accessed_files.length > 0 && (
              <div style={{ marginBottom: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <FolderOpenOutlined style={{ fontSize: 13, opacity: 0.6 }} />
                  <span style={{ fontSize: 11, fontWeight: 600, opacity: 0.6 }}>{t('Accessed Files')}</span>
                </div>
                {node.node_accessed_files.map((file) => (
                  <div
                    key={file}
                    style={{
                      fontSize: 12,
                      padding: '2px 8px',
                      marginBottom: 2,
                      backgroundColor: 'rgba(255,152,0,0.08)',
                      borderLeft: '2px solid #ff9800',
                      borderRadius: 2,
                      fontFamily: 'monospace',
                    }}
                  >
                    {file}
                  </div>
                ))}
              </div>
            )}

            {node.node_credentials_found && node.node_credentials_found.length > 0 && (
              <div style={{ marginBottom: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <KeyOutlined style={{ fontSize: 13, opacity: 0.6 }} />
                  <span style={{ fontSize: 11, fontWeight: 600, opacity: 0.6 }}>{t('Credentials Found')}</span>
                </div>
                {node.node_credentials_found.map((cred) => (
                  <div
                    key={cred}
                    style={{
                      fontSize: 12,
                      padding: '2px 8px',
                      marginBottom: 2,
                      backgroundColor: 'rgba(244,67,54,0.08)',
                      borderLeft: '2px solid #f44336',
                      borderRadius: 2,
                      fontFamily: 'monospace',
                    }}
                  >
                    {cred}
                  </div>
                ))}
              </div>
            )}

            {node.node_ports_found && node.node_ports_found.length > 0 && (
              <div style={{ marginBottom: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <LanOutlined style={{ fontSize: 13, opacity: 0.6 }} />
                  <span style={{ fontSize: 11, fontWeight: 600, opacity: 0.6 }}>{t('Ports Discovered')}</span>
                </div>
                {node.node_ports_found.map((port) => (
                  <div
                    key={port}
                    style={{
                      fontSize: 12,
                      padding: '2px 8px',
                      marginBottom: 2,
                      backgroundColor: 'rgba(6,182,212,0.07)',
                      borderLeft: '2px solid #06b6d4',
                      borderRadius: 2,
                      fontFamily: 'monospace',
                    }}
                  >
                    {port}
                  </div>
                ))}
              </div>
            )}

            {node.node_users_found && node.node_users_found.length > 0 && (
              <div style={{ marginBottom: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <GroupOutlined style={{ fontSize: 13, opacity: 0.6 }} />
                  <span style={{ fontSize: 11, fontWeight: 600, opacity: 0.6 }}>{t('Users Discovered')}</span>
                </div>
                {node.node_users_found.map((user) => (
                  <div
                    key={user}
                    style={{
                      fontSize: 12,
                      padding: '2px 8px',
                      marginBottom: 2,
                      backgroundColor: 'rgba(168,85,247,0.07)',
                      borderLeft: '2px solid #a855f7',
                      borderRadius: 2,
                      fontFamily: 'monospace',
                    }}
                  >
                    {user}
                  </div>
                ))}
              </div>
            )}

            {node.node_cves_found && node.node_cves_found.length > 0 && (
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <BugReportOutlined style={{ fontSize: 13, opacity: 0.6 }} />
                  <span style={{ fontSize: 11, fontWeight: 600, opacity: 0.6 }}>{t('CVEs Identified')}</span>
                </div>
                {node.node_cves_found.map((cve) => (
                  <div
                    key={cve}
                    style={{
                      fontSize: 12,
                      padding: '2px 8px',
                      marginBottom: 2,
                      backgroundColor: 'rgba(239,68,68,0.07)',
                      borderLeft: '2px solid #ef4444',
                      borderRadius: 2,
                      fontFamily: 'monospace',
                    }}
                  >
                    {cve}
                  </div>
                ))}
              </div>
            )}
          </>
        )}

        {/* ── Lateral Movement Context ──────────────────────── */}
        {(sourceEndpoints.length > 0 || targetEndpoints.length > 0) && (
          <>
            <Divider sx={{ my: 1 }} />
            <SectionTitle
              icon={<AccountTreeOutlined fontSize="small" />}
              label={t('Lateral Movement Context')}
            />
            <div style={{ display: 'flex', gap: 16 }}>
              {sourceEndpoints.length > 0 && (
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 10, opacity: 0.5, marginBottom: 6, fontWeight: 600, textTransform: 'uppercase' }}>
                    {t('Compromised From')}
                  </div>
                  {sourceEndpoints.map((ep) => {
                    const epStatus = getNodeStatus(ep);
                    const epColors = STATUS_COLORS[epStatus];
                    return (
                      <div
                        key={ep.node_id}
                        style={{
                          padding: '6px 10px',
                          marginBottom: 4,
                          borderRadius: 4,
                          backgroundColor: 'rgba(255,255,255,0.04)',
                          border: '1px solid rgba(255,255,255,0.1)',
                        }}
                      >
                        <div style={{ fontSize: 13, fontWeight: 600 }}>{ep.node_hostname ?? ep.node_label}</div>
                        {ep.node_ip && <div style={{ fontSize: 11, opacity: 0.5 }}>{ep.node_ip}</div>}
                        <Chip
                          label={epStatus}
                          size="small"
                          sx={{
                            mt: 0.5, height: 16, fontSize: 9,
                            backgroundColor: `${epColors.fill}33`,
                            color: epColors.fill,
                          }}
                        />
                      </div>
                    );
                  })}
                </div>
              )}
              {targetEndpoints.length > 0 && (
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 10, opacity: 0.5, marginBottom: 6, fontWeight: 600, textTransform: 'uppercase' }}>
                    {t('Led to Compromise of')}
                  </div>
                  {targetEndpoints.map((ep) => {
                    const epStatus = getNodeStatus(ep);
                    const epColors = STATUS_COLORS[epStatus];
                    return (
                      <div
                        key={ep.node_id}
                        style={{
                          padding: '6px 10px',
                          marginBottom: 4,
                          borderRadius: 4,
                          backgroundColor: 'rgba(255,255,255,0.04)',
                          border: '1px solid rgba(255,255,255,0.1)',
                        }}
                      >
                        <div style={{ fontSize: 13, fontWeight: 600 }}>{ep.node_hostname ?? ep.node_label}</div>
                        {ep.node_ip && <div style={{ fontSize: 11, opacity: 0.5 }}>{ep.node_ip}</div>}
                        <Chip
                          label={epStatus}
                          size="small"
                          sx={{
                            mt: 0.5, height: 16, fontSize: 9,
                            backgroundColor: `${epColors.fill}33`,
                            color: epColors.fill,
                          }}
                        />
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </Drawer>
  );
};

export default EndpointDetailDialog;
