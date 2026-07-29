import { ArrowBack, Close, OpenInNew, ShieldOutlined } from '@mui/icons-material';
import { Button, IconButton, Paper, Popover, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
// eslint-disable-next-line import/no-named-as-default
import DOMPurify from 'dompurify';
import { useEffect, useRef, useState } from 'react';

import { searchDistinctFindingsForInjects } from '../../../../../actions/findings/finding-actions';
import { getInjectStatusWithGlobalExecutionTraces, searchTargets } from '../../../../../actions/injects/inject-action';
import AttackPatternChip from '../../../../../components/AttackPatternChip';
import Tabs from '../../../../../components/common/tabs/Tabs';
import useTabs from '../../../../../components/common/tabs/useTabs';
import Terminal, { type TerminalLine } from '../../../../../components/common/terminal/Terminal';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { AttackPathExecutionDetailDTO, InjectStatusOutput, InjectTarget } from '../../../../../utils/api-types';
import useEnterpriseEdition from '../../../../../utils/hooks/useEnterpriseEdition';
import { getStatusColor } from '../../../../../utils/statusUtils';
import { buildTenantApiPath } from '../../../../../utils/url-helper';
import EEChip from '../../../common/entreprise_edition/EEChip';
import expectationIconByType from '../../../common/ExpectationIconByType';
import GlobalExecutionTraces from '../../../common/injects/status/traces/GlobalExecutionTraces';
import TerminalViewTab from '../../../common/injects/status/traces/TerminalViewTab';
import FindingList from '../../../findings/FindingList';
import ImageWithFallback from './ImageWithFallback';

interface Props {
  loading: boolean;
  detail: AttackPathExecutionDetailDTO | null;
  onClose: () => void;
  // Return to the endpoint/finding panel this execution was opened from (master→detail navigation). When
  // set, a back arrow sits left of the title; `onClose` still fully closes the panel.
  onBack?: () => void;
  // Open the originating inject (pending backend: needs the inject id on the execution detail).
  onOpenInject?: () => void;
  // Friendly endpoint name (e.g. "kingslanding"), resolved by the caller from the graph node — the
  // execution DTO only carries the raw endpoint key (a UUID) and the IP, neither of which reads well.
  endpointLabel?: string;
}

const RESULT_TAB = 'result';
const TERMINAL_TAB = 'terminal';
const FINDINGS_TAB = 'findings';
const REMEDIATION_TAB = 'remediation';

interface PlatformAlert {
  id: string;
  title: string;
  date?: string | null;
}
interface SecurityPlatform {
  /** The platform's collector type, when known — drives its catalog logo. */
  type?: string;
  label: string;
}
/** A platform that acted on the execution, with the alerts it raised. */
interface SecurityPlatformWithAlerts {
  platform: SecurityPlatform;
  alerts: PlatformAlert[];
}

// The platform's catalog logo (collectors brick) with a graceful fallback: on dev, a platform that
// isn't installed 404s its image, so we swap in a generic shield icon instead of a broken image.
const PlatformLogo = ({ type, label }: {
  type: string;
  label: string;
}) => {
  const size = {
    width: 20,
    height: 20,
    borderRadius: 4,
  };
  return (
    <ImageWithFallback
      src={buildTenantApiPath(`/api/collectors/${type}/image`)}
      alt={label}
      fallback={<ShieldOutlined style={size} />}
      style={size}
    />
  );
};

// A security platform asset logo (by asset id + theme mode) with the same graceful fallback.
const SecurityPlatformAssetLogo = ({ platformId, label, mode }: {
  platformId: string;
  label: string;
  mode: string;
}) => {
  const size = {
    width: 20,
    height: 20,
    borderRadius: 4,
  };
  return (
    <ImageWithFallback
      src={buildTenantApiPath(`/api/images/security_platforms/id/${platformId}/${mode}`)}
      alt={label}
      fallback={<ShieldOutlined style={size} />}
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
  const { t, fldt } = useFormatter();
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
        <PlatformLogo type={platform.type ?? ''} label={platform.label} />
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
              {a.date && <Typography variant="caption" color="text.secondary">{fldt(a.date)}</Typography>}
            </div>
          ))}
        </div>
      </Popover>
    </>
  );
};

// Live terminal for a real execution: the attack-path DTO only carries `command`/`terminalOutput` on
// seeded runs, so for a real inject we reuse the shared `TerminalViewTab`, fed by the live
// `execution_traces` — exactly like the inject detail view. The DTO exposes `injectId` but not the
// executed target, so we resolve the inject's asset targets and pick the one matching this execution's
// endpoint (falling back to the first asset), then hand it to `TerminalViewTab`.
const LiveExecutionTerminal = ({ injectId, endpointName }: {
  injectId: string;
  endpointName?: string;
}) => {
  const { t } = useFormatter();
  const [target, setTarget] = useState<InjectTarget | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true);
    searchTargets(injectId, 'ASSETS', {
      filterGroup: {
        mode: 'and',
        filters: [],
      },
      size: 50,
      page: 0,
    })
      .then((response) => {
        if (!active) {
          return;
        }
        const targets: InjectTarget[] = response.data?.content ?? [];
        const match = targets.find(tg => tg.target_name && tg.target_name === endpointName);
        setTarget(match ?? targets[0] ?? null);
      })
      .catch(() => active && setTarget(null))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [injectId, endpointName]);

  if (loading) {
    return <Loader variant="inElement" size="sm" />;
  }
  if (!target?.target_id || !target.target_type) {
    return <Typography variant="body2" color="text.secondary">{t('No traces on this target.')}</Typography>;
  }
  return <TerminalViewTab injectId={injectId} target={target} />;
};

// Terminal for a network injector's execution (NetExec, Nmap…): these have no per-agent terminal, so the
// per-target `TerminalViewTab` above shows nothing. Instead render the inject's global execution traces —
// the very same "Traces" the inject execution-details page shows (the "<tool> succeeded: …" output).
const InjectorExecutionTraces = ({ injectId }: { injectId: string }) => {
  const { t } = useFormatter();
  const [injectStatus, setInjectStatus] = useState<InjectStatusOutput | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true);
    getInjectStatusWithGlobalExecutionTraces(injectId)
      .then((response: { data: InjectStatusOutput }) => active && setInjectStatus(response.data))
      .catch(() => active && setInjectStatus(null))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [injectId]);

  if (loading) {
    return <Loader variant="inElement" size="sm" />;
  }
  if (!injectStatus) {
    return <Typography variant="body2" color="text.secondary">{t('No data available')}</Typography>;
  }
  return <GlobalExecutionTraces injectStatus={injectStatus} />;
};

// The Result & Terminal panel for one execution (issue 5048): an in-flow panel between the execution
// feed and the map (product mockup), not an overlay. Reuses the platform's shared `Terminal` renderer,
// fed by the frozen snapshot's masked command and output. The Result tab shows the target and the
// security platforms that prevented/detected the action (with their linked alerts on click).
const ExecutionResultTerminalPanel = ({ loading, detail, onClose, onBack, onOpenInject, endpointLabel }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { isValidated } = useEnterpriseEdition();
  const { currentTab, handleChangeTab } = useTabs(RESULT_TAB);
  // Detection remediations (per security platform) for this action, resolved from the execution's
  // payload's detection remediations by the backend (empty when the payload has none).
  const detectionRemediations = detail?.detectionRemediations ?? [];

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

  // Seeded runs carry a frozen command/output snapshot on the DTO; a real inject leaves them empty and
  // is rendered live from execution traces instead (see hasSnapshot below).
  const hasSnapshot = Boolean(detail?.command || detail?.terminalOutput);
  // On the terminal tab a network injector shows its execution traces (a plain list that grows), unlike
  // the snapshot/payload `Terminal` which is sized to fill and scrolls internally. The list needs the
  // outer box to scroll, otherwise long traces are clipped.
  const injectorTracesView = !hasSnapshot && !!detail?.injectId && !detail?.payloadId;
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

  // The platforms that actually acted on this execution, resolved by the backend from the inject's
  // expectations with their linked alerts (`securityPlatforms`, one entry per platform per bucket).
  // Nothing is fabricated here: an execution no platform evaluated shows no platform.
  const platformsByBucket = (bucket: 'prevention' | 'detection'): SecurityPlatformWithAlerts[] =>
    (detail?.securityPlatforms ?? [])
      .filter(p => p.bucket === bucket)
      .map(p => ({
        platform: {
          type: p.platformType ?? undefined,
          label: p.platformName ?? t('Unknown platform'),
        },
        alerts: (p.alerts ?? []).map(a => ({
          id: a.id ?? `${p.platformName}-${a.title}`,
          title: a.title ?? '',
          date: a.date ?? p.detectedAt,
        })),
      }));
  const preventedBy = platformsByBucket('prevention');
  const detectedBy = platformsByBucket('detection');
  // Platform attribution is Enterprise-gated: the resolver returns nothing without an active licence,
  // which must never read as "no platform prevented this". Same treatment, minus the Enterprise
  // wording, for any other reason attribution can be absent (a run not yet committed, a target with
  // neither agent nor asset).
  const attributionUnavailable = !isValidated;

  const renderExpectationRow = (
    expectationType: 'prevention' | 'detection',
    heading: string,
    status: string | null | undefined,
    platforms: SecurityPlatformWithAlerts[],
  ) => {
    // The verdict comes from the expectation status, never from "did any platform answer": a pending
    // expectation reads pending (grey), an expiration-stamped one is a real negative (red), and an
    // undeclared expectation says so. Colours come from the shared status vocabulary, which already
    // maps the expectation display labels this projection stores.
    const hasExpectation = !!status && status.trim().length > 0;
    const statusColor = hasExpectation ? getStatusColor(theme, status) : theme.palette.text.disabled;
    const statusLabel = hasExpectation
      ? t(status)
      : t('No expectation for {type}', { type: t(expectationType === 'prevention' ? 'Prevention' : 'Detection') });
    return (
      <div>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          marginBottom: 6,
        }}
        >
          {expectationIconByType(expectationType, { color: statusColor })}
          <Typography variant="subtitle2">{heading}</Typography>
          <Typography variant="caption" sx={{ color: statusColor }}>{statusLabel}</Typography>
        </div>
        {platforms.length === 0
          ? (
              <Typography variant="caption" sx={{ color: theme.palette.text.disabled }}>
                {attributionUnavailable
                  ? t('Platform attribution requires Enterprise Edition')
                  : t('No platform attribution available')}
              </Typography>
            )
          : (
              <div style={{
                display: 'flex',
                gap: 8,
                flexWrap: 'wrap',
              }}
              >
                {platforms.map((p, index) => (
                  <SecurityPlatformItem
                    key={`${p.platform.type ?? 'unknown'}-${p.platform.label}-${index}`}
                    platform={p.platform}
                    alerts={p.alerts}
                  />
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
        // Fills the resizable drawer container (drag the handle to widen when traces overflow).
        flex: 1,
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
        gap: theme.spacing(1),
        padding: theme.spacing(2, 2.5, 1),
        flexShrink: 0,
      }}
      >
        {/* Back to the endpoint/finding panel this execution was opened from. */}
        {onBack && (
          <IconButton
            size="small"
            aria-label={t('Back')}
            onClick={onBack}
            sx={{
              flexShrink: 0,
              mt: 0.25,
            }}
          >
            <ArrowBack fontSize="small" />
          </IconButton>
        )}
        <div style={{
          minWidth: 0,
          flex: 1,
        }}
        >
          <Typography variant="h6" noWrap>{detail?.payloadName || t('Execution')}</Typography>
          <Typography variant="caption" color="text.secondary">
            {[detail?.agentName, detail?.agentPrivilege].filter(Boolean).join(' · ')}
          </Typography>
          {/* MITRE ATT&CK technique(s) this action maps to, resolved server-side from the
              execution's injector contract (AttackPathExecutionDetailDTO.attackPatterns). */}
          {(detail?.attackPatterns?.length ?? 0) > 0 && (
            <div style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 4,
              marginTop: 6,
            }}
            >
              {detail?.attackPatterns?.map(ap => (
                <AttackPatternChip
                  key={ap.externalId}
                  attackPattern={{
                    attack_pattern_id: ap.externalId ?? '',
                    attack_pattern_external_id: ap.externalId ?? '',
                    attack_pattern_name: ap.name ?? '',
                  }}
                />
              ))}
            </div>
          )}
        </div>
        <IconButton size="small" aria-label={t('Close')} onClick={onClose} sx={{ flexShrink: 0 }}>
          <Close />
        </IconButton>
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
                // The tab label follows its content: a seeded snapshot or a payload-backed inject shows a
                // real terminal ("Terminal view"); a network injector shows its execution traces instead
                // ("Execution details").
                label: hasSnapshot || detail.payloadId ? t('Terminal view') : t('Execution details'),
              },
              // The inject's findings (same list as the inject detail's Findings tab), for both endpoint
              // and injector executions — shown only once we have the inject id to scope the search.
              ...(detail.injectId
                ? [{
                    key: FINDINGS_TAB,
                    label: t('Findings'),
                  }]
                : []),
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
              // The Result tab owns its scroll; the snapshot/payload Terminal scrolls internally so its
              // outer box must not add a second scrollbar. The injector traces list, however, needs this
              // box to scroll or long output is clipped.
              overflow: currentTab === TERMINAL_TAB && !injectorTracesView ? 'hidden' : 'auto',
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
                  {/* The friendly endpoint name (kingslanding), not the raw endpoint key/UUID, to stay
                      consistent with the graph node; fall back to the IP only when no name is known. */}
                  <Typography variant="subtitle2">{endpointLabel || detail.targetHostname || detail.targetIp || detail.endpointKey}</Typography>
                </div>
                {/* Enterprise gate on the attribution itself, not on the verdicts: one chip for the
                    section, offering the upsell path instead of a dead-end sentence per row. */}
                {attributionUnavailable && (
                  <EEChip clickable featureDetectedInfo={t('Security platform attribution')} />
                )}
                {renderExpectationRow('prevention', t('Prevented by'), detail?.preventionStatus, preventedBy)}
                {renderExpectationRow('detection', t('Detected by'), detail?.detectionStatus, detectedBy)}
                {/* Jump to the originating inject for the full action definition (pending backend id). */}
                {onOpenInject && (
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={<OpenInNew fontSize="small" />}
                    onClick={onOpenInject}
                    sx={{ alignSelf: 'flex-start' }}
                  >
                    {t('Inject details')}
                  </Button>
                )}
              </div>
            )}

            {currentTab === TERMINAL_TAB && (() => {
              // Seeded runs show their frozen snapshot. For a real inject: a payload-backed execution runs
              // on an agent and has a per-target terminal (keep it); a network injector (NetExec, Nmap…)
              // has none, so show its global execution traces instead.
              if (hasSnapshot || !detail.injectId) {
                return <Terminal lines={terminalLines} maxHeight={terminalMaxHeight} />;
              }
              return detail.payloadId
                ? <LiveExecutionTerminal injectId={detail.injectId} endpointName={endpointLabel || detail.targetHostname || detail.endpointKey} />
                : <InjectorExecutionTraces injectId={detail.injectId} />;
            })()}

            {currentTab === FINDINGS_TAB && detail.injectId && (
              <FindingList
                filterLocalStorageKey="ap-inject-findings"
                searchDistinctFindings={input => searchDistinctFindingsForInjects(detail.injectId as string, input)}
                contextId={detail.injectId}
                // Compact in the narrow drawer: no search/filter/pagination top bar, and drop the
                // asset-groups column (attack-path targets are direct assets, so it's always empty).
                compact
                hiddenFields={['finding_asset_groups', 'finding_created_at', 'finding_updated_at']}
              />
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
                  <div key={rem.detection_remediation_id ?? rem.detection_remediation_security_platform ?? index}>
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      marginBottom: 6,
                    }}
                    >
                      {rem.detection_remediation_security_platform && (
                        <SecurityPlatformAssetLogo
                          platformId={rem.detection_remediation_security_platform}
                          label={rem.detection_remediation_security_platform_name ?? rem.detection_remediation_security_platform}
                          mode={theme.palette.mode}
                        />
                      )}
                      <Typography variant="subtitle2">{rem.detection_remediation_security_platform_name ?? rem.detection_remediation_security_platform}</Typography>
                    </div>
                    {/* Detection rule text is sanitized before rendering — never injected raw. */}
                    <div
                      // eslint-disable-next-line react/no-danger
                      dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize((rem.detection_remediation_values ?? '').replace(/\n/g, '<br/>')) }}
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
