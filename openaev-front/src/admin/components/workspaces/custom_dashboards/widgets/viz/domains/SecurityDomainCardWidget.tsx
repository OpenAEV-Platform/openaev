import { Button, Divider, List, ListItem, Typography } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import type { DomainHelper } from '../../../../../../../actions/domains/domain-helper';
import { useFormatter } from '../../../../../../../components/i18n';
import { useHelper } from '../../../../../../../store';
import type { Domain } from '../../../../../../../utils/api-types';
import ExpectationPercentResultByType from '../../../../../common/domains/ExpectationPercentResultByType';
import expectationIconByType from '../../../../../common/ExpectationIconByType';
import { CustomDashboardContext } from '../../../CustomDashboardContext';
import {
  DEFAULT_EMPTY_EXPECTATIONS,
  type EsDomainsAvgDataExtended, getIconByDomain,
} from './SecurityDomainsWidgetUtils';

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'flex',
    gap: theme.spacing(1),
    height: 'fit-content',
  },
  headerContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: theme.spacing(1),
    textTransform: 'none',
  },
  headerListIcons: {
    display: 'flex',
    gap: theme.spacing(0.5),
  },
  noDetailedDatas: {
    maxWidth: theme.spacing(25),
    textAlign: 'center',
    alignSelf: 'center',
  },
}));

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
  const { classes } = useStyles();
  const { label: domainName, color: domainStatusColor } = esDomainDatas;

  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);
  const domains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());

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
    <div className={classes.container}>
      <Button
        onClick={() => onCardDomainClick(domainName)}
        className={classes.headerContainer}
      >
        {getIconByDomain(domainName, { color: domainStatusColor })}
        <Typography variant="subtitle1">{t(domainName)}</Typography>
        <span className={classes.headerListIcons}>
          {(esDomainDatas.data.length > 0 ? esDomainDatas.data : DEFAULT_EMPTY_EXPECTATIONS).map(data => (
            expectationIconByType(data.label, { color: data.color })
          ))}
        </span>
      </Button>

      {isOpen && (
        <>
          <Divider orientation="vertical" variant="middle" />
          {esDomainDatas.data.length > 0 ? (
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
            <Typography className={classes.noDetailedDatas} variant="body2">
              {t('No data collected on this domain at this time. Run a scenario to start analyzing your position on this domain.')}
            </Typography>
          )}
        </>
      )}
    </div>

  );
};

export default SecurityDomainCardWidget;
