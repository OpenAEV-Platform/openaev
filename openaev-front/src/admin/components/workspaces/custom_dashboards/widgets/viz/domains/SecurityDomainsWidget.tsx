import { FunctionComponent, useState } from 'react';
import { type Domain, EsAvgs, EsDomainsAvgData, EsSeries } from '../../../../../../../utils/api-types';
import { IconBarElement } from '../../../../../common/domains/IconBar-model';
import IconBar from '../../../../../common/domains/IconBar';
import { getDomainByIcon } from './SecurityDomainsWidgetUtils';
import { colorByAverage } from '../../../../../common/ColorByResult';
import ExpectationResultByType from '../../../../../common/domains/ExpectationResultByType';
import { useHelper } from '../../../../../../../store';
import type { DomainHelper } from '../../../../../../../actions/helper';
import { useTheme } from '@mui/material/styles';
import { useFormatter } from '../../../../../../../components/i18n';


interface Props {
  data: EsAvgs;
}

const SecurityDomainsWidget: FunctionComponent<Props> = ({
  data
}) => {

  const theme = useTheme();
  const { t } = useFormatter();

  const [domainType, setDomainType] = useState<string|null>(null);
  const handleClick = (type: string | undefined) => {
    if (type){
      setDomainType((current) => (current === type ? null : type))
    }
  };

  const allDomains: Domain[] = useHelper((helper: DomainHelper) => {
    return helper.getDomains();
  });

  const globalSuccessRate = (domain: EsDomainsAvgData): number => {
    if (domain.data){
      let successTotal = 0;
      let totalData = 0;
      for (const item of domain.data){
        item.value ? totalData += item.value : totalData == 0;
        if (item.data) {
          for (const entry of item.data){
            if(entry.key === "success"){
              entry.value ? successTotal += entry.value : 0;
            }
          }
        }
      }
      return successTotal/totalData;
    } else {
      return 0;
    }

  }


  let iconBarElements: IconBarElement[] = [];

  allDomains.map((domain: Domain) => {
      let selectedDomains = data.security_domain_average.filter(s => s.label === domain.domain_name);
      if (selectedDomains.length > 0){
        if(selectedDomains[0].label !== "To classify" && selectedDomains[0].data) {
          let element: IconBarElement = {
            type: selectedDomains[0].label,
            selectedType: domainType,
            icon: () => getDomainByIcon(selectedDomains[0].label),
            color: colorByAverage(globalSuccessRate(selectedDomains[0])*100),
            name: selectedDomains[0].label,
            results: () => (<ExpectationResultByType results={selectedDomains[0].data}/>),
            expandedResults: () =>(<ExpectationResultByType results={selectedDomains[0].data} inline={true}/>),
            function: () => handleClick(selectedDomains[0].label)
          };
          iconBarElements.push(element);
        }
      } else {
        let emptyResult : EsSeries[] = [
          {label: "prevention", value: - 1, data: []},
          {label: "detection", value: - 1, data: []},
          {label: "vulnerability", value: - 1, data: []}
        ];
        let element: IconBarElement = {
          type: domain.domain_name,
          selectedType: domainType,
          icon: () => getDomainByIcon(domain.domain_name),
          color: colorByAverage(- 1),
          name: domain.domain_name,
          results: () => (<ExpectationResultByType results={emptyResult}/>),
          expandedResults: () =>(<span style={{fontSize: theme.typography.body2.fontSize,color:'rgba(128,127,127,0.37)'}}>{t('No data collected on this domain at this time. Run a scenario to start analyzing your position on this domain.')}</span>),
          function: () => handleClick(domain.domain_name)
        };
        iconBarElements.push(element);
      }
  });


  return(
    <IconBar elements={iconBarElements}/>
  );
};

export default SecurityDomainsWidget;
