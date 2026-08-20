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
import { ExecutionRowStatusBadge } from './ExecutionStatusBadge';

interface FindingGroup {
  type: string;
  values: string[];
}

const PAGE_SIZE_OPTIONS = [5, 10, 25, 50];
const DEFAULT_PAGE_SIZE = 10;

// Executions render as an aligned three-column table (Action | Execution | Result). A network inject
// carries no agent metadata, so without a fixed grid the rows were ragged and the two status chips
// landed at a different x on every row; the fixed template plus right-aligned status cells turn them
// into a proper column with a header that says what each chip means.
const EXECUTION_GRID_COLUMNS = 'minmax(0, 1fr) 132px 116px';

const executionHeaderCellSx = {
  fontFamily: '"Geologica", sans-serif',
  fontWeight: 600,
  fontSize: 10,
  letterSpacing: '0.12em',
  textTransform: 'uppercase',
  color: 'text.secondary',
} as const;

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
  simulationId: string;
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
  /**
   * Empty-state text for the Findings section. The panel serves endpoints, teams, asset groups AND
   * actions (injector panel), so the caller words it for the actual entity ("No findings on this
   * team", "No findings from this action", ...). Falls back to a neutral target wording.
   */
  emptyFindingsLabel?: string;
  /**
   * Empty-state text for the Executions section. An action panel has no "target" to reach (it RUNS
   * executions), so it reads "No executions recorded for this action" rather than the endpoint panel's
   * "reached this endpoint". Falls back to a neutral target wording.
   */
  emptyExecutionsLabel?: string;
}

// Right-side panel for one endpoint selected in the attack-path graph: its findings grouped by type
// and the executions that reached it. Design-system layout: an app-Drawer-style header (h5 + close),
// InjectFormSection sections, FindingIcon on each finding group and a verdict pill per execution.
// Mirrors FindingDetailPanel / ExecutionResultTerminalPanel so the three side panels are consistent.
const EndpointDetailPanel = ({
  simulationId,
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
  emptyFindingsLabel,
  emptyExecutionsLabel,
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
              <Alert severity="info">{emptyFindingsLabel ?? t('No findings on this target')}</Alert>
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
                    {/* The pager belongs to THIS group only (each finding type pages on its own), so it
                        sits on the group's own header row. Under the last value it landed between two
                        groups, equidistant from both, and read as paging the whole section. */}
                    {pageCount > 1 && (
                      <Pagination
                        size="small"
                        count={pageCount}
                        page={page}
                        siblingCount={0}
                        aria-label={t('Findings pagination for {type}', { type: g.type })}
                        onChange={(_, value) => setGroupPages(prev => ({
                          ...prev,
                          [g.type]: value,
                        }))}
                        sx={{ ml: 'auto' }}
                      />
                    )}
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
                </Box>
              );
            })}
          </InjectFormSection>
        )}

        <InjectFormSection title={`${t('Executions')} (${executions.length})`}>
          {executions.length === 0
            ? <Alert severity="info">{emptyExecutionsLabel ?? t('No execution reached this target')}</Alert>
            : (
                <Box sx={{
                  display: 'flex',
                  flexDirection: 'column',
                }}
                >
                  {/* Column headers, aligned with the row grid below so the two status columns read as a
                      table and the reader knows what each chip means. */}
                  <Box sx={{
                    display: 'grid',
                    gridTemplateColumns: EXECUTION_GRID_COLUMNS,
                    alignItems: 'center',
                    gap: 1,
                    px: 0.5,
                    pb: 0.75,
                    borderBottom: `1px solid ${theme.palette.divider}`,
                  }}
                  >
                    <Typography sx={executionHeaderCellSx}>{t('Action')}</Typography>
                    <Typography sx={{
                      ...executionHeaderCellSx,
                      textAlign: 'right',
                    }}
                    >
                      {t('Execution')}
                    </Typography>
                    <Typography sx={{
                      ...executionHeaderCellSx,
                      textAlign: 'right',
                    }}
                    >
                      {t('Result')}
                    </Typography>
                  </Box>
                  {executions.map((e) => {
                    const status = execStatusLabel(e.status);
                    const highlighted = !!e.ref && highlightedExecutionIds.has(e.ref);
                    const subtitle = [e.agentName, e.privilege].filter(Boolean).join(' · ');
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
                          'display': 'grid',
                          'gridTemplateColumns': EXECUTION_GRID_COLUMNS,
                          'alignItems': 'center',
                          'gap': 1,
                          'minHeight': 44,
                          'py': 0.5,
                          'px': 0.5,
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
                        <Box sx={{ minWidth: 0 }}>
                          <Typography variant="body2" noWrap title={e.payloadName || e.label}>
                            {e.payloadName || e.label}
                          </Typography>
                          {subtitle && (
                            <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                              {subtitle}
                            </Typography>
                          )}
                        </Box>
                        {/* Whether this execution actually ran at all (issue 244) - the verdict pill in the
                            last column only answers whether it was caught. Both status cells are right-aligned
                            in their fixed grid column so agent-based and network-based rows line up. */}
                        <Box sx={{
                          display: 'flex',
                          justifyContent: 'flex-end',
                          minWidth: 0,
                        }}
                        >
                          <ExecutionRowStatusBadge
                            simulationId={simulationId}
                            executionRef={e.ref}
                            endpointName={e.hostname}
                            injectId={e.injectId}
                            payloadId={e.payloadId}
                            executionStatus={e.executionStatus}
                          />
                        </Box>
                        <Box sx={{
                          display: 'flex',
                          justifyContent: 'flex-end',
                          minWidth: 0,
                        }}
                        >
                          <AttackPathVerdictPill label={status} status={e.status} />
                        </Box>
                      </Box>
                    );
                  })}
                  {/* The list holds one page, so reaching the rest must be an action rather than a dead
                      caption: same slot, a text button that fetches the next page (See More precedent).
                      Where the caller cannot fetch more (the injector panel reads a bounded set in one go),
                      say what is not shown rather than truncate silently. */}
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
              )}
        </InjectFormSection>
      </Box>
    </Paper>
  );
};

export default EndpointDetailPanel;
