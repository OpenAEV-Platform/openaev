import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import type { DomainHelper } from '../../../../../../../actions/helper';
import { useFormatter } from '../../../../../../../components/i18n';
import { useHelper } from '../../../../../../../store';
import { type Domain, type EsAvgs, type EsDomainsAvgData, type EsSeries } from '../../../../../../../utils/api-types';
import { colorByAverage, EMPTY_DATA } from '../../../../../common/ColorByResult';
import ExpectationResultByType from '../../../../../common/domains/ExpectationResultByType';
import { type IconBarElement } from '../../../../../common/domains/IconBar-model';
import SecurityDomainsWidgetIconBar from './SecurityDomainsWidgetIconBar';
import { buildOrderedDomains, getIconByDomain } from './SecurityDomainsWidgetUtils';

interface Props { data: EsAvgs }

const SecurityDomainsWidget: FunctionComponent<Props> = ({ data }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const [domainType, setDomainType] = useState<string | null>(null);
  const handleClick = (type: string | undefined) => type && setDomainType(current => (current === type ? null : type));

  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());

  const globalSuccessRate = (domain: EsDomainsAvgData): number => {
    if (!domain.data) return 0;

    let successTotal = 0;
    let totalData = 0;

    for (const item of domain.data) {
      totalData += item.value ?? 0;

      if (item.data) {
        const successEntry = item.data.find(entry => entry.key === 'success');
        successTotal += successEntry?.value ?? 0;
      }
    }

    return totalData === 0 ? 0 : successTotal / totalData;
  };

  const iconBarElements: IconBarElement[] = [];

  allDomains.map((domain: Domain) => {
    if (domain.domain_name !== 'To classify') {
      const selectedDomains = data.security_domain_average.filter(s => s.label === domain.domain_name);
      if (selectedDomains.length > 0) {
        if (selectedDomains[0].label !== 'To classify' && selectedDomains[0].data) {
          const element: IconBarElement = {
            type: selectedDomains[0].label,
            selectedType: domainType,
            icon: () => getIconByDomain(selectedDomains[0].label),
            color: colorByAverage(globalSuccessRate(selectedDomains[0]) * 100),
            name: selectedDomains[0].label ? selectedDomains[0].label : t('Unknown'),
            results: () => (<ExpectationResultByType results={selectedDomains[0].data} />),
            expandedResults: () => (<ExpectationResultByType results={selectedDomains[0].data} inline={true} />),
            function: () => handleClick(selectedDomains[0].label),
          };
          iconBarElements.push(element);
        }
      } else {
        const emptyResult: EsSeries[] = [
          {
            label: 'prevention',
            value: -1,
            data: [],
          },
          {
            label: 'detection',
            value: -1,
            data: [],
          },
          {
            label: 'vulnerability',
            value: -1,
            data: [],
          },
        ];
        const element: IconBarElement = {
          type: domain.domain_name,
          selectedType: domainType,
          icon: () => getIconByDomain(domain.domain_name),
          color: colorByAverage(-1),
          name: domain.domain_name,
          results: () => (<ExpectationResultByType results={emptyResult} />),
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
