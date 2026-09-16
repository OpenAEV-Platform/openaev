import { PlayCircleOutlineOutlined, RouteOutlined } from '@mui/icons-material';
import { Box, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Binoculars, Target } from 'mdi-material-ui';
import { type FunctionComponent, memo, type ReactElement, useContext, useMemo } from 'react';
import { Link } from 'react-router';

import { type SecurityPlatformHelper } from '../../../../../../../actions/assets/asset-helper';
import { useFormatter } from '../../../../../../../components/i18n';
import { useHelper } from '../../../../../../../store';
import { type EsSeries, type SecurityPlatform } from '../../../../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../../../../utils/url-helper';
import { CustomDashboardContext } from '../../../CustomDashboardContext';
import { isSeriesEmpty, sampleExposureSeries } from '../sample/sampleData';
import SamplePreview from '../sample/SamplePreview';
import AttackFlow from './AttackFlow';
import ExposureConsole from './ExposureConsole';

interface Props {
  widgetId: string;
  series: EsSeries[];
}

interface SeriesIndexes {
  success: number;
  failed: number;
  pending: number;
}

/**
 * Resolves which series carries which outcome, by label with a fall back to the
 * declaration order used by `attemptedSeries` in defaultHomeWidgets.
 *
 * The numbers and the drill-downs both read this single resolution. A command-center
 * widget can also be built by hand (WidgetForm exposes the type) with its series in
 * any order, so matching labels to render the tile while addressing series
 * positionally to drill it would recreate exactly the drift this fixes. `-1` means
 * the widget declares no such series.
 */
const resolveSeriesIndexes = (series: EsSeries[]): SeriesIndexes => {
  const byLabel = (needle: string, fallback: number) => {
    const found = series.findIndex(s => (s.label ?? '').toUpperCase().includes(needle));
    if (found >= 0) return found;
    return fallback < series.length ? fallback : -1;
  };
  return {
    success: byLabel('SUCCESS', 0),
    failed: byLabel('FAIL', 1),
    pending: byLabel('PENDING', 2),
  };
};

interface DomainScore {
  key: string;
  success: number;
  failed: number;
  pending: number;
  resilience: number;
}

const computeDomainScores = (series: EsSeries[], indexes: SeriesIndexes): DomainScore[] => {
  const successSeries = series[indexes.success];
  const failedSeries = series[indexes.failed];
  const pendingSeries = series[indexes.pending];
  const byKey = new Map<string, {
    success: number;
    failed: number;
    pending: number;
  }>();
  const accumulate = (data: EsSeries['data'], field: 'success' | 'failed' | 'pending') => {
    (data ?? []).forEach((d) => {
      if (!d.label) return;
      const entry = byKey.get(d.label) ?? {
        success: 0,
        failed: 0,
        pending: 0,
      };
      entry[field] += d.value ?? 0;
      byKey.set(d.label, entry);
    });
  };
  accumulate(successSeries?.data, 'success');
  accumulate(failedSeries?.data, 'failed');
  accumulate(pendingSeries?.data, 'pending');
  return [...byKey.entries()].map(([key, v]) => ({
    key,
    success: v.success,
    failed: v.failed,
    pending: v.pending,
    // Resilience only ever weighs resolved outcomes: a pending expectation has no
    // verdict to score, and counting it would drag every gate rate towards zero.
    resilience: v.success + v.failed > 0 ? Math.round((v.success / (v.success + v.failed)) * 100) : 0,
  }));
};

/**
 * The adversarial exposure command center: a 3D exposure orb (overall verdict),
 * a live kill-chain attack-flow with outcome stats (blocked / detected /
 * breached), and direct calls to action into the offensive workflows. It is the
 * single hero of the home page and intentionally avoids duplicating the
 * per-pillar resilience gauges shown below it.
 */
const CommandCenterWidget: FunctionComponent<Props> = ({ widgetId, series }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { openWidgetResults } = useContext(CustomDashboardContext);

  const securityPlatforms: SecurityPlatform[] = useHelper(
    (helper: SecurityPlatformHelper) => helper.getSecurityPlatforms(),
  );

  const isSample = isSeriesEmpty(series);
  const displaySeries = isSample ? sampleExposureSeries : series;

  // Rendering may fall back to sample data, but a drill-down always queries the real
  // widget - so the indexes it sends must be resolved against the real series.
  const displayIndexes = useMemo(() => resolveSeriesIndexes(displaySeries), [displaySeries]);
  const drillIndexes = useMemo(() => resolveSeriesIndexes(series), [series]);

  const domains = useMemo(() => computeDomainScores(displaySeries, displayIndexes), [displaySeries, displayIndexes]);

  // Every expectation type present in the data becomes a defense gate, in
  // attack-lifecycle order (exploit the vulnerability, then prevention blocks,
  // then detection catches); unknown / future dynamic types simply append.
  const layers = useMemo(() => {
    const rank = (key: string) => {
      const order = ['VULNERABILITY', 'PREVENTION', 'DETECTION', 'MANUAL', 'CHALLENGE', 'ARTICLE'];
      const idx = order.indexOf(key.toUpperCase());
      return idx === -1 ? order.length : idx;
    };
    return [...domains]
      .sort((a, b) => rank(a.key) - rank(b.key) || a.key.localeCompare(b.key))
      .map(d => ({
        key: d.key,
        success: d.success,
        failed: d.failed,
        pending: d.pending,
      }));
  }, [domains]);

  const totals = useMemo(() => domains.reduce(
    (acc, d) => ({
      success: acc.success + d.success,
      failed: acc.failed + d.failed,
      pending: acc.pending + d.pending,
    }),
    {
      success: 0,
      failed: 0,
      pending: 0,
    },
  ), [domains]);

  const exposureScore = totals.success + totals.failed > 0
    ? Math.round((totals.failed / (totals.success + totals.failed)) * 100)
    : 0;
  const totalValidations = totals.success + totals.failed;

  const breached = totals.failed;

  // Orbit the real connected security platforms (their actual logos + names);
  // fall back to a representative sample constellation when none are connected.
  const orbitPlatforms = useMemo(() => {
    if (securityPlatforms.length > 0) {
      return securityPlatforms.map(p => ({
        id: p.asset_id,
        name: p.asset_name,
        type: (p.security_platform_type as string) ?? 'EDR',
        logo: buildTenantApiPath(`/api/images/security_platforms/id/${p.asset_id}/${theme.palette.mode}`),
      }));
    }
    return [
      {
        id: 's1',
        name: 'EDR',
        type: 'EDR',
      },
      {
        id: 's2',
        name: 'SIEM',
        type: 'SIEM',
      },
      {
        id: 's3',
        name: 'NDR',
        type: 'NDR',
      },
      {
        id: 's4',
        name: 'SOAR',
        type: 'SOAR',
      },
      {
        id: 's5',
        name: 'XDR',
        type: 'XDR',
      },
      {
        id: 's6',
        name: 'ISPM',
        type: 'ISPM',
      },
    ];
  }, [securityPlatforms, theme.palette.mode]);

  // Every drill-down names the series it aggregated instead of restating their
  // filters: the list is then the widget's own definition of the number, so the
  // two cannot drift when the series change (#7079).
  const onInvestigate = (typeKey: string) => {
    const declared = (...indexes: number[]) => indexes.filter(index => index >= 0);
    // breached assets: every failed validation, regardless of type
    if (typeKey === 'breach') {
      openWidgetResults({
        widgetId,
        series_indexes: declared(drillIndexes.failed),
      });
      return;
    }
    // adversary: every attempted validation, resolved or still pending
    if (typeKey === 'all') {
      openWidgetResults({
        widgetId,
        series_indexes: declared(drillIndexes.success, drillIndexes.failed, drillIndexes.pending),
      });
      return;
    }
    openWidgetResults({
      widgetId,
      filter_values_map: { inject_expectation_type: [typeKey.toUpperCase()] },
      series_indexes: declared(drillIndexes.success),
    });
  };

  // Icons AND order mirror the left navigation bar (the source of truth):
  // Scenarios, Simulations, Atomic testings, Findings - see LeftBar.tsx.
  const ctas: {
    label: string;
    caption: string;
    icon: ReactElement;
    color: string;
    to: string;
  }[] = [
    {
      label: t('Design a scenario'),
      caption: t('Emulate real-world adversaries'),
      icon: <RouteOutlined />,
      color: theme.palette.secondary.main,
      to: '/admin/scenarios',
    },
    {
      label: t('Launch a simulation'),
      caption: t('Validate your defenses now'),
      icon: <PlayCircleOutlineOutlined />,
      color: theme.palette.primary.main,
      to: '/admin/simulations',
    },
    {
      label: t('Run an atomic test'),
      caption: t('Fire a single technique'),
      icon: <Target />,
      color: theme.palette.warning.main,
      to: '/admin/atomic_testings',
    },
    {
      label: t('Review findings'),
      caption: t('Hunt down validated gaps'),
      icon: <Binoculars />,
      color: theme.palette.error.main,
      to: '/admin/findings',
    },
  ];

  return (
    <SamplePreview active={isSample} variant="subtle">
      <div
        style={{
          height: '100%',
          display: 'grid',
          gridTemplateColumns: 'minmax(240px, 3fr) minmax(400px, 9fr) auto',
          gap: theme.spacing(1.5),
          overflow: 'hidden',
        }}
      >
        {/* LEFT: exposure orb with orbiting connected platforms */}
        <ExposureConsole
          score={exposureScore}
          gaps={breached}
          validations={totalValidations}
          platforms={orbitPlatforms}
          breakdown={layers}
          onInvestigate={() => onInvestigate('breach')}
        />

        {/* CENTER: live, actionable kill-chain */}
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            minWidth: 0,
            minHeight: 0,
          }}
        >
          <AttackFlow
            layers={layers}
            breached={breached}
            onInvestigate={onInvestigate}
          />
        </div>

        {/* RIGHT: compact glass action rail */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            minWidth: 0,
            minHeight: 0,
          }}
        >
          <Box
            className="noDrag"
            sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 1,
              padding: 0.75,
              borderRadius: 1.5,
              border: `1px solid ${theme.palette.divider}`,
              // plain translucent fill: a backdrop-filter here would be re-blurred on every
              // animation frame of the neighboring SVGs for no visible benefit (flat backdrop)
              background: alpha(theme.palette.background.default, 0.5),
            }}
          >
            {ctas.map((cta, i) => (
              <Tooltip key={cta.to} title={`${cta.label} - ${cta.caption}`} placement="left">
                <Box
                  // Real router link (not a JS navigate) so ctrl/cmd+click opens a
                  // new tab; native anchors also handle Enter activation themselves.
                  component={Link}
                  to={cta.to}
                  aria-label={cta.label}
                  sx={{
                    'position': 'relative',
                    'display': 'flex',
                    'alignItems': 'center',
                    'justifyContent': 'center',
                    'width': 38,
                    'height': 38,
                    'borderRadius': 1,
                    'cursor': 'pointer',
                    'textDecoration': 'none',
                    'color': cta.color,
                    'background': alpha(cta.color, 0.08),
                    'transition': 'all 0.2s cubic-bezier(0.22, 1, 0.36, 1)',
                    '& svg': { fontSize: 20 },
                    '&::after': i < ctas.length - 1
                      ? {
                          content: '""',
                          position: 'absolute',
                          bottom: -5,
                          left: '25%',
                          width: '50%',
                          height: '1px',
                          background: theme.palette.divider,
                        }
                      : undefined,
                    '&:hover': {
                      background: alpha(cta.color, 0.2),
                      boxShadow: `inset 0 0 0 1px ${alpha(cta.color, 0.5)}, 0 0 12px ${alpha(cta.color, 0.35)}`,
                      transform: 'translateX(-2px)',
                    },
                  }}
                >
                  {cta.icon}
                </Box>
              </Tooltip>
            ))}
          </Box>
        </div>
      </div>
    </SamplePreview>
  );
};

export default memo(CommandCenterWidget);
