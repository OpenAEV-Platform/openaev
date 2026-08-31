import { FileDownloadOutlined } from '@mui/icons-material';
import { alpha, Box, Button, CircularProgress, IconButton, Popover, Skeleton, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { FileChartOutline } from 'mdi-material-ui';
import { type FunctionComponent, type MouseEvent, useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  createReporting,
  downloadReportingGenerationUrl,
  fetchReportingGeneration,
  fetchReportingsByContext,
  generateReporting,
} from '../../../actions/reporting/reporting-actions';
import { useFormatter } from '../../../components/i18n';
import { type Reporting, type ReportingGeneration, type ReportingInput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { latestGeneration, REPORTING_CONTEXT_LABELS } from './ReportingContexts';
import { ReportingFormatFragment, ReportingStatusChip } from './ReportingFragments';

const POLL_INTERVAL_MS = 2500;
// ~3 minutes: past this we stop polling and point to the report page instead.
const MAX_POLLS = Math.ceil((3 * 60 * 1000) / POLL_INTERVAL_MS);

// Default composition of a report created straight from an entity page: every
// standard module, in reading order. Users can refine it later from the
// dedicated report page.
const DEFAULT_MODULE_TYPES: ReportingInput['reporting_modules'] = [
  { module_type: 'COVER' },
  { module_type: 'EXECUTIVE_SUMMARY' },
  { module_type: 'SUBJECT_DETAILS' },
  { module_type: 'MITRE_COVERAGE' },
  { module_type: 'RESULTS_BREAKDOWN' },
  { module_type: 'SECURITY_DOMAINS' },
  { module_type: 'SCORE_TRENDS' },
  { module_type: 'FAILED_EXPECTATIONS' },
  { module_type: 'FINDINGS' },
];

interface Props {
  contextType: Reporting['reporting_context_type'];
  contextId: string;
  entityName: string;
}

/**
 * Hero-friendly reports popover shared by every reportable entity surface
 * (simulation, scenario, atomic testing, endpoint, asset group, player, team).
 * Lists the entity's most recent reports with their latest generation status
 * and offers a one-click "generate" that reuses the most recent report - or
 * bootstraps a sensible default one when the entity has none yet.
 */
const EntityReportsPanel: FunctionComponent<Props> = ({ contextType, contextId, entityName }) => {
  const theme = useTheme();
  const { t, fldt } = useFormatter();
  const navigate = useNavigate();
  const ability = useAbility();

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  // null = not fetched yet (skeleton state while the popover is open).
  const [reportings, setReportings] = useState<Reporting[] | null>(null);
  const [generating, setGenerating] = useState(false);
  const [generationError, setGenerationError] = useState<string | null>(null);
  const [pollTimedOut, setPollTimedOut] = useState(false);
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  }, []);

  // Never leak the poll interval past the component's lifetime.
  useEffect(() => stopPolling, [stopPolling]);

  const loadReportings = useCallback(() => {
    fetchReportingsByContext(contextType, contextId)
      .then((result: { data: Reporting[] }) => setReportings(result.data ?? []))
      .catch(() => setReportings([]));
  }, [contextType, contextId]);

  // Fetch on each open so the list reflects generations triggered elsewhere.
  useEffect(() => {
    if (anchorEl) {
      setReportings(null);
      loadReportings();
    }
  }, [anchorEl, loadReportings]);

  if (!ability.can(ACTIONS.ACCESS, SUBJECTS.REPORTINGS)) {
    return null;
  }

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.REPORTINGS);
  const contextLabel = t(REPORTING_CONTEXT_LABELS[contextType]).toLowerCase();

  const handleClose = () => {
    setAnchorEl(null);
    // A closed panel no longer reports progress: stop polling and reset the
    // transient generation state (the report page shows the full history).
    stopPolling();
    setGenerating(false);
    setGenerationError(null);
    setPollTimedOut(false);
  };

  const sortedReportings = [...(reportings ?? [])]
    .sort((a, b) => (b.reporting_updated_at ?? '').localeCompare(a.reporting_updated_at ?? ''))
    .slice(0, 5);

  const pollGeneration = (generationId: string) => {
    let polls = 0;
    stopPolling();
    pollTimerRef.current = setInterval(() => {
      polls += 1;
      if (polls > MAX_POLLS) {
        stopPolling();
        setGenerating(false);
        setPollTimedOut(true);
        loadReportings();
        return;
      }
      fetchReportingGeneration(generationId)
        .then((result: { data: ReportingGeneration }) => {
          const status = result.data.reporting_generation_status;
          if (status === 'SUCCESS') {
            stopPolling();
            setGenerating(false);
            MESSAGING$.notifySuccess(t('Report successfully generated'));
            loadReportings();
          } else if (status === 'ERROR') {
            stopPolling();
            setGenerating(false);
            setGenerationError(result.data.reporting_generation_error || t('The report generation failed.'));
            loadReportings();
          }
        })
        .catch(() => {
          // Transient poll failure (the API layer already notified): keep
          // polling until the cap, the generation may still complete.
        });
    }, POLL_INTERVAL_MS);
  };

  const handleGenerate = async () => {
    setGenerating(true);
    setGenerationError(null);
    setPollTimedOut(false);
    try {
      // Reuse the most recently updated report; bootstrap a default one when
      // the entity has none yet.
      let target = sortedReportings[0];
      if (!target) {
        const input: ReportingInput = {
          reporting_name: t('{entityName} report', { entityName }),
          reporting_context_type: contextType,
          reporting_context_id: contextId,
          reporting_default_format: 'PDF',
          reporting_time_range: 'LAST_30_DAYS',
          reporting_modules: DEFAULT_MODULE_TYPES,
        };
        target = (await createReporting(input)).data as Reporting;
      }
      const generation: ReportingGeneration = (await generateReporting(target.reporting_id, { reporting_generation_format: target.reporting_default_format ?? 'PDF' })).data;
      loadReportings();
      pollGeneration(generation.reporting_generation_id);
    } catch {
      // The API layer already notified the user (simplePostCall rethrows after
      // notifying); come back to the idle footer so they can retry.
      setGenerating(false);
    }
  };

  return (
    <>
      <Tooltip title={t('Reports')}>
        <IconButton
          size="small"
          color="primary"
          aria-label={t('Reports')}
          onClick={(event: MouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget)}
        >
          <FileChartOutline fontSize="small" />
        </IconButton>
      </Tooltip>
      <Popover
        open={!!anchorEl}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        slotProps={{
          paper: {
            variant: 'outlined',
            sx: {
              marginTop: 1,
              width: 560,
              maxWidth: '90vw',
              borderRadius: 1,
              padding: 2,
            },
          },
        }}
      >
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.12em',
          textTransform: 'uppercase',
          color: 'text.secondary',
          marginBottom: 1.5,
        }}
        >
          {t('Reports')}
        </Typography>

        {/* Loading: mirror the row anatomy with three skeletons. */}
        {reportings === null && (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 1,
            marginBottom: 1.5,
          }}
          >
            {[0, 1, 2].map(index => (
              <Skeleton key={index} variant="rounded" height={52} />
            ))}
          </Box>
        )}

        {reportings !== null && sortedReportings.length === 0 && (
          <Box sx={{ marginBottom: 1.5 }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t('No reports yet for this {contextLabel}.', { contextLabel })}
            </Typography>
            {canManage && (
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {t('Generate one to get a shareable snapshot of the current results.')}
              </Typography>
            )}
          </Box>
        )}

        {reportings !== null && sortedReportings.length > 0 && (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            marginBottom: 1.5,
          }}
          >
            {sortedReportings.map((reporting) => {
              const generation = latestGeneration(reporting);
              const downloadable = generation?.reporting_generation_status === 'SUCCESS' && generation.reporting_generation_document;
              return (
                // Row anatomy: format | name + latest generation date | status | download
                <Box
                  key={reporting.reporting_id}
                  onClick={() => navigate(`/admin/reporting/${reporting.reporting_id}`)}
                  sx={{
                    'display': 'flex',
                    'alignItems': 'center',
                    'gap': 1.5,
                    'minHeight': 52,
                    'padding': theme.spacing(1, 1.25),
                    'borderRadius': 1,
                    'cursor': 'pointer',
                    'minWidth': 0,
                    '&:hover': { backgroundColor: theme.palette.action.hover },
                  }}
                >
                  <Box sx={{
                    width: 64,
                    flexShrink: 0,
                  }}
                  >
                    <ReportingFormatFragment format={reporting.reporting_default_format} />
                  </Box>
                  <Box sx={{
                    flex: 1,
                    minWidth: 0,
                  }}
                  >
                    <Typography
                      noWrap
                      sx={{
                        fontSize: 13,
                        fontWeight: 500,
                        lineHeight: 1.3,
                      }}
                    >
                      {reporting.reporting_name}
                    </Typography>
                    <Typography
                      noWrap
                      sx={{
                        fontSize: 11,
                        color: 'text.secondary',
                        lineHeight: 1.3,
                      }}
                    >
                      {generation
                        ? t('Generated on {date}', { date: fldt(generation.reporting_generation_created_at) })
                        : t('Never generated')}
                    </Typography>
                  </Box>
                  {generation && (
                    <ReportingStatusChip
                      status={generation.reporting_generation_status}
                      tooltip={generation.reporting_generation_status === 'ERROR' && generation.reporting_generation_error
                        ? generation.reporting_generation_error
                        : undefined}
                    />
                  )}
                  {downloadable && (
                    <Tooltip title={t('Download latest generation')}>
                      <IconButton
                        size="small"
                        color="primary"
                        component="a"
                        href={downloadReportingGenerationUrl(generation.reporting_generation_id)}
                        onClick={event => event.stopPropagation()}
                      >
                        <FileDownloadOutlined fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                </Box>
              );
            })}
          </Box>
        )}

        {generationError && (
          <Box sx={{
            padding: 1,
            borderRadius: 1,
            border: `1px solid ${alpha(theme.palette.error.main, 0.25)}`,
            background: alpha(theme.palette.error.main, 0.05),
            marginBottom: 1.5,
          }}
          >
            <Typography variant="body2" sx={{ color: 'error.main' }}>
              {generationError}
            </Typography>
          </Box>
        )}

        {pollTimedOut && (
          <Typography
            variant="body2"
            sx={{
              color: 'text.secondary',
              marginBottom: 1.5,
            }}
          >
            {t('The generation is still running - check the report page for the result.')}
          </Typography>
        )}

        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
        >
          <Button
            size="small"
            variant="text"
            onClick={() => navigate('/admin/reporting')}
            sx={{ marginRight: 'auto' }}
          >
            {t('Browse all')}
          </Button>
          {generating
            ? (
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                }}
                >
                  <CircularProgress size={18} />
                  <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                    {t('Generating report...')}
                  </Typography>
                </Box>
              )
            : canManage && (
              <Button
                size="small"
                variant="contained"
                color="primary"
                onClick={handleGenerate}
              >
                {t('Generate report')}
              </Button>
            )}
        </Box>
      </Popover>
    </>
  );
};

export default EntityReportsPanel;
