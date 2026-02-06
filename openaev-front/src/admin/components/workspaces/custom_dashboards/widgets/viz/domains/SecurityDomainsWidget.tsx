import { type FunctionComponent, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type DomainHelper } from '../../../../../../../actions/domains/domain-helper';
import { useHelper } from '../../../../../../../store';
import { type Domain } from '../../../../../../../utils/api-types';
import { TO_CLASSIFY } from '../../../../../../../utils/domains/domainUtils';
import SecurityDomainCardWidget from './SecurityDomainCardWidget';
import {
  EMPTY_DATA,
  type EsAvgsExtended, orderDomains,
} from './SecurityDomainsWidgetUtils';

interface Props {
  widgetId: string;
  data: EsAvgsExtended;
}

const useStyles = makeStyles()(theme => ({
  container: {
    width: '100%',
    display: 'flex',
    justifyContent: 'space-evenly',
    gap: theme.spacing(1),
  },
}));

const SecurityDomainsWidget: FunctionComponent<Props> = ({ widgetId, data }) => {
  const { classes } = useStyles();

  const [selectedDomainName, setSelectedDomainName] = useState<string | null>(null);
  const onCardDomainClick = (type: string) => setSelectedDomainName(current => (current === type ? null : type));

  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const orderedDomains = useMemo(
    () => orderDomains(allDomains.filter(d => d.domain_name !== TO_CLASSIFY)),
    [allDomains],
  );
  const domainDataMap = useMemo(
    () => new Map(data.security_domain_average.map(item => [item.label, item])),
    [data.security_domain_average],
  );

  return (
    <div className={classes.container}>
      {orderedDomains.map((domain) => {
        const { domain_name } = domain;
        const domainData = domainDataMap.get(domain_name) ?? {
          label: domain.domain_name,
          data: [],
          color: EMPTY_DATA,
        };

        return (
          <SecurityDomainCardWidget
            key={domain_name}
            widgetId={widgetId}
            isOpen={selectedDomainName == domain_name}
            esDomainDatas={domainData}
            onCardDomainClick={onCardDomainClick}
          />
        );
      })}
    </div>
  );
};

export default SecurityDomainsWidget;
