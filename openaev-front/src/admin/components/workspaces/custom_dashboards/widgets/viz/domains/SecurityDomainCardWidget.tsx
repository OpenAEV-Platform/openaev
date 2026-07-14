import { ButtonBase, List, ListItem, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useMemo } from 'react';

import type { DomainHelper } from '../../../../../../../actions/domains/domain-helper';
import { useFormatter } from '../../../../../../../components/i18n';
import { useHelper } from '../../../../../../../store';
import type { Domain } from '../../../../../../../utils/api-types';
import useCountUp from '../../../../../../../utils/hooks/useCountUp';
import { capitalize } from '../../../../../../../utils/String';
import ExpectationPercentResultByType from '../../../../../common/domains/ExpectationPercentResultByType';
import expectationIconByType from '../../../../../common/ExpectationIconByType';
import { CustomDashboardContext } from '../../../CustomDashboardContext';
import {
  calcPercentage,
  colorByAverageForExpectation,
  DEFAULT_EMPTY_EXPECTATIONS,
  type EsDomainsAvgDataExtended,
  getIconByDomain,
} from './SecurityDomainsWidgetUtils';

interface Props {
  widgetId: string;
  isOpen: boolean;
  esDomainDatas: EsDomainsAvgDataExtended;
  onCardDomainClick: (domainName: string) => void;
}

const SecurityDomainCardWidget: FunctionComponent<Props> = ({
  widgetId,
  isOpen = false,
  onCardDomainClick,
  esDomainDatas,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const { label: domainName } = esDomainDatas;

  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);
  const domains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());

  const hasData = esDomainDatas.data.length > 0;

  // Overall success rate for the domain: sum of successful expectations over total expectations.
  const score = useMemo(() => {
    const totals = esDomainDatas.data.reduce(
      (acc, serie) => ({
        success: acc.success + (serie.data.find(d => d.key === 'success')?.value ?? 0),
        total: acc.total + (serie.value ?? 0),
      }),
      {
        success: 0,
        total: 0,
      },
    );
    return calcPercentage(totals.success, totals.total);
  }, [esDomainDatas.data]);

  const hasScore = score >= 0;
  const bandColor = colorByAverageForExpectation(score, theme);
  const animatedScore = useCountUp(hasScore ? score : 0, 1200);

  const onPercentClick = (expectationType: string, expectationStatus: string) => {
    const domain = domains.find(d => d.domain_name === domainName);
    if (!domain) {
      return;
    }
    openWidgetDataDrawer({
      widgetId,
      filter_values_map: {
        base_security_domains_side: [domain?.domain_id],
        inject_expectation_type: [expectationType],
        inject_expectation_status: [expectationStatus],
      },
      series_index: 0,
    });
  };

  return (
    <section
      style={{
        display: 'flex',
        alignItems: 'stretch',
        gap: theme.spacing(1.5),
        minWidth: 0,
        height: '100%',
        flex: isOpen ? '0 0 auto' : '1 1 0',
      }}
    >
      <ButtonBase
        onClick={() => onCardDomainClick(domainName)}
        aria-expanded={isOpen}
        sx={{
          'flex': '1 1 0',
          'minWidth': theme.spacing(14),
          'display': 'flex',
          'flexDirection': 'column',
          'alignItems': 'stretch',
          'justifyContent': 'space-between',
          'gap': 1,
          'padding': 1.5,
          'borderRadius': 1,
          'textAlign': 'left',
          'color': 'text.primary',
          'backgroundColor': alpha(theme.palette.background.accent ?? theme.palette.background.paper, hasData ? 0.5 : 0.25),
          'border': '1px solid',
          'borderColor': isOpen ? alpha(bandColor, 0.55) : theme.palette.divider,
          'transition': 'transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease',
          '&:hover': {
            transform: 'translateY(-2px)',
            borderColor: alpha(bandColor, 0.55),
            boxShadow: `0 4px 14px ${alpha(theme.palette.common.black, 0.35)}, 0 0 0 1px ${alpha(bandColor, 0.15)}`,
          },
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1),
            minWidth: 0,
          }}
        >
          <div
            style={{
              width: 30,
              height: 30,
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 4,
              backgroundColor: alpha(bandColor, 0.15),
            }}
          >
            {getIconByDomain(domainName, {
              color: bandColor,
              fontSize: 18,
            })}
          </div>
          <Typography
            variant="body2"
            sx={{
              fontWeight: 600,
              fontSize: 12,
              lineHeight: 1.25,
              color: hasData ? 'text.primary' : 'text.disabled',
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
          >
            {t(domainName)}
          </Typography>
        </div>

        <Typography
          sx={{
            fontFamily: '"Geologica", sans-serif',
            fontWeight: 600,
            fontSize: 22,
            lineHeight: 1,
            color: hasScore ? bandColor : 'text.disabled',
          }}
        >
          {hasScore ? `${Math.round(animatedScore)}%` : '—'}
        </Typography>

        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(0.75),
          }}
        >
          <div
            style={{
              height: 4,
              borderRadius: 2,
              overflow: 'hidden',
              backgroundColor: alpha(theme.palette.text.primary, 0.08),
            }}
          >
            <div
              style={{
                width: hasScore ? `${animatedScore}%` : 0,
                height: '100%',
                borderRadius: 2,
                backgroundColor: bandColor,
                transition: 'width 0.2s linear',
              }}
            />
          </div>
          <div
            style={{
              display: 'flex',
              gap: theme.spacing(0.75),
            }}
          >
            {(hasData ? esDomainDatas.data : DEFAULT_EMPTY_EXPECTATIONS).map(data => (
              <Tooltip key={`${domainName}-icon-${data.label}`} title={t(capitalize(data.label))}>
                <span
                  style={{
                    display: 'inline-flex',
                    color: data.color,
                  }}
                >
                  {expectationIconByType(data.label, { fontSize: 14 })}
                </span>
              </Tooltip>
            ))}
          </div>
        </div>
      </ButtonBase>

      {isOpen && (
        <div
          style={{
            flex: '0 0 auto',
            display: 'flex',
            alignItems: 'center',
            padding: theme.spacing(0.5, 1.5),
            borderRadius: 4,
            border: `1px solid ${theme.palette.divider}`,
            backgroundColor: alpha(theme.palette.background.accent ?? theme.palette.background.paper, 0.3),
          }}
        >
          {hasData ? (
            <List disablePadding>
              {esDomainDatas.data.map(({ label, color, data }) => (
                <ListItem key={`${domainName}-${label}`} disablePadding disableGutters>
                  <ExpectationPercentResultByType
                    datasByDomainsAndType={data}
                    expectationType={label}
                    color={color}
                    onExpectationResultClick={status => onPercentClick(label, status)}
                  />
                </ListItem>
              ))}
            </List>
          ) : (
            <Typography
              variant="body2"
              sx={{
                maxWidth: theme.spacing(25),
                textAlign: 'center',
                alignSelf: 'center',
              }}
            >
              {t('No data collected on this domain at this time. Run a scenario to start analyzing your position on this domain.')}
            </Typography>
          )}
        </div>
      )}
    </section>
  );
};

export default SecurityDomainCardWidget;
