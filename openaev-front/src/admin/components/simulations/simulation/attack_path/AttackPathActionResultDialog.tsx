import {
  AccountTreeOutlined,
  CheckCircleOutlined,
  Close as CloseIcon,
  DevicesOutlined,
  FolderOpenOutlined,
  KeyOutlined,
  PersonOutlined,
  ReportProblemOutlined,
  ScheduleOutlined,
  ShieldOutlined,
} from '@mui/icons-material';
import { Chip, Dialog, DialogContent, DialogTitle, Divider, IconButton, Typography } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import TerminalViewTab from '../../../common/injects/status/traces/TerminalViewTab';
import {
  type AttackPathExpectation,
  type AttackPathNode,
  getNodeStatus,
  STATUS_COLORS,
} from './attackPathUtils';

interface AttackPathActionResultDialogProps {
  node: AttackPathNode | null;
  open: boolean;
  onClose: () => void;
  allNodes: AttackPathNode[];
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

const AttackPathActionResultDialog: FunctionComponent<AttackPathActionResultDialogProps> = ({
  node,
  open,
  onClose,
  allNodes,
}) => {
  const { t, fldt } = useFormatter();

  if (!node) return null;

  const status = getNodeStatus(node);
  const colors = STATUS_COLORS[status];

  // Build chain context
  const prevNode = node.node_chain_previous
    ? allNodes.find((n) => n.node_id === node.node_chain_previous)
    : null;
  const nextNode = node.node_chain_next
    ? allNodes.find((n) => n.node_id === node.node_chain_next)
    : null;

  const statusLabel = status.charAt(0).toUpperCase() + status.slice(1);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth PaperProps={{
      sx: { backgroundColor: 'background.paper', backgroundImage: 'none', maxHeight: '90vh' },
    }}>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 2, pb: 1 }}>
        <div style={{
          width: 14,
          height: 14,
          borderRadius: '50%',
          backgroundColor: colors.fill,
          flexShrink: 0,
        }} />
        <span style={{ flex: 1, fontWeight: 700, fontSize: 16 }}>{node.node_label}</span>
        <Chip
          label={statusLabel}
          size="small"
          sx={{ backgroundColor: colors.fill, color: '#fff', fontWeight: 600, fontSize: 11, height: 22 }}
        />
        <IconButton onClick={onClose} size="small" sx={{ ml: 1 }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers>

        {/* ── Target Information ─────────────────────────── */}
        <SectionTitle icon={<DevicesOutlined fontSize="small" />} label={t('Target Information')} />
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
        {node.node_executed_at && <InfoRow label={t('Executed At')} value={fldt(node.node_executed_at)} />}

        {/* ── Captured Assets ─────────────────────────────── */}
        {((node.node_accessed_files && node.node_accessed_files.length > 0)
          || (node.node_credentials_found && node.node_credentials_found.length > 0)) && (
          <>
            <Divider sx={{ my: 1 }} />
            <SectionTitle icon={<ReportProblemOutlined fontSize="small" />} label={t('Captured Assets')} />

            {node.node_accessed_files && node.node_accessed_files.length > 0 && (
              <div style={{ marginBottom: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <FolderOpenOutlined style={{ fontSize: 13, opacity: 0.6 }} />
                  <span style={{ fontSize: 11, fontWeight: 600, opacity: 0.6 }}>{t('Accessed Files')}</span>
                </div>
                {node.node_accessed_files.map((file) => (
                  <div key={file} style={{
                    fontSize: 12,
                    padding: '2px 8px',
                    marginBottom: 2,
                    backgroundColor: 'rgba(255,152,0,0.08)',
                    borderLeft: '2px solid #ff9800',
                    borderRadius: 2,
                    fontFamily: 'monospace',
                  }}>
                    {file}
                  </div>
                ))}
              </div>
            )}

            {node.node_credentials_found && node.node_credentials_found.length > 0 && (
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <KeyOutlined style={{ fontSize: 13, opacity: 0.6 }} />
                  <span style={{ fontSize: 11, fontWeight: 600, opacity: 0.6 }}>{t('Credentials Found')}</span>
                </div>
                {node.node_credentials_found.map((cred) => (
                  <div key={cred} style={{
                    fontSize: 12,
                    padding: '2px 8px',
                    marginBottom: 2,
                    backgroundColor: 'rgba(244,67,54,0.08)',
                    borderLeft: '2px solid #f44336',
                    borderRadius: 2,
                    fontFamily: 'monospace',
                  }}>
                    {cred}
                  </div>
                ))}
              </div>
            )}
          </>
        )}

        {/* ── Expectations ─────────────────────────────────── */}
        {node.node_expectations && node.node_expectations.length > 0 && (
          <>
            <Divider sx={{ my: 1 }} />
            <SectionTitle icon={<ShieldOutlined fontSize="small" />} label={t('Expectations')} />
            {node.node_expectations.map((exp: AttackPathExpectation) => (
              <div key={exp.expectation_id} style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '4px 0',
                fontSize: 12,
              }}>
                <span style={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  flexShrink: 0,
                  backgroundColor: exp.expectation_status === 'SUCCESS' ? '#4caf50'
                    : exp.expectation_status === 'FAILED' ? '#f44336' : '#9e9e9e',
                }} />
                <span style={{ flex: 1, opacity: 0.85 }}>{exp.expectation_type}</span>
                <Chip
                  label={exp.expectation_status}
                  size="small"
                  sx={{
                    height: 18,
                    fontSize: 10,
                    backgroundColor: exp.expectation_status === 'SUCCESS' ? 'rgba(76,175,80,0.2)'
                      : exp.expectation_status === 'FAILED' ? 'rgba(244,67,54,0.2)' : 'rgba(158,158,158,0.2)',
                    color: exp.expectation_status === 'SUCCESS' ? '#4caf50'
                      : exp.expectation_status === 'FAILED' ? '#f44336' : '#9e9e9e',
                  }}
                />
                {exp.expectation_score !== null && (
                  <span style={{ opacity: 0.5, fontSize: 11 }}>
                    {exp.expectation_score}/{exp.expectation_expected_score}
                  </span>
                )}
              </div>
            ))}
          </>
        )}

        {/* ── Chaining Context ──────────────────────────────── */}
        {(prevNode || nextNode) && (
          <>
            <Divider sx={{ my: 1 }} />
            <SectionTitle icon={<AccountTreeOutlined fontSize="small" />} label={t('Chaining Context')} />
            <div style={{ display: 'flex', gap: 16 }}>
              {prevNode && (
                <div style={{
                  flex: 1,
                  padding: '8px 12px',
                  borderRadius: 4,
                  backgroundColor: 'rgba(255,255,255,0.04)',
                  border: '1px solid rgba(255,255,255,0.1)',
                }}>
                  <div style={{ fontSize: 10, opacity: 0.5, marginBottom: 4, fontWeight: 600, textTransform: 'uppercase' }}>
                    {t('Previous Action')}
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{prevNode.node_label}</div>
                  {prevNode.node_hostname && (
                    <div style={{ fontSize: 11, opacity: 0.5 }}>→ {prevNode.node_hostname}</div>
                  )}
                  <Chip
                    label={getNodeStatus(prevNode)}
                    size="small"
                    sx={{
                      mt: 0.5,
                      height: 16,
                      fontSize: 9,
                      backgroundColor: STATUS_COLORS[getNodeStatus(prevNode)].fill + '33',
                      color: STATUS_COLORS[getNodeStatus(prevNode)].fill,
                    }}
                  />
                </div>
              )}
              {nextNode && (
                <div style={{
                  flex: 1,
                  padding: '8px 12px',
                  borderRadius: 4,
                  backgroundColor: 'rgba(255,255,255,0.04)',
                  border: '1px solid rgba(255,255,255,0.1)',
                }}>
                  <div style={{ fontSize: 10, opacity: 0.5, marginBottom: 4, fontWeight: 600, textTransform: 'uppercase' }}>
                    {t('Next Action')}
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{nextNode.node_label}</div>
                  {nextNode.node_hostname && (
                    <div style={{ fontSize: 11, opacity: 0.5 }}>→ {nextNode.node_hostname}</div>
                  )}
                  <Chip
                    label={getNodeStatus(nextNode)}
                    size="small"
                    sx={{
                      mt: 0.5,
                      height: 16,
                      fontSize: 9,
                      backgroundColor: STATUS_COLORS[getNodeStatus(nextNode)].fill + '33',
                      color: STATUS_COLORS[getNodeStatus(nextNode)].fill,
                    }}
                  />
                </div>
              )}
            </div>
          </>
        )}

        {/* ── Execution Logs / Terminal Output ──────────────────────────────── */}
        {node.node_terminal_output && (
          <>
            <Divider sx={{ my: 1 }} />
            <SectionTitle icon={<ScheduleOutlined fontSize="small" />} label={t('Terminal Output')} />
            <pre style={{
              backgroundColor: '#0d1117',
              color: '#39d353',
              fontFamily: 'monospace',
              fontSize: 12,
              padding: '12px',
              borderRadius: 4,
              maxHeight: 300,
              overflowY: 'auto',
              margin: 0,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
            }}>
              {node.node_terminal_output}
            </pre>
          </>
        )}
        {!node.node_terminal_output && node.node_inject_id && node.node_asset_id && (
          <>
            <Divider sx={{ my: 1 }} />
            <SectionTitle icon={<ScheduleOutlined fontSize="small" />} label={t('Execution Logs')} />
            <TerminalViewTab
              injectId={node.node_inject_id}
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              target={{ target_id: node.node_asset_id, target_type: 'ASSETS' } as any}
              forceExpanded
            />
          </>
        )}

        {/* ── No extra info fallback ─────────────────────────── */}
        {!node.node_terminal_output && !node.node_inject_id && !node.node_expectations?.length && !prevNode && !nextNode && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 0', opacity: 0.5 }}>
            <CheckCircleOutlined fontSize="small" />
            <span style={{ fontSize: 13 }}>{t('No additional result data available')}</span>
          </div>
        )}

      </DialogContent>
    </Dialog>
  );
};

export default AttackPathActionResultDialog;
