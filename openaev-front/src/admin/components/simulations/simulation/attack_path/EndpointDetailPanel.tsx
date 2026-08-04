import { Close } from '@mui/icons-material';
import { Alert, Box, Button, IconButton, MenuItem, Pagination, Paper, Select, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { AttackPathNodeDTO } from '../../../../../utils/api-types';
import InjectFormSection from '../../../common/injects/form/InjectFormSection';
import AttackPathVerdictPill from './AttackPathVerdictPill';

interface FindingGroup {
  type: string;
  values: string[];
}

const PAGE_SIZE_OPTIONS = [5, 10, 25, 50];
const DEFAULT_PAGE_SIZE = 10;

// A compact "N / page" selector rendered in the Findings section header: values are all loaded, so
// this only windows the display of a group that would otherwise render an unbounded list of values.
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
// and the executions that reached it. Design-system layout: an app-Drawer-style header (h5 + close),
// InjectFormSection sections, FindingIcon on each finding group and a verdict pill per execution.
// Mirrors FindingDetailPanel / ExecutionResultTerminalPanel so the three side panels are consistent.
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
  // One page per finding-type group (values are already loaded — this only windows the display); the
  // page size is shared by every group, one control for the whole Findings section.
  const [groupPages, setGroupPages] = useState<Record<string, number>>({});
  const [findingsPageSize, setFindingsPageSize] = useState(DEFAULT_PAGE_SIZE);

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
      {/* Header in the app Drawer language: title + close, over the standard divider. The title row
          centers the close control on the title line; the subtitle flows below it. */}
      <Box sx={{
        padding: theme.spacing(2, 2.5, 1.5),
        borderBottom: `1px solid ${theme.palette.divider}`,
        flexShrink: 0,
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
        >
          <Typography
            variant="h5"
            noWrap
            title={endpointLabel}
            sx={{
              flex: 1,
              minWidth: 0,
              margin: 0,
            }}
          >
            {endpointLabel}
          </Typography>
          <IconButton size="small" aria-label={t('Close')} onClick={onClose} sx={{ flexShrink: 0 }}>
            <Close fontSize="small" />
          </IconButton>
        </Box>
        {endpointSub && (
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{endpointSub}</Typography>
        )}
      </Box>

      <Box sx={{
        padding: theme.spacing(2, 2.5),
        display: 'flex',
        flexDirection: 'column',
        gap: 3,
      }}
      >
        {!hideFindings && (
          <InjectFormSection
            title={t('Findings')}
            action={findingGroups.some(g => g.values.length > PAGE_SIZE_OPTIONS[0])
              ? <PageSizeSelect value={findingsPageSize} onChange={setFindingsPageSize} />
              : undefined}
          >
            {findingsLoading && (
              <Box sx={{ minHeight: 60 }}>
                <Loader variant="inElement" size="sm" />
              </Box>
            )}
            {!findingsLoading && findingGroups.length === 0 && (
              <Alert severity="info">{t('No findings on this endpoint')}</Alert>
            )}
            {!findingsLoading && findingGroups.map((g) => {
              const pageCount = Math.max(1, Math.ceil(g.values.length / findingsPageSize));
              const page = Math.min(groupPages[g.type] ?? 1, pageCount);
              const pageValues = g.values.slice((page - 1) * findingsPageSize, page * findingsPageSize);
              return (
                <Box key={g.type}>
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.75,
                    mb: 0.5,
                  }}
                  >
                    <Box sx={{
                      'display': 'flex',
                      'alignItems': 'center',
                      'flexShrink': 0,
                      '& .MuiSvgIcon-root': { fontSize: 16 },
                    }}
                    >
                      <FindingIcon findingType={g.type} />
                    </Box>
                    <Typography
                      sx={{
                        fontFamily: '"Geologica", sans-serif',
                        fontWeight: 600,
                        fontSize: 10,
                        letterSpacing: '0.12em',
                        textTransform: 'uppercase',
                        color: 'text.secondary',
                      }}
                    >
                      {`${g.type} (${g.values.length})`}
                    </Typography>
                  </Box>
                  {pageValues.map((v, i) => (
                    <Typography
                      key={`${g.type}-${i}`}
                      variant="body2"
                      noWrap
                      title={v}
                      sx={{ pl: 2.75 }}
                    >
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
                      sx={{
                        mt: 0.5,
                        pl: 2.25,
                      }}
                    />
                  )}
                </Box>
              );
            })}
          </InjectFormSection>
        )}

        <InjectFormSection title={`${t('Executions')} (${executions.length})`}>
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
          }}
          >
            {executions.map((e) => {
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
                    'py': 0.75,
                    'px': 0.5,
                    'borderRadius': 1,
                    'borderBottom': `1px solid ${theme.palette.divider}`,
                    'backgroundColor': highlighted ? 'action.selected' : undefined,
                    // A left accent so the finding's producing execution stands out in the feed.
                    'borderLeft': highlighted ? `2px solid ${theme.palette.primary.main}` : '2px solid transparent',
                    'cursor': 'pointer',
                    'transition': theme.transitions.create(['background-color', 'border-color']),
                    '&:hover': { backgroundColor: 'action.hover' },
                    '&:focus-visible': {
                      outline: `2px solid ${theme.palette.primary.main}`,
                      outlineOffset: -2,
                    },
                  }}
                >
                  <Box sx={{
                    minWidth: 0,
                    flex: 1,
                  }}
                  >
                    <Typography variant="body2" noWrap>{e.payloadName || e.label}</Typography>
                    <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                      {[e.agentName, e.privilege].filter(Boolean).join(' · ')}
                    </Typography>
                  </Box>
                  <AttackPathVerdictPill label={status} status={e.status} />
                </Box>
              );
            })}
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
                        alignSelf: 'flex-start',
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
          </Box>
        </InjectFormSection>
      </Box>
    </Paper>
  );
};

export default EndpointDetailPanel;
