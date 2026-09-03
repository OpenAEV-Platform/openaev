import { ArrowDropDownOutlined, FileDownloadOutlined } from '@mui/icons-material';
import { Box, Button, ButtonGroup, Chip, CircularProgress, IconButton, Menu, MenuItem, Tooltip, Typography } from '@mui/material';
import { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import {
  downloadReportingGenerationUrl,
  fetchReporting,
  fetchReportingGeneration,
  fetchReportingGenerations,
  generateReporting,
} from '../../../actions/reporting/reporting-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useRoutedTabs from '../../../components/common/tabs/useRoutedTabs';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import { type Reporting, type ReportingGeneration } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { TIME_RANGE_LABELS } from './render/reportingRenderLabels';
import { REPORTING_CONTEXT_ICONS, REPORTING_CONTEXT_LABELS } from './ReportingContexts';
import { REPORTING_FORMATS, resolveSubjectOptions } from './ReportingFormUtils';
import { ReportingFormatFragment } from './ReportingFragments';
import ReportingGenerationsTab from './ReportingGenerationsTab';
import ReportingPopover from './ReportingPopover';
import ReportingPreviewTab from './ReportingPreviewTab';
import ReportingSchedulesTab from './ReportingSchedulesTab';

const POLL_INTERVAL_MS = 2500;
// ~3 minutes: past this we stop polling, the generations tab still refreshes.
const MAX_POLLS = Math.ceil((3 * 60 * 1000) / POLL_INTERVAL_MS);

/**
 * Report detail page: header (subject, formats, generate now), live preview,
 * generation history and recurring schedules.
 */
const ReportingPage = () => {
  const { t, fldt } = useFormatter();
  const { reportingId } = useParams();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.REPORTINGS);

  // -- Report ------------------------------------------------------------------

  const [reporting, setReporting] = useState<Reporting | null>(null);
  const [loading, setLoading] = useState(true);

  const loadReporting = useCallback(() => {
    if (!reportingId) return Promise.resolve();
    return fetchReporting(reportingId)
      .then((result: { data: Reporting }) => setReporting(result.data))
      .catch(() => setReporting(null));
  }, [reportingId]);

  useEffect(() => {
    loadReporting().finally(() => setLoading(false));
  }, [loadReporting]);

  // -- Subject entity name --------------------------------------------------------

  const [subjectName, setSubjectName] = useState<string | undefined>();
  const contextType = reporting?.reporting_context_type;
  const contextId = reporting?.reporting_context_id;
  useEffect(() => {
    if (!contextType || contextType === 'PLATFORM' || !contextId) {
      setSubjectName(undefined);
      return;
    }
    resolveSubjectOptions(contextType, [contextId])
      .then(options => setSubjectName(options[0]?.label))
      .catch(() => setSubjectName(undefined));
  }, [contextType, contextId]);

  // -- Generations ------------------------------------------------------------------

  const [generations, setGenerations] = useState<ReportingGeneration[]>([]);
  const loadGenerations = useCallback(() => {
    if (!reportingId) return;
    fetchReportingGenerations(reportingId)
      .then((result: { data: ReportingGeneration[] }) => setGenerations(result.data ?? []))
      .catch(() => {
        // The API layer already notified; keep the current list.
      });
  }, [reportingId]);

  useEffect(() => {
    loadGenerations();
  }, [loadGenerations]);

  // -- Generate now + polling ---------------------------------------------------------

  const [generating, setGenerating] = useState(false);
  const [formatMenuAnchor, setFormatMenuAnchor] = useState<HTMLElement | null>(null);
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  }, []);

  // Never leak the poll interval past the component's lifetime.
  useEffect(() => stopPolling, [stopPolling]);

  const pollGeneration = (generationId: string) => {
    let polls = 0;
    stopPolling();
    pollTimerRef.current = setInterval(() => {
      polls += 1;
      if (polls > MAX_POLLS) {
        stopPolling();
        setGenerating(false);
        MESSAGING$.notifyError(t('The generation is taking longer than expected - its result will appear in the generations list.'));
        loadGenerations();
        return;
      }
      fetchReportingGeneration(generationId)
        .then((result: { data: ReportingGeneration }) => {
          const status = result.data.reporting_generation_status;
          if (status === 'SUCCESS') {
            stopPolling();
            setGenerating(false);
            MESSAGING$.notifySuccess(t('Report successfully generated'));
            loadGenerations();
          } else if (status === 'ERROR') {
            stopPolling();
            setGenerating(false);
            MESSAGING$.notifyError(result.data.reporting_generation_error || t('The report generation failed.'));
            loadGenerations();
          }
        })
        .catch(() => {
          // Transient poll failure: keep polling until the cap.
        });
    }, POLL_INTERVAL_MS);
  };

  const handleGenerate = async (format: Reporting['reporting_default_format']) => {
    if (!reportingId) return;
    setFormatMenuAnchor(null);
    setGenerating(true);
    try {
      const generation: ReportingGeneration = (await generateReporting(reportingId, { reporting_generation_format: format ?? 'PDF' })).data;
      loadGenerations();
      pollGeneration(generation.reporting_generation_id);
    } catch {
      // The API layer already notified the user.
      setGenerating(false);
    }
  };

  // -- Tabs -------------------------------------------------------------------------

  const tabEntries: TabsEntry[] = [
    {
      key: 'preview',
      label: t('Preview'),
    },
    {
      key: 'generations',
      label: t('Generations'),
    },
    {
      key: 'schedules',
      label: t('Schedules'),
    },
  ];
  // Routed tabs (/admin/reporting/:id[/generations|/schedules]) so every tab
  // is deep-linkable, like the atomic testing / scenario / asset detail tabs.
  const { currentTab, handleChangeTab } = useRoutedTabs(['preview', 'generations', 'schedules'], 'preview');

  if (loading) return <Loader />;
  if (!reporting) return <NotFound />;

  const ContextIcon = REPORTING_CONTEXT_ICONS[reporting.reporting_context_type];
  const contextLabel = t(REPORTING_CONTEXT_LABELS[reporting.reporting_context_type]);
  const defaultFormat = reporting.reporting_default_format ?? 'PDF';

  // Latest downloadable output, surfaced in the header so grabbing the most
  // recent report never requires a trip to the Generations tab.
  const latestDownloadable = [...generations]
    .sort((a, b) => (b.reporting_generation_created_at ?? '').localeCompare(a.reporting_generation_created_at ?? ''))
    .find(generation => generation.reporting_generation_status === 'SUCCESS' && generation.reporting_generation_document);

  return (
    <>
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Reporting'),
            link: '/admin/reporting',
          },
          {
            label: reporting.reporting_name,
            current: true,
          },
        ]}
      />
      <Box sx={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: 2,
      }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            flexWrap: 'wrap',
          }}
          >
            <Typography variant="h1" sx={{ margin: 0 }}>
              {reporting.reporting_name}
            </Typography>
            <Chip
              icon={<ContextIcon fontSize="small" />}
              label={subjectName ? `${contextLabel} - ${subjectName}` : contextLabel}
              size="small"
              variant="outlined"
            />
            <ReportingFormatFragment format={defaultFormat} />
            <Chip label={t(TIME_RANGE_LABELS[reporting.reporting_time_range ?? 'LAST_30_DAYS'])} size="small" variant="outlined" />
          </Box>
          {reporting.reporting_description && (
            <Typography
              variant="body2"
              sx={{
                color: 'text.secondary',
                marginTop: 0.5,
              }}
            >
              {reporting.reporting_description}
            </Typography>
          )}
          <Typography
            variant="body2"
            sx={{
              color: 'text.secondary',
              fontSize: 12,
              marginTop: 0.5,
            }}
          >
            {`${t('Updated at')} ${fldt(reporting.reporting_updated_at)}`}
          </Typography>
        </Box>
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          flexShrink: 0,
        }}
        >
          {latestDownloadable && (
            <Tooltip title={`${t('Download latest generation')} (${latestDownloadable.reporting_generation_format ?? ''})`}>
              <IconButton
                color="primary"
                component="a"
                href={downloadReportingGenerationUrl(latestDownloadable.reporting_generation_id)}
              >
                <FileDownloadOutlined />
              </IconButton>
            </Tooltip>
          )}
          {canManage && (
            <>
              <ButtonGroup variant="contained" size="small" disabled={generating}>
                <Button
                  startIcon={generating ? <CircularProgress size={14} color="inherit" /> : undefined}
                  onClick={() => handleGenerate(defaultFormat)}
                >
                  {generating ? t('Generating...') : `${t('Generate now')} (${defaultFormat})`}
                </Button>
                <Button
                  size="small"
                  aria-label={t('Choose format')}
                  onClick={event => setFormatMenuAnchor(event.currentTarget)}
                >
                  <ArrowDropDownOutlined />
                </Button>
              </ButtonGroup>
              <Menu
                anchorEl={formatMenuAnchor}
                open={!!formatMenuAnchor}
                onClose={() => setFormatMenuAnchor(null)}
              >
                {REPORTING_FORMATS.map(format => (
                  <MenuItem key={format} onClick={() => handleGenerate(format)}>
                    {`${t('Generate')} ${format}`}
                  </MenuItem>
                ))}
              </Menu>
            </>
          )}
          <ReportingPopover
            reporting={reporting}
            onUpdate={result => setReporting(result)}
            onDelete={() => navigate('/admin/reporting')}
          />
        </Box>
      </Box>

      <Box sx={{ marginTop: 2 }}>
        <Tabs
          entries={tabEntries}
          currentTab={currentTab}
          onChange={key => handleChangeTab(key)}
        />
        {currentTab === 'preview' && (
          <ReportingPreviewTab
            reportingId={reporting.reporting_id}
            refreshToken={reporting.reporting_updated_at}
          />
        )}
        {currentTab === 'generations' && (
          <ReportingGenerationsTab
            generations={generations}
            onReload={loadGenerations}
            canManage={canManage}
            onGenerate={() => handleGenerate(defaultFormat)}
            generating={generating}
          />
        )}
        {currentTab === 'schedules' && (
          <ReportingSchedulesTab
            reporting={reporting}
            onChanged={() => loadReporting()}
            canManage={canManage}
          />
        )}
      </Box>
    </>
  );
};

export default ReportingPage;
