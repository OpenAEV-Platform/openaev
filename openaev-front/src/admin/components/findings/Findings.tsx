import { searchDistinctFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import Tabs from '../../../components/common/tabs/Tabs';
import useRoutedTabs from '../../../components/common/tabs/useRoutedTabs';
import { useFormatter } from '../../../components/i18n';
import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
import FindingList from './FindingList';

// Placeholder catalog id for the Prowler injector (misconfiguration scanner) - mirrors
// ProwlerInjectorIntegration#PROWLER_INJECTOR_ID on the backend. Prowler is not integrated as a
// real connector yet, so the Misconfiguration tab shows no results until findings are actually
// attached to injects using this injector; the filter is wired in advance per product decision.
const PROWLER_INJECTOR_ID = '8f1a2e63-6c7b-4d4a-9b0e-2d7c5a1f9e64';

const withSourceFilter = (input: SearchPaginationInput, operator: 'eq' | 'not_eq'): SearchPaginationInput => {
  const filter: Filter = {
    id: generateFilterId(),
    key: 'finding_source',
    operator,
    values: [PROWLER_INJECTOR_ID],
  };
  return {
    ...input,
    filterGroup: {
      mode: input.filterGroup?.mode ?? 'and',
      filters: [...(input.filterGroup?.filters ?? []), filter],
    },
  };
};

const Findings = () => {
  const { t } = useFormatter();
  // Vulnerability = everything except Prowler-sourced findings; Misconfiguration = only
  // Prowler-sourced findings (per product decision, filtered on finding_source/injector).
  const { currentTab, handleChangeTab } = useRoutedTabs(['vulnerability', 'misconfiguration'], 'vulnerability');

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Findings'),
          current: true,
        }]}
      />
      <Tabs
        entries={[
          {
            key: 'vulnerability',
            label: t('Vulnerability'),
          },
          {
            key: 'misconfiguration',
            label: t('Misconfiguration'),
          },
        ]}
        currentTab={currentTab}
        onChange={handleChangeTab}
      />
      {currentTab === 'vulnerability' && (
        <FindingList
          searchDistinctFindings={(input: SearchPaginationInput) => searchDistinctFindings(withSourceFilter(input, 'not_eq'))}
          filterLocalStorageKey="findings-vulnerability"
        />
      )}
      {currentTab === 'misconfiguration' && (
        <FindingList
          searchDistinctFindings={(input: SearchPaginationInput) => searchDistinctFindings(withSourceFilter(input, 'eq'))}
          filterLocalStorageKey="findings-misconfiguration"
        />
      )}
    </>
  );
};

export default Findings;
