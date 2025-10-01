import { searchDistinctFindings, searchFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import { INJECT, SCENARIO, SIMULATION } from '../../../constants/Entities';
import type { RelatedFindingOutput } from '../../../utils/api-types';
import FindingContextLink from './FindingContextLink';
import FindingList from './FindingList';

const Findings = () => {
  const { t } = useFormatter();

  const additionalFilterNames = [
    'finding_inject_id',
    'finding_scenario',
    'finding_simulation',
  ];

  const additionalHeaders = [
    {
      field: 'finding_scenario',
      label: 'Scenario',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={SCENARIO} />,
    },
    {
      field: 'finding_simulation',
      label: 'Simulation',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={SIMULATION} />,
    },
    {
      field: 'finding_inject',
      label: 'Inject',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={INJECT} />,
    },
  ];
  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Findings'),
          current: true,
        }]}
      />
      <FindingList
        searchDistinctFindings={searchDistinctFindings}
        searchFindings={searchFindings}
        additionalHeaders={additionalHeaders}
        additionalFilterNames={additionalFilterNames}
        filterLocalStorageKey="findings"
      />
    </>
  );
};

export default Findings;
