import { CenterFocusStrong, Close } from '@mui/icons-material';
import { Alert, Box, Chip, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useRef, useState } from 'react';

import Tabs from '../../../../../components/common/tabs/Tabs';
import useTabs from '../../../../../components/common/tabs/useTabs';
import Terminal, { type TerminalLine } from '../../../../../components/common/terminal/Terminal';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import Loader from '../../../../../components/Loader';
import type { AttackPathExecutionDetailDTO } from '../../../../../utils/api-types';

interface Props {
  loading: boolean;
  detail: AttackPathExecutionDetailDTO | null;
  onClose: () => void;
  // Re-center the execution's endpoint on the map (the product's link to the logic map).
  onFocusOnMap?: () => void;
}

const RESULT_TAB = 'result';
const TERMINAL_TAB = 'terminal';
const PREVENTION_TAB = 'prevention';
const DETECTION_TAB = 'detection';

// The Result & Terminal panel for one execution (issue 5048): an in-flow panel between the execution feed
// and the map (product mockup), not an overlay. Reuses the platform's shared `Terminal` renderer, fed by
// the frozen snapshot's masked command and output, so it looks native without touching the live inject.
// The Result tab is a snapshot view (target, status, findings); credentials arrive masked from the server.
const ExecutionResultTerminalPanel = ({ loading, detail, onClose, onFocusOnMap }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { currentTab, handleChangeTab } = useTabs(RESULT_TAB);

  // Size the Terminal to exactly fill its scroll area so it is the single scroller (no nested
  // scrollbar) and nothing is clipped: measure the content box and track it on resize.
  const contentRef = useRef<HTMLDivElement | null>(null);
  const [terminalMaxHeight, setTerminalMaxHeight] = useState(400);
  useEffect(() => {
    const el = contentRef.current;
    if (!el) {
      return undefined;
    }
    const measure = () => {
      const styles = window.getComputedStyle(el);
      const padY = parseFloat(styles.paddingTop) + parseFloat(styles.paddingBottom);
      setTerminalMaxHeight(Math.max(160, el.clientHeight - padY));
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [loading, detail]);

  const terminalLines: TerminalLine[] = [];
  if (detail?.command) {
    terminalLines.push({
      key: 'command',
      content: `$ ${detail.command}`,
      level: 'info',
    });
  }
  (detail?.terminalOutput ?? '').split('\n').forEach((line, index) => {
    terminalLines.push({
      key: `out-${index}`,
      content: line,
    });
  });

  // Prevention/Detection tabs (issue 6647): the attack-path snapshot only exposes the aggregate
  // expectation status per execution — the per-security-platform breakdown (source, time, alerts)
  // is a backend dependency. We reuse the shared `ItemStatus` pill (same one the security-platforms
  // table uses) so the status reads natively, and flag that the detailed table is coming.
  const renderExpectationTab = (status: string | null | undefined) => {
    if (!status) {
      return <Alert severity="info">{t('No expectation for this execution')}</Alert>;
    }
    return (
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
      }}
      >
        <Typography variant="subtitle2">{t('Security platforms')}</Typography>
        <div>
          <ItemStatus label={status} status={status} />
        </div>
        <Typography variant="caption" color="text.secondary">
          {t('The per-security-platform breakdown will be available soon.')}
        </Typography>
      </Box>
    );
  };

  return (
    <Paper
      variant="outlined"
      style={{
        width: 400,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        px: 2.5,
        pt: 2,
        pb: 1,
        flexShrink: 0,
      }}
      >
        <div style={{ minWidth: 0 }}>
          <Typography variant="h6" noWrap>{detail?.payloadName || t('Execution')}</Typography>
          <Typography variant="caption" color="text.secondary">
            {[detail?.agentName, detail?.agentPrivilege].filter(Boolean).join(' · ')}
          </Typography>
        </div>
        <Box sx={{
          display: 'flex',
          gap: 0.5,
          flexShrink: 0,
        }}
        >
          {onFocusOnMap && (
            <Tooltip title={t('Focus on map')}>
              <IconButton size="small" aria-label={t('Focus on map')} onClick={onFocusOnMap}>
                <CenterFocusStrong fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          <IconButton size="small" aria-label={t('Close')} onClick={onClose}>
            <Close />
          </IconButton>
        </Box>
      </Box>

      {loading && (
        <Box sx={{ minHeight: 160 }}>
          <Loader variant="inElement" size="sm" />
        </Box>
      )}

      {!loading && detail && (
        <Box sx={{
          flex: 1,
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
          px: 2.5,
          pb: 2,
        }}
        >
          <Tabs
            entries={[
              {
                key: RESULT_TAB,
                label: t('Result'),
              },
              {
                key: TERMINAL_TAB,
                label: t('Terminal view'),
              },
              {
                key: PREVENTION_TAB,
                label: t('Prevention'),
              },
              {
                key: DETECTION_TAB,
                label: t('Detection'),
              },
            ]}
            currentTab={currentTab}
            onChange={handleChangeTab}
          />

          <Box
            ref={contentRef}
            sx={{
              flex: 1,
              minHeight: 0,
              // The Result tab owns its scroll (the findings list can be long); the Terminal tab is
              // sized to fill this box and scrolls internally, so the outer box must not add a second
              // scrollbar.
              overflow: currentTab === TERMINAL_TAB ? 'hidden' : 'auto',
              pt: 2,
            }}
          >
            {currentTab === RESULT_TAB && (
              <>
                <Typography variant="subtitle2">{detail.targetHostname || detail.endpointKey}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {[detail.targetIp, detail.targetPlatform].filter(Boolean).join(' · ')}
                </Typography>
                <Box sx={{
                  display: 'flex',
                  gap: 1,
                  my: 1.5,
                  flexWrap: 'wrap',
                }}
                >
                  {detail.preventionStatus && (
                    <Chip size="small" variant="outlined" label={`${t('Prevention')}: ${detail.preventionStatus}`} />
                  )}
                  {detail.detectionStatus && (
                    <Chip size="small" variant="outlined" label={`${t('Detection')}: ${detail.detectionStatus}`} />
                  )}
                </Box>
                <Typography variant="subtitle2">{`${t('Findings')} (${detail.findings?.length ?? 0})`}</Typography>
                {(detail.findings?.length ?? 0) === 0 && (
                  <Alert severity="info" sx={{ mt: 1 }}>{t('No findings')}</Alert>
                )}
                {(detail.findings ?? []).map((finding, index) => (
                  <Box
                    key={`${finding.type}-${finding.value}-${index}`}
                    sx={{
                      py: 0.5,
                      borderBottom: `1px solid ${theme.palette.divider}`,
                    }}
                  >
                    <Typography variant="body2" noWrap title={finding.value}>{finding.value}</Typography>
                    <Typography variant="caption" color="text.secondary">{finding.type}</Typography>
                  </Box>
                ))}
              </>
            )}

            {currentTab === TERMINAL_TAB && (
              <Terminal lines={terminalLines} maxHeight={terminalMaxHeight} />
            )}

            {currentTab === PREVENTION_TAB && renderExpectationTab(detail.preventionStatus)}

            {currentTab === DETECTION_TAB && renderExpectationTab(detail.detectionStatus)}
          </Box>
        </Box>
      )}
    </Paper>
  );
};

export default ExecutionResultTerminalPanel;
