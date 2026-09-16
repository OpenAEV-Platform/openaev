import { searchDistinctFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import type { Page } from '../../../components/common/queryable/Page';
import { useFormatter } from '../../../components/i18n';
import type { AggregatedFindingOutput, SearchPaginationInput } from '../../../utils/api-types';
import FindingList from './FindingList';
import {
  addProwlerPrototypeFindings,
  getProwlerPrototypeSearchInput,
} from './prowler_prototype/prowler-findings.fixture';

const Findings = () => {
  const { t } = useFormatter();
  const searchFindingsWithPrototype = (input: SearchPaginationInput): Promise<{ data: Page<AggregatedFindingOutput> }> => (
    searchDistinctFindings(getProwlerPrototypeSearchInput(input))
      .then(response => addProwlerPrototypeFindings(response, input))
  );

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
        searchDistinctFindings={searchFindingsWithPrototype}
        filterLocalStorageKey="findings"
        showArchiveTabs
      />
    </>
  );
};

export default Findings;
