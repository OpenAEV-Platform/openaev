import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { TO_CLASSIFY } from '../../../../../utils/domains/domainUtils';
import { capitalize } from '../../../../../utils/String';
import expectationIconByType from '../../../common/ExpectationIconByType';
import {
  calcPercentage,
  colorByAverageForExpectation,
  DEFAULT_EMPTY_EXPECTATIONS,
  determinePercentage,
  EMPTY_DATA,
  type EsDomainsAvgDataExtended,
  formatPercentage,
  getIconByDomain,
  getOrderByDomain,
  isStatus,
} from '../../../workspaces/custom_dashboards/widgets/viz/domains/SecurityDomainsWidgetUtils';
import { type ModuleDataState, type SecurityDomainsData } from '../useReportingRenderData';
import { ModuleEmpty, ModuleError } from './ModuleSection';

/**
 * Performance by security domain, print edition of the home dashboard band:
 * one card per referential domain (untested domains stay visible as gaps)
 * with the overall success rate and the per-expectation-type rates. Reuses
 * the widget's math helpers so the report can never contradict the dashboard;
 * the interactive band itself depends on the Redux domain store and
 * click-to-investigate context, hence this deterministic equivalent.
 */

interface Props { domains: ModuleDataState<SecurityDomainsData> }

/** Success rate over RESOLVED expectations only (same math as the band). */
const successRate = (series: EsDomainsAvgDataExtended['data']): number => {
  const totals = series.reduce(
    (acc, serie) => ({
      success: acc.success + serie.data
        .filter(bucket => isStatus(bucket.key, 'SUCCESS'))
        .reduce((sum, bucket) => sum + (bucket.value ?? 0), 0),
      resolved: acc.resolved + serie.data
        .filter(bucket => isStatus(bucket.key, 'SUCCESS') || isStatus(bucket.key, 'FAILED'))
        .reduce((sum, bucket) => sum + (bucket.value ?? 0), 0),
    }),
    {
      success: 0,
      resolved: 0,
    },
  );
  return calcPercentage(totals.success, totals.resolved);
};

const SecurityDomainsModule: FunctionComponent<Props> = ({ domains }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const cards = useMemo(() => {
    if (domains.status !== 'success' || !domains.data) return [];
    const extended = determinePercentage(domains.data.avgs, theme);
    const byName = new Map(extended.security_domain_average.map(entry => [entry.label, entry]));
    return domains.data.domains
      .filter(domain => domain.domain_name && domain.domain_name !== TO_CLASSIFY)
      .sort((a, b) => getOrderByDomain(a.domain_name) - getOrderByDomain(b.domain_name))
      .map((domain) => {
        const data = byName.get(domain.domain_name) ?? {
          label: domain.domain_name,
          data: [],
          color: EMPTY_DATA,
        } as EsDomainsAvgDataExtended;
        const score = successRate(data.data);
        return {
          name: domain.domain_name,
          data,
          score,
          bandColor: colorByAverageForExpectation(score, theme),
        };
      });
  }, [domains, theme]);

  if (domains.status === 'error') return <ModuleError />;
  if (domains.status !== 'success' || !domains.data || cards.length === 0
    || cards.every(card => card.data.data.length === 0)) {
    return <ModuleEmpty message={t('No expectation was resolved over the selected time range.')} />;
  }

  return (
    <Box sx={{
      display: 'grid',
      gridTemplateColumns: 'repeat(3, 1fr)',
      gap: 2,
    }}
    >
      {cards.map(({ name, data, score, bandColor }) => {
        const hasData = data.data.length > 0;
        const hasScore = score >= 0;
        return (
          <Box
            key={name}
            sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 1.25,
              padding: 2,
              borderRadius: 1,
              border: `1px solid ${theme.palette.divider}`,
              backgroundColor: alpha(theme.palette.background.paper, hasData ? 1 : 0.5),
              breakInside: 'avoid',
            }}
          >
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
            >
              <Box sx={{
                width: 26,
                height: 26,
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 0.5,
                backgroundColor: alpha(bandColor, 0.15),
              }}
              >
                {getIconByDomain(name, {
                  color: bandColor,
                  fontSize: 16,
                })}
              </Box>
              <Typography sx={{
                flex: 1,
                fontSize: 11,
                fontWeight: 600,
                lineHeight: 1.25,
                color: hasData ? 'text.primary' : 'text.disabled',
              }}
              >
                {t(name)}
              </Typography>
              <Typography sx={{
                fontFamily: '"Geologica", sans-serif',
                fontSize: 18,
                fontWeight: 600,
                lineHeight: 1,
                color: hasScore ? bandColor : 'text.disabled',
              }}
              >
                {hasScore ? formatPercentage(score) : '—'}
              </Typography>
            </Box>
            <Box sx={{
              height: 4,
              borderRadius: 2,
              overflow: 'hidden',
              backgroundColor: alpha(theme.palette.text.primary, 0.08),
            }}
            >
              <Box sx={{
                width: hasScore ? `${score}%` : 0,
                height: '100%',
                borderRadius: 2,
                backgroundColor: bandColor,
              }}
              />
            </Box>
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 0.5,
            }}
            >
              {(hasData ? data.data : DEFAULT_EMPTY_EXPECTATIONS).map((serie) => {
                const rate = successRate([serie]);
                const rateColor = colorByAverageForExpectation(rate, theme);
                return (
                  <Box
                    key={`${name}-${serie.label}`}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 0.75,
                    }}
                  >
                    <Box sx={{
                      display: 'inline-flex',
                      color: rateColor,
                    }}
                    >
                      {expectationIconByType(serie.label, { fontSize: 13 })}
                    </Box>
                    <Typography sx={{
                      flex: 1,
                      fontSize: 9.5,
                      fontWeight: 600,
                      letterSpacing: '0.08em',
                      textTransform: 'uppercase',
                      color: 'text.secondary',
                    }}
                    >
                      {t(capitalize(serie.label))}
                    </Typography>
                    <Typography sx={{
                      fontFamily: '"Geologica", sans-serif',
                      fontSize: 11,
                      fontWeight: 700,
                      color: rate >= 0 ? rateColor : 'text.disabled',
                    }}
                    >
                      {rate >= 0 ? formatPercentage(rate) : '—'}
                    </Typography>
                  </Box>
                );
              })}
            </Box>
          </Box>
        );
      })}
    </Box>
  );
};

export default SecurityDomainsModule;
