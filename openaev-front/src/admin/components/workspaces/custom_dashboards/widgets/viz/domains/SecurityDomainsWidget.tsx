import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import type { DomainHelper } from '../../../../../../../actions/helper';
import { useFormatter } from '../../../../../../../components/i18n';
import { useHelper } from '../../../../../../../store';
import { type Domain } from '../../../../../../../utils/api-types';
import { TO_CLASSIFY } from '../../../../../../../utils/domains/domainUtils';
import ExpectationResultByType from '../../../../../common/domains/ExpectationResultByType';
import { type IconBarElement } from '../../../../../common/domains/IconBar-model';
import SecurityDomainsWidgetIconBar from './SecurityDomainsWidgetIconBar';
import {
  buildOrderedDomains, DEFAULT_EMPTY_EXPECTATIONS, EMPTY_DATA,
  type EsAvgsExtended,
  type EsDomainsAvgDataExtended,
  getIconByDomain,
} from './SecurityDomainsWidgetUtils';

interface Props { data: EsAvgsExtended }

const SecurityDomainsWidget: FunctionComponent<Props> = ({ data }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const [domainType, setDomainType] = useState<string | null>(null);
  const handleClick = (type: string | undefined) => type && setDomainType(current => (current === type ? null : type));

  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const iconBarElements: IconBarElement[] = [];

  allDomains.map((domain: Domain) => {
    if (domain.domain_name !== TO_CLASSIFY) {
      const selectedDomains: EsDomainsAvgDataExtended | undefined = data.security_domain_average.filter(s => s.label === domain.domain_name).at(0);
      if (selectedDomains) {
        if (selectedDomains.label !== TO_CLASSIFY && selectedDomains.data) {
          const element: IconBarElement = {
            type: selectedDomains.label,
            selectedType: domainType,
            icon: () => getIconByDomain(selectedDomains.label),
            color: selectedDomains.color ?? EMPTY_DATA,
            name: selectedDomains.label ? selectedDomains.label : t('Unknown'),
            results: () => (<ExpectationResultByType results={selectedDomains.data} />),
            expandedResults: () => (<ExpectationResultByType results={selectedDomains.data} inline={true} />),
            function: () => handleClick(selectedDomains.label),
          };
          iconBarElements.push(element);
        }
      } else {
        const element: IconBarElement = {
          type: domain.domain_name,
          selectedType: domainType,
          icon: () => getIconByDomain(domain.domain_name),
          color: EMPTY_DATA,
          name: domain.domain_name,
          results: () => (<ExpectationResultByType results={DEFAULT_EMPTY_EXPECTATIONS} />),
          expandedResults: () => (
            <span style={{
              fontSize: theme.typography.body2.fontSize,
              color: EMPTY_DATA,
            }}
            >
              {t('No data collected on this domain at this time. Run a scenario to start analyzing your position on this domain.')}
            </span>
          ),
          function: () => handleClick(domain.domain_name),
        };
        iconBarElements.push(element);
      }
    }
  });

  const orderedDomains = buildOrderedDomains(iconBarElements);

  return (
    <SecurityDomainsWidgetIconBar elements={orderedDomains} />
  );
};

export default SecurityDomainsWidget;
