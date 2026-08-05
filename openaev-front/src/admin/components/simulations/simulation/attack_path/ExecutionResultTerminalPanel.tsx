import { ArrowBack, Close, OpenInNew, ShieldOutlined } from '@mui/icons-material';
import { Box, Button, IconButton, Paper, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
// eslint-disable-next-line import/no-named-as-default
import DOMPurify from 'dompurify';
import { useContext, useEffect, useRef, useState } from 'react';

import { searchDistinctFindingsForInjects } from '../../../../../actions/findings/finding-actions';
import { getInjectStatusWithGlobalExecutionTraces, searchTargets } from '../../../../../actions/injects/inject-action';
import AttackPatternChip from '../../../../../components/AttackPatternChip';
import Tabs from '../../../../../components/common/tabs/Tabs';
import useTabs from '../../../../../components/common/tabs/useTabs';
import Terminal, { type TerminalLine } from '../../../../../components/common/terminal/Terminal';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { AttackPathExecutionDetailDTO, AttackPathSecurityPlatformDTO, InjectStatusOutput, InjectTarget } from '../../../../../utils/api-types';
import useEnterpriseEdition from '../../../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import { getStatusColor } from '../../../../../utils/statusUtils';
import { buildTenantApiPath } from '../../../../../utils/url-helper';
import StatusPill from '../../../atomic_testings/atomic_testing/target_result/StatusPill';
import EEChip from '../../../common/entreprise_edition/EEChip';
import expectationIconByType from '../../../common/ExpectationIconByType';
import GlobalExecutionTraces from '../../../common/injects/status/traces/GlobalExecutionTraces';
import TerminalViewTab from '../../../common/injects/status/traces/TerminalViewTab';
import FindingList from '../../../findings/FindingList';
import { InjectorExecutionStatusBadge, PayloadExecutionStatusBadge } from './ExecutionStatusBadge';
import ExpectationPlatformsTable, {
  type ExpectationPlatformAlert,
  type ExpectationPlatformRow,
} from './ExpectationPlatformsTable';
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

interface SecurityPlatformRow {
  key: string;
  type?: string;
  label: string;
  status?: string;
  detectedAt?: string;
  alerts: ExpectationPlatformAlert[];
  sourceId?: string;
  sourceAssetId?: string;
}

const isGenericSecurityPlatformType = (value?: string) => {
  const normalized = (value ?? '').trim().toLowerCase();
  return normalized === 'securityplatform'
    || normalized === 'security_platform_type'
    || normalized === 'security_platform';
};

const isCollectorSlugType = (value?: string) => {
  const normalized = (value ?? '').trim().toLowerCase();
  return normalized.startsWith('openaev_');
};

type LegacyAttackPathDetail = AttackPathExecutionDetailDTO & {
  security_platforms?: AttackPathSecurityPlatformDTO[];
  platforms?: AttackPathSecurityPlatformDTO[];
  preventionPlatforms?: AttackPathSecurityPlatformDTO[];
  detectionPlatforms?: AttackPathSecurityPlatformDTO[];
  vulnerabilityPlatforms?: AttackPathSecurityPlatformDTO[];
  preventedBy?: AttackPathSecurityPlatformDTO[];
  detectedBy?: AttackPathSecurityPlatformDTO[];
  vulnerabilityBy?: AttackPathSecurityPlatformDTO[];
};

const withBucket = (
  list: AttackPathSecurityPlatformDTO[] | undefined,
  bucket: 'prevention' | 'detection' | 'vulnerability',
): AttackPathSecurityPlatformDTO[] => (list ?? []).map(platform => ({
  ...platform,
  bucket: platform.bucket ?? bucket,
}));

// Keep this shared component compatible with both new and legacy backend payload shapes.
const getSecurityPlatforms = (detail: AttackPathExecutionDetailDTO | null): AttackPathSecurityPlatformDTO[] => {
  if (!detail) {
    return [];
  }
  if ((detail.securityPlatforms ?? []).length > 0) {
    return detail.securityPlatforms ?? [];
  }

  const legacy = detail as LegacyAttackPathDetail;
  const directLegacy = legacy.security_platforms ?? legacy.platforms;
  if ((directLegacy ?? []).length > 0) {
    return directLegacy ?? [];
  }

  return [
    ...withBucket(legacy.preventionPlatforms ?? legacy.preventedBy, 'prevention'),
    ...withBucket(legacy.detectionPlatforms ?? legacy.detectedBy, 'detection'),
    ...withBucket(legacy.vulnerabilityPlatforms ?? legacy.vulnerabilityBy, 'vulnerability'),
  ];
};

const toDisplayStatus = (status?: string) => {
  switch ((status ?? '').toUpperCase()) {
    case 'UNKNOWN':
    case 'EXPIRED':
      return 'Expired';
    case 'SUCCESS':
      return 'Success';
    case 'PARTIAL':
      return 'Partial';
    case 'PENDING':
      return 'Pending';
    case 'FAILED':
      return 'Failed';
    default:
      return status ?? '-';
  }
};

const toPlatformRows = (
  list: AttackPathSecurityPlatformDTO[] | undefined,
  bucket: 'prevention' | 'detection' | 'vulnerability',
): SecurityPlatformRow[] => {
  if (!list) {
    return [];
  }
  return list
    .filter(platform => (platform.bucket ?? '').toLowerCase() === bucket)
    .map((platform, index) => {
      const platformWithAsset = platform as AttackPathSecurityPlatformDTO & {
        sourceId?: string;
        source_id?: string;
        sourceAssetId?: string;
        source_asset_id?: string;
        sourceType?: string;
        source_type?: string;
        sourcePlatform?: string;
        source_platform?: string;
        platform?: string;
        platform_type?: string;
        type?: string;
      };
      const explicitType = platform.platformType?.trim();
      const aliasedType = [
        platformWithAsset.sourceType,
        platformWithAsset.source_type,
        platformWithAsset.sourcePlatform,
        platformWithAsset.source_platform,
        platformWithAsset.platform,
        platformWithAsset.platform_type,
        platformWithAsset.type,
      ]
        .map(value => value?.trim())
        .find(Boolean);
      const resolvedType = isGenericSecurityPlatformType(explicitType) && aliasedType
        ? aliasedType
        : explicitType;
      const normalizedType
        = isCollectorSlugType(resolvedType) ? 'collector' : resolvedType;
      return {
        key: `${bucket}-${platform.platformName ?? 'unknown'}-${platform.platformType ?? 'unknown'}-${index}`,
        type: normalizedType || undefined,
        label: platform.platformName ?? 'Unknown platform',
        status: platform.status,
        detectedAt: platform.detectedAt,
        sourceId: platformWithAsset.sourceId ?? platformWithAsset.source_id ?? undefined,
        sourceAssetId: platformWithAsset.sourceAssetId ?? platformWithAsset.source_asset_id ?? undefined,
        alerts: (platform.alerts ?? []).map((a, i) => ({
          id: a.id ?? `${bucket}-alert-${index}-${i}`,
          title: a.title ?? 'Alert',
          date: a.date,
          link: a.link,
        })),
      };
    });
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

const COLLECTOR_TYPE_BY_LABEL: Record<string, string> = {
  'expectations expiration manager': 'openaev_fake_detector',
  'expectations vulnerability manager': 'openaev_expectations_vulnerability_manager',
};

const toCollectorTypeFromLabel = (label: string) => {
  const normalizedLabel = label.trim().toLowerCase();
  if (COLLECTOR_TYPE_BY_LABEL[normalizedLabel]) {
    return COLLECTOR_TYPE_BY_LABEL[normalizedLabel];
  }
  return normalizedLabel
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
};

const PlatformLogo = ({ collectorType, label }: {
  collectorType: string;
  label: string;
}) => {
  const size = {
    width: 20,
    height: 20,
    borderRadius: 4,
  };
  return (
    <ImageWithFallback
      src={buildTenantApiPath(`/api/collectors/${collectorType}/image`)}
      alt={label}
      fallback={<ShieldOutlined style={size} />}
      style={size}
    />
  );
};

const CollectorByIdLogo = ({ collectorId, label }: {
  collectorId: string;
  label: string;
}) => {
  const size = {
    width: 20,
    height: 20,
    borderRadius: 4,
  };
  return (
    <ImageWithFallback
      src={buildTenantApiPath(`/api/collectors/id/${collectorId}/image`)}
      alt={label}
      fallback={<ShieldOutlined style={size} />}
      style={size}
    />
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

// A network injector (NetExec, Nmap…) has no single shell command on the DTO — `command` is only ever
// resolved for Command-payload-backed injects (see InjectExecutionStep#getCommand on the backend).
// What it actually ran is instead reconstructed and partially masked server-side (see
// AttackPathGraphService#injectorCommandLine) from its own redacted execution trace and the inject's
// resolved content — never sent to the client in the clear, unlike reconstructing it here would.
const InjectorCommandLine = ({ commandLine }: { commandLine: string }) => {
  const { t } = useFormatter();
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2" gutterBottom>{t('Command')}</Typography>
      <Typography
        variant="body2"
        sx={{
          fontFamily: 'monospace',
          wordBreak: 'break-all',
          whiteSpace: 'pre-wrap',
        }}
      >
        {commandLine}
      </Typography>
    </Box>
  );
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
  const ability = useContext(AbilityContext);
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
  // A network injector (NetExec, Nmap...) never has a `command` — regardless of whether its
  // terminalOutput snapshot is populated — so its reconstructed command line (server-side, see
  // AttackPathGraphService#injectorCommandLine) renders as an extra block ahead of the traces/snapshot.
  const showsCommandParams = !!detail?.injectorCommandLine;
  // On the terminal tab a network injector shows its execution traces (a plain list that grows) and/or
  // its command params, unlike the snapshot/payload `Terminal` which is sized to fill and scrolls
  // internally. Both need the outer box to scroll, otherwise their content is clipped.
  const injectorTracesView = (!hasSnapshot || showsCommandParams) && !!detail?.injectId && !detail?.payloadId;
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

  // The platform rows come from the backend's execution-level collector snapshot (`securityPlatforms`,
  // one entry per source per bucket). Nothing is fabricated here: an execution no platform evaluated
  // shows no platform row.
  const platforms = getSecurityPlatforms(detail);
  const preventedBy = toPlatformRows(platforms, 'prevention');
  const detectedBy = toPlatformRows(platforms, 'detection');
  const vulnerabilityBy = toPlatformRows(platforms, 'vulnerability');
  const showPrevention = preventedBy.length > 0 || Boolean(detail?.preventionStatus);
  const showDetection = detectedBy.length > 0 || Boolean(detail?.detectionStatus);
  const showVulnerability = vulnerabilityBy.length > 0 || Boolean(detail?.vulnerabilityStatus);
  // Platform attribution is Enterprise-gated: the resolver returns nothing without an active licence,
  // which must never read as "no platform prevented this". Same treatment, minus the Enterprise
  // wording, for any other reason attribution can be absent (a run not yet committed, a target with
  // neither agent nor asset).
  const attributionUnavailable = !isValidated;

  const renderExpectationRow = (
    expectationType: 'prevention' | 'detection' | 'vulnerability',
    heading: string,
    // Raw i18n key, translated once at render time (never pre-translated by the caller).
    emptyLabel: string,
    // The expectation's own verdict from the run projection: the fallback when no platform row
    // exists (e.g. attribution is Enterprise-gated), so an absent attribution never reads as a
    // negative result — the licence gates attribution, not results.
    expectationStatus: string | null | undefined,
    platformRows: SecurityPlatformRow[],
  ) => {
    const hasRows = platformRows.length > 0;
    const hasStatus = !!expectationStatus && expectationStatus.trim().length > 0;
    // Colour the verdict from actual platform rows when we have them, from the run's own
    // expectation status otherwise.
    const succeeded = platformRows.some(p => (p.status ?? '').toUpperCase() === 'SUCCESS');
    let iconColor = theme.palette.error.main;
    if (hasRows) {
      if (succeeded) {
        iconColor = expectationType === 'detection' ? theme.palette.warning.main : theme.palette.success.main;
      }
    } else if (hasStatus) {
      iconColor = getStatusColor(theme, expectationStatus);
    }
    let summaryLabel = emptyLabel;
    let summaryTone = succeeded ? 'SUCCESS' : 'FAILED';
    if (hasRows) {
      if (succeeded) {
        if (expectationType === 'prevention') {
          summaryLabel = 'Prevented';
        } else if (expectationType === 'detection') {
          summaryLabel = 'Detected';
        } else {
          summaryLabel = 'Not vulnerable';
        }
      } else if (expectationType === 'vulnerability') {
        summaryLabel = 'Vulnerable';
      }
    } else if (hasStatus) {
      summaryLabel = expectationStatus;
      summaryTone = expectationStatus;
    }
    const alertsTotal = platformRows.reduce((sum, row) => sum + row.alerts.length, 0);
    // Attribution can be absent for a licence reason (Enterprise-gated resolver) or simply because
    // no platform answered; only the latter is a real negative worth the red wording.
    const emptyState = attributionUnavailable
      ? (
          <Typography variant="caption" sx={{ color: theme.palette.text.disabled }}>
            {t('Platform attribution requires Enterprise Edition')}
          </Typography>
        )
      : <Typography variant="caption" style={{ color: theme.palette.error.main }}>{t(emptyLabel)}</Typography>;

    const tableRows: ExpectationPlatformRow[] = platformRows.map((row) => {
      const normalizedType = row.type?.trim();
      const resolvedType = isGenericSecurityPlatformType(normalizedType)
        ? undefined
        : normalizedType;

      let icon;
      if (row.sourceAssetId) {
        icon = (
          <SecurityPlatformAssetLogo
            platformId={row.sourceAssetId}
            label={row.label}
            mode={theme.palette.mode}
          />
        );
      } else if (row.sourceId) {
        icon = <CollectorByIdLogo collectorId={row.sourceId} label={row.label} />;
      } else {
        icon = (
          <PlatformLogo
            collectorType={toCollectorTypeFromLabel(row.label)}
            label={row.label}
          />
        );
      }

      return {
        key: row.key,
        name: row.label,
        type: resolvedType,
        status: row.status,
        statusLabel: toDisplayStatus(row.status),
        statusTone: row.status,
        detectedAt: row.detectedAt,
        alerts: row.alerts,
        icon: icon,
      };
    });

    return (
      <div>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1.5),
          marginBottom: theme.spacing(1),
        }}
        >
          <div
            aria-hidden
            style={{
              width: 32,
              height: 32,
              borderRadius: 4,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: alpha(iconColor, 0.12),
              color: iconColor,
              flexShrink: 0,
            }}
          >
            {expectationIconByType(expectationType, { color: iconColor })}
          </div>
          <div style={{
            minWidth: 0,
            flex: 1,
          }}
          >
            <Typography variant="subtitle2">{heading}</Typography>
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
              {expectationType.toUpperCase()}
            </Typography>
          </div>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1),
          }}
          >
            <StatusPill label={t(summaryLabel)} status={summaryTone} />
            <span
              style={{
                minWidth: 34,
                height: 22,
                borderRadius: 4,
                padding: theme.spacing(0, 0.75),
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: alpha(iconColor, 0.12),
                color: iconColor,
                fontSize: 12,
                fontWeight: 700,
              }}
            >
              {alertsTotal}
            </span>
          </div>
        </div>
        {platformRows.length === 0
          ? emptyState
          : <ExpectationPlatformsTable rows={tableRows} />}
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
      {/* Header in the app Drawer language (h5 + close over the standard divider), consistent with
          the endpoint/finding master panels this detail view replaces. The title row centers the
          back and close controls on the h5 line; subtitle and chips flow below, indented under the
          title so they stay aligned with it when the back arrow is present. */}
      <div style={{
        padding: theme.spacing(2, 2.5, 1.5),
        borderBottom: `1px solid ${theme.palette.divider}`,
        flexShrink: 0,
      }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          {/* Back to the endpoint/finding panel this execution was opened from. */}
          {onBack && (
            <IconButton
              size="small"
              aria-label={t('Back')}
              onClick={onBack}
              sx={{ flexShrink: 0 }}
            >
              <ArrowBack fontSize="small" />
            </IconButton>
          )}
          <Typography
            variant="h5"
            noWrap
            sx={{
              flex: 1,
              minWidth: 0,
              margin: 0,
            }}
          >
            {detail?.payloadName || t('Execution')}
          </Typography>
          <IconButton size="small" aria-label={t('Close')} onClick={onClose} sx={{ flexShrink: 0 }}>
            <Close fontSize="small" />
          </IconButton>
        </div>
        {/* 38px = the 30px back IconButton + the 8px row gap, so these lines start under the title. */}
        <div style={{ paddingLeft: onBack ? 38 : 0 }}>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            {[detail?.agentName, detail?.agentPrivilege].filter(Boolean).join(' · ')}
          </Typography>
          {/* MITRE ATT&CK technique(s) this action maps to, resolved server-side from the
               execution's injector contract (AttackPathExecutionDetailDTO.attackPatterns). */}
          {(detail?.attackPatterns?.length ?? 0) > 0 && (
            <div style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: theme.spacing(0.5),
              marginTop: theme.spacing(0.75),
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
      </div>

      {loading && (
        <div style={{ minHeight: 120 }}>
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
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 1,
                }}
                >
                  {/* The friendly endpoint name (kingslanding), not the raw endpoint key/UUID, to stay
                       consistent with the graph node; fall back to the IP only when no name is known. */}
                  <Typography variant="subtitle2">{endpointLabel || detail.targetHostname || detail.targetIp || detail.endpointKey}</Typography>
                  {/* Whether the action actually ran at all (issue 244): those verdicts below answer "was
                      it caught?", never "did it run?" — a technical failure and a clean-but-undetected run
                      previously looked identical. Sits beside the endpoint name (not its own row) to save
                      vertical space. */}
                  {detail.injectId && (
                    <Box sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 0.75,
                      flexShrink: 0,
                    }}
                    >
                      <Typography
                        variant="caption"
                        color="text.secondary"
                        sx={{
                          textTransform: 'uppercase',
                          letterSpacing: '0.06em',
                        }}
                      >
                        {t('Status')}
                        :
                      </Typography>
                      {detail.payloadId
                        ? (
                            <PayloadExecutionStatusBadge
                              injectId={detail.injectId}
                              endpointName={endpointLabel || detail.targetHostname || detail.endpointKey}
                            />
                          )
                        : <InjectorExecutionStatusBadge injectId={detail.injectId} />}
                    </Box>
                  )}
                </Box>
                {/* Enterprise gate on the attribution itself, not on the verdicts: one chip for the
                    section, offering the upsell path instead of a dead-end sentence per row. Only
                    clickable for a user who could act on the dialog — the licence form it opens
                    requires platform settings, so for anyone else the chip stays informational
                    rather than a dead click (EETooltip's nuance). */}
                {attributionUnavailable && (
                  <EEChip
                    clickable={ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS)}
                    featureDetectedInfo={t('Security platform attribution')}
                  />
                )}
                {showPrevention && renderExpectationRow('prevention', t('Prevention'), 'Not Prevented', detail?.preventionStatus, preventedBy)}
                {showDetection && renderExpectationRow('detection', t('Detection'), 'Not Detected', detail?.detectionStatus, detectedBy)}
                {showVulnerability && renderExpectationRow('vulnerability', t('Vulnerability'), 'Not vulnerable', detail?.vulnerabilityStatus, vulnerabilityBy)}
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

            {currentTab === TERMINAL_TAB && (
              <>
                {/* A network injector (NetExec, Nmap…) has no `command` regardless of whether its
                    terminalOutput snapshot is populated — show what was actually sent either way,
                    ahead of whichever traces/output view renders below. */}
                {detail.injectorCommandLine && (
                  <InjectorCommandLine commandLine={detail.injectorCommandLine} />
                )}
                {(() => {
                  // Seeded runs show their frozen snapshot. For a real inject: a payload-backed execution
                  // runs on an agent and has a per-target terminal (keep it); a network injector has none,
                  // so show its global execution traces instead.
                  if (hasSnapshot || !detail.injectId) {
                    return <Terminal lines={terminalLines} maxHeight={terminalMaxHeight} />;
                  }
                  return detail.payloadId
                    ? <LiveExecutionTerminal injectId={detail.injectId} endpointName={endpointLabel || detail.targetHostname || detail.endpointKey} />
                    : <InjectorExecutionTraces injectId={detail.injectId} />;
                })()}
              </>
            )}

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
                      gap: theme.spacing(0.75),
                      marginBottom: theme.spacing(0.75),
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
