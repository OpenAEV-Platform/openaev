import { CenterFocusStrong, Close, OpenInNew, ShieldOutlined } from '@mui/icons-material';
import { Button, IconButton, Paper, Popover, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
// eslint-disable-next-line import/no-named-as-default
import DOMPurify from 'dompurify';
import { useEffect, useRef, useState } from 'react';

import AttackPatternChip from '../../../../../components/AttackPatternChip';
import Tabs from '../../../../../components/common/tabs/Tabs';
import useTabs from '../../../../../components/common/tabs/useTabs';
import Terminal, { type TerminalLine } from '../../../../../components/common/terminal/Terminal';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { CROWDSTRIKE, SPLUNK } from '../../../../../constants/Entities';
import type { AttackPathExecutionDetailDTO } from '../../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../../utils/url-helper';
import expectationIconByType from '../../../common/ExpectationIconByType';
import { mitreForPayloadName } from './attack-path-mitre';

// A detection remediation surfaced per security platform (how to detect what was missed). Shape kept
// local and loose because the backend does not expose it on the attack-path execution yet.
// TODO(#6647): populate from AttackPathExecutionDetailDTO.detectionRemediations once the backend adds
// it (resolved from the execution's payload's detection remediations). See the backend requirements.
interface ExecDetectionRemediation {
  collectorType?: string;
  collectorLabel?: string;
  values?: string;
}

// The attack-path execution detail, augmented with the fields the backend still has to expose so the
// Remediation tab and the "Action details" link can use real data (see backend requirements topo).
type ExecutionDetail = AttackPathExecutionDetailDTO & {
  detectionRemediations?: ExecDetectionRemediation[];
  injectId?: string;
};

interface Props {
  loading: boolean;
  detail: AttackPathExecutionDetailDTO | null;
  onClose: () => void;
  // Re-center the execution's endpoint on the map (the product's link to the logic map).
  onFocusOnMap?: () => void;
  // Open the originating inject (pending backend: needs the inject id on the execution detail).
  onOpenInject?: () => void;
}

const RESULT_TAB = 'result';
const TERMINAL_TAB = 'terminal';
const REMEDIATION_TAB = 'remediation';

interface PlatformAlert {
  id: string;
  title: string;
  date?: string | null;
}
interface SecurityPlatform {
  type: string;
  label: string;
}

// An expectation verdict is a success when the platform prevented or detected the action.
const statusSucceeded = (status?: string | null): boolean =>
  ['prevented', 'detected', 'success'].includes((status ?? '').toLowerCase());

// The platform's catalog logo (collectors brick) with a graceful fallback: on dev, a platform that
// isn't installed 404s its image, so we swap in a generic shield icon instead of a broken image.
const PlatformLogo = ({ type, label }: {
  type: string;
  label: string;
}) => {
  const [failed, setFailed] = useState(false);
  const size = {
    width: 20,
    height: 20,
    borderRadius: 4,
  };
  if (failed) {
    return <ShieldOutlined style={size} />;
  }
  return (
    <img
      src={buildTenantApiPath(`/api/collectors/${type}/image`)}
      alt={label}
      onError={() => setFailed(true)}
      style={size}
    />
  );
};

// One security platform that acted on the execution, with its linked alerts revealed in a popover on
// click. The icon is the platform's catalog logo (collectors brick), same source as everywhere else.
const SecurityPlatformItem = ({ platform, alerts }: {
  platform: SecurityPlatform;
  alerts: PlatformAlert[];
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const alertCount = alerts.length;
  const alertLabel = `${alertCount} ${alertCount === 1 ? t('alert') : t('alerts')}`;
  return (
    <>
      <div
        role="button"
        tabIndex={0}
        title={t('Show alerts')}
        onClick={e => setAnchor(e.currentTarget)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setAnchor(e.currentTarget);
          }
        }}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          cursor: 'pointer',
          padding: '2px 8px',
          borderRadius: 4,
          border: `1px solid ${theme.palette.divider}`,
        }}
      >
        <PlatformLogo type={platform.type} label={platform.label} />
        <Typography variant="body2">{platform.label}</Typography>
        {/* Blue, link-styled CTA showing how many alerts the platform raised; opens the same popover. */}
        <Typography
          component="span"
          variant="body2"
          style={{
            color: theme.palette.primary.main,
            textDecoration: 'underline',
            fontWeight: 500,
          }}
        >
          {alertLabel}
        </Typography>
      </div>
      <Popover
        open={Boolean(anchor)}
        anchorEl={anchor}
        onClose={() => setAnchor(null)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'left',
        }}
      >
        <div style={{
          padding: theme.spacing(1.5),
          minWidth: 240,
        }}
        >
          <Typography variant="subtitle2" gutterBottom>{`${t('Alerts')} (${alerts.length})`}</Typography>
          {alerts.length === 0 && (
            <Typography variant="caption" color="text.secondary">{t('No alert linked')}</Typography>
          )}
          {alerts.map(a => (
            <div
              key={a.id}
              style={{
                padding: '4px 0',
                borderBottom: `1px solid ${theme.palette.divider}`,
              }}
            >
              <Typography variant="body2">{a.title}</Typography>
              {a.date && <Typography variant="caption" color="text.secondary">{a.date}</Typography>}
            </div>
          ))}
        </div>
      </Popover>
    </>
  );
};

// The Result & Terminal panel for one execution (issue 5048): an in-flow panel between the execution
// feed and the map (product mockup), not an overlay. Reuses the platform's shared `Terminal` renderer,
// fed by the frozen snapshot's masked command and output. The Result tab shows the target and the
// security platforms that prevented/detected the action (with their linked alerts on click).
const ExecutionResultTerminalPanel = ({ loading, detail, onClose, onFocusOnMap, onOpenInject }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { currentTab, handleChangeTab } = useTabs(RESULT_TAB);
  // Detection remediations (per security platform) for this action — pending backend, so empty today.
  const detectionRemediations = (detail as ExecutionDetail | null)?.detectionRemediations ?? [];

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

  // TODO(#6647): replace this placeholder with the real per-security-platform expectation results
  // (source platform, status, detection time, linked alerts) once the backend exposes them on the
  // execution detail. For now we surface the aggregate prevention/detection verdict against the
  // platforms wired on this environment (CrowdStrike for prevention & detection, Splunk for detection).
  const buildAlerts = (label: string): PlatformAlert[] => [
    {
      id: `${label}-1`,
      title: t('Suspicious activity flagged'),
      date: detail?.executedAt,
    },
    {
      id: `${label}-2`,
      title: t('Endpoint telemetry correlated'),
      date: detail?.executedAt,
    },
  ];
  const preventedBy: SecurityPlatform[] = statusSucceeded(detail?.preventionStatus)
    ? [{
        type: CROWDSTRIKE,
        label: 'CrowdStrike',
      }]
    : [];
  // A prevented action is necessarily detected too (you cannot block what you did not see), so a
  // successful prevention implies detection.
  const isDetected = statusSucceeded(detail?.detectionStatus) || statusSucceeded(detail?.preventionStatus);
  const detectedBy: SecurityPlatform[] = isDetected
    ? [{
        type: CROWDSTRIKE,
        label: 'CrowdStrike',
      }, {
        type: SPLUNK,
        label: 'Splunk',
      }]
    : [];

  const renderExpectationRow = (
    expectationType: 'prevention' | 'detection',
    heading: string,
    emptyLabel: string,
    platforms: SecurityPlatform[],
  ) => {
    // Colour the verdict: prevention succeeds green, detection succeeds orange, neither is red.
    const succeeded = platforms.length > 0;
    let iconColor = theme.palette.error.main;
    if (succeeded) {
      iconColor = expectationType === 'prevention' ? theme.palette.success.main : theme.palette.warning.main;
    }
    return (
      <div>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          marginBottom: 6,
        }}
        >
          {expectationIconByType(expectationType, { color: iconColor })}
          <Typography variant="subtitle2">{heading}</Typography>
        </div>
        {platforms.length === 0
          ? <Typography variant="caption" style={{ color: theme.palette.error.main }}>{emptyLabel}</Typography>
          : (
              <div style={{
                display: 'flex',
                gap: 8,
                flexWrap: 'wrap',
              }}
              >
                {platforms.map(p => (
                  <SecurityPlatformItem key={p.type} platform={p} alerts={buildAlerts(p.label)} />
                ))}
              </div>
            )}
      </div>
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
      <div style={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        padding: theme.spacing(2, 2.5, 1),
        flexShrink: 0,
      }}
      >
        <div style={{ minWidth: 0 }}>
          <Typography variant="h6" noWrap>{detail?.payloadName || t('Execution')}</Typography>
          <Typography variant="caption" color="text.secondary">
            {[detail?.agentName, detail?.agentPrivilege].filter(Boolean).join(' · ')}
          </Typography>
          {/* Path story: injector -> endpoint -> finding type, resolved for this execution. Finding
              values are omitted here (they can be secrets); only the type is shown. */}
          {(() => {
            const crumb = [
              detail?.payloadName,
              detail?.targetHostname || detail?.endpointKey,
              detail?.findings?.[0]?.type,
            ].filter(Boolean);
            return crumb.length > 1 && (
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{
                  display: 'block',
                  mt: 0.25,
                }}
                title={crumb.join(' › ')}
                noWrap
              >
                {crumb.join('  ›  ')}
              </Typography>
            );
          })()}
          {/* MITRE ATT&CK technique(s) this action maps to (front-only static lookup for the POC). */}
          {mitreForPayloadName(detail?.payloadName).length > 0 && (
            <div style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 4,
              marginTop: 6,
            }}
            >
              {mitreForPayloadName(detail?.payloadName).map(tech => (
                <AttackPatternChip key={tech.attack_pattern_external_id} attackPattern={tech} />
              ))}
            </div>
          )}
        </div>
        <div style={{
          display: 'flex',
          gap: theme.spacing(0.5),
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
        </div>
      </div>

      {loading && (
        <div style={{ minHeight: 160 }}>
          <Loader variant="inElement" size="sm" />
        </div>
      )}

      {!loading && detail && (
        <div style={{
          flex: 1,
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
          padding: theme.spacing(0, 2.5, 2),
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
                key: REMEDIATION_TAB,
                label: t('Remediation'),
              },
            ]}
            currentTab={currentTab}
            onChange={handleChangeTab}
          />

          <div
            ref={contentRef}
            style={{
              flex: 1,
              minHeight: 0,
              // The Result tab owns its scroll; the Terminal tab is sized to fill this box and scrolls
              // internally, so the outer box must not add a second scrollbar.
              overflow: currentTab === TERMINAL_TAB ? 'hidden' : 'auto',
              paddingTop: theme.spacing(2),
            }}
          >
            {currentTab === RESULT_TAB && (
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: theme.spacing(2),
              }}
              >
                <div>
                  <Typography variant="subtitle2">{detail.targetHostname || detail.endpointKey}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {[detail.targetIp, detail.targetPlatform].filter(Boolean).join(' · ')}
                  </Typography>
                </div>
                {renderExpectationRow('prevention', t('Prevented by'), t('Not Prevented'), preventedBy)}
                {renderExpectationRow('detection', t('Detected by'), t('Not Detected'), detectedBy)}
                {/* Jump to the originating inject for the full action definition (pending backend id). */}
                {onOpenInject && (
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={<OpenInNew fontSize="small" />}
                    onClick={onOpenInject}
                    sx={{ alignSelf: 'flex-start' }}
                  >
                    {t('Action details')}
                  </Button>
                )}
              </div>
            )}

            {currentTab === TERMINAL_TAB && (
              <Terminal lines={terminalLines} maxHeight={terminalMaxHeight} />
            )}

            {currentTab === REMEDIATION_TAB && (
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: theme.spacing(2),
              }}
              >
                <Typography variant="caption" color="text.secondary">
                  {t('How each security platform could detect this action (detection rules).')}
                </Typography>
                {detectionRemediations.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    {t('No detection remediation available for this action yet.')}
                  </Typography>
                )}
                {detectionRemediations.map((rem, index) => (
                  <div key={rem.collectorType ?? index}>
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      marginBottom: 6,
                    }}
                    >
                      {rem.collectorType && <PlatformLogo type={rem.collectorType} label={rem.collectorLabel ?? rem.collectorType} />}
                      <Typography variant="subtitle2">{rem.collectorLabel ?? rem.collectorType}</Typography>
                    </div>
                    {/* Detection rule text is sanitized before rendering — never injected raw. */}
                    <div
                      // eslint-disable-next-line react/no-danger
                      dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize((rem.values ?? '').replace(/\n/g, '<br/>')) }}
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </Paper>
  );
};

export default ExecutionResultTerminalPanel;
