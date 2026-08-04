import { Close } from '@mui/icons-material';
import { Box, Button, IconButton, MenuItem, Pagination, Paper, Select, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { AttackPathNodeDTO } from '../../../../../utils/api-types';
import attackPathStatusColor from './attack-path-colors';

interface FindingGroup {
  type: string;
  values: string[];
}

const PAGE_SIZE_OPTIONS = [5, 10, 25, 50];
const DEFAULT_PAGE_SIZE = 10;

// A compact "N / page" selector next to a section title — shared by the Findings groups and the
// Executions list so both windowed lists offer the same control.
const PageSizeSelect = ({ value, onChange }: {
  value: number;
  onChange: (value: number) => void;
}) => {
  const { t } = useFormatter();
  return (
    <Select
      size="small"
      variant="standard"
      value={value}
      onChange={e => onChange(Number(e.target.value))}
      sx={{ fontSize: 12 }}
    >
      {PAGE_SIZE_OPTIONS.map(size => (
        <MenuItem key={size} value={size} sx={{ fontSize: 12 }}>
          {t('{count} / page', { count: size })}
        </MenuItem>
      ))}
    </Select>
  );
};

interface Props {
  endpointLabel: string;
  endpointSub?: string;
  findingsLoading: boolean;
  findingGroups: FindingGroup[];
  executions: AttackPathNodeDTO[];
  /**
   * How many executions target this endpoint in total — the list holds one page of them. Absent (or
   * not greater than what is loaded) means there is nothing more to fetch.
   */
  totalExecutions?: number;
  /** Fetches the next page of executions. */
  onShowMore?: () => void;
  /** A page is in flight, so the button reads as busy and cannot be clicked twice. */
  loadingMore?: boolean;
  highlightedExecutionIds: Set<string>;
  // Registers the DOM node of an execution row so the page can scroll the highlighted one into view.
  registerRow: (id: string, el: HTMLDivElement | null) => void;
  onSelectExecution: (ref: string) => void;
  // Translated status label for an execution status (prevented / detected / ...).
  execStatusLabel: (status?: string) => string;
  onClose: () => void;
  // When true, the Findings section is omitted — used for the injector panel, which lists the
  // injector's contracts under "Executions" but has no findings of its own.
  hideFindings?: boolean;
}

// Right-side panel for one endpoint selected in the attack-path graph: its findings grouped by type
// and the executions that reached it. Mirrors FindingDetailPanel / ExecutionResultTerminalPanel
// (outlined Paper, width 340, header with a close control) so the three side panels are consistent.
const EndpointDetailPanel = ({
  endpointLabel,
  endpointSub,
  findingsLoading,
  findingGroups,
  executions,
  totalExecutions,
  onShowMore,
  loadingMore = false,
  highlightedExecutionIds,
  registerRow,
  onSelectExecution,
  execStatusLabel,
  onClose,
  hideFindings = false,
}: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  // One page per finding-type group (all values are already loaded — this just windows the display);
  // the page size is shared by every group, like a single control for the whole Findings section.
  const [groupPages, setGroupPages] = useState<Record<string, number>>({});
  const [findingsPageSize, setFindingsPageSize] = useState(DEFAULT_PAGE_SIZE);
  // Executions are windowed the same way. `executions` itself may still be a server-paginated subset
  // (see totalExecutions/onShowMore below) — this only windows whatever is currently loaded.
  const [executionsPage, setExecutionsPage] = useState(1);
  const [executionsPageSize, setExecutionsPageSize] = useState(DEFAULT_PAGE_SIZE);
  const executionsPageCount = Math.max(1, Math.ceil(executions.length / executionsPageSize));
  const currentExecutionsPage = Math.min(executionsPage, executionsPageCount);
  const pagedExecutions = executions.slice(
    (currentExecutionsPage - 1) * executionsPageSize,
    currentExecutionsPage * executionsPageSize,
  );

  return (
    <Paper
      variant="outlined"
      style={{
        flex: 1,
        minWidth: 0,
        overflow: 'auto',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: theme.spacing(1),
        padding: theme.spacing(2, 2.5, 1),
      }}
      >
        <div style={{ minWidth: 0 }}>
          <Typography variant="h6" noWrap title={endpointLabel}>{endpointLabel}</Typography>
          {endpointSub && (
            <Typography variant="caption" color="text.secondary" noWrap>{endpointSub}</Typography>
          )}
        </div>
        <IconButton size="small" aria-label={t('Close')} onClick={onClose} sx={{ flexShrink: 0 }}>
          <Close />
        </IconButton>
      </div>

      <div style={{ padding: theme.spacing(0, 2.5, 2) }}>
        {!hideFindings && (
          <>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 1,
            }}
            >
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('Findings')}
              </Typography>
              {findingGroups.some(g => g.values.length > PAGE_SIZE_OPTIONS[0]) && (
                <PageSizeSelect value={findingsPageSize} onChange={setFindingsPageSize} />
              )}
            </Box>
            {findingsLoading && (
              <Box sx={{ minHeight: 60 }}>
                <Loader variant="inElement" size="sm" />
              </Box>
            )}
            {!findingsLoading && findingGroups.length === 0 && (
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{
                  display: 'block',
                  mb: 1,
                }}
              >
                {t('No findings on this endpoint')}
              </Typography>
            )}
            {!findingsLoading && findingGroups.map((g) => {
              const pageCount = Math.max(1, Math.ceil(g.values.length / findingsPageSize));
              const page = Math.min(groupPages[g.type] ?? 1, pageCount);
              const pageValues = g.values.slice(
                (page - 1) * findingsPageSize,
                page * findingsPageSize,
              );
              return (
                <Box key={g.type} sx={{ mb: 1 }}>
                  <Typography
                    variant="caption"
                    sx={{
                      display: 'block',
                      fontWeight: 700,
                      color: 'text.primary',
                      textTransform: 'uppercase',
                      letterSpacing: 0.4,
                    }}
                  >
                    {`${g.type} (${g.values.length})`}
                  </Typography>
                  {pageValues.map((v, i) => (
                    <Typography key={`${g.type}-${i}`} variant="body2" noWrap title={v}>
                      {v}
                    </Typography>
                  ))}
                  {pageCount > 1 && (
                    <Pagination
                      size="small"
                      count={pageCount}
                      page={page}
                      onChange={(_, value) => setGroupPages(prev => ({
                        ...prev,
                        [g.type]: value,
                      }))}
                      sx={{ mt: 0.5 }}
                    />
                  )}
                </Box>
              );
            })}
          </>
        )}

        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 1,
          mt: hideFindings ? 0 : 1,
        }}
        >
          <Typography variant="subtitle2" color="text.secondary" gutterBottom>
            {`${t('Executions')} (${executions.length})`}
          </Typography>
          {executions.length > PAGE_SIZE_OPTIONS[0] && (
            <PageSizeSelect
              value={executionsPageSize}
              onChange={(value) => {
                setExecutionsPageSize(value);
                setExecutionsPage(1);
              }}
            />
          )}
        </Box>
        {pagedExecutions.map((e) => {
          const status = execStatusLabel(e.status);
          const highlighted = !!e.ref && highlightedExecutionIds.has(e.ref);
          return (
            <Box
              key={e.id}
              ref={(el: HTMLDivElement | null) => {
                if (e.id) {
                  registerRow(e.id, el);
                }
              }}
              role="button"
              tabIndex={0}
              onClick={() => e.ref && onSelectExecution(e.ref)}
              onKeyDown={(ev) => {
                if (e.ref && (ev.key === 'Enter' || ev.key === ' ')) {
                  ev.preventDefault();
                  onSelectExecution(e.ref);
                }
              }}
              sx={{
                'display': 'flex',
                'alignItems': 'center',
                'gap': 1,
                'py': 0.5,
                'px': 0.5,
                'borderRadius': 1,
                'borderBottom': `1px solid ${theme.palette.divider}`,
                'backgroundColor': highlighted ? 'action.selected' : undefined,
                // A left accent so the finding's producing execution stands out in the feed.
                'borderLeft': highlighted ? `2px solid ${theme.palette.primary.main}` : '2px solid transparent',
                'cursor': 'pointer',
                '&:hover': { backgroundColor: 'action.hover' },
                '&:focus-visible': {
                  outline: `2px solid ${theme.palette.primary.main}`,
                  outlineOffset: -2,
                },
              }}
            >
              <span
                role="img"
                aria-label={status}
                title={status}
                style={{
                  flex: '0 0 auto',
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  background: attackPathStatusColor(theme, e.status),
                }}
              />
              <div style={{ minWidth: 0 }}>
                <Typography variant="body2" noWrap>{e.payloadName || e.label}</Typography>
                <Typography variant="caption" color="text.secondary" noWrap>
                  {[status, e.agentName, e.privilege].filter(Boolean).join(' · ')}
                </Typography>
              </div>
            </Box>
          );
        })}
        {executionsPageCount > 1 && (
          <Pagination
            size="small"
            count={executionsPageCount}
            page={currentExecutionsPage}
            onChange={(_, value) => setExecutionsPage(value)}
            sx={{ mt: 0.5 }}
          />
        )}
        {/* The list holds one page, so reaching the rest must be an action rather than a dead caption:
            same slot, a text button that fetches the next page (See More precedent). Where the caller
            cannot fetch more (the injector panel reads a bounded set in one go), say what is not shown
            rather than truncate silently. */}
        {(totalExecutions ?? 0) > executions.length && (
          onShowMore
            ? (
                <Button
                  size="small"
                  variant="text"
                  disabled={loadingMore}
                  onClick={() => onShowMore()}
                  sx={{
                    mt: 1,
                    textTransform: 'none',
                  }}
                >
                  {`${t('Show more')} (${(totalExecutions ?? 0) - executions.length})`}
                </Button>
              )
            : (
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    display: 'block',
                    pt: 1,
                  }}
                >
                  {t('Showing the first {count} of {total}', {
                    count: executions.length,
                    total: totalExecutions,
                  })}
                </Typography>
              )
        )}
      </div>
    </Paper>
  );
};

export default EndpointDetailPanel;
