import { searchStableFindings } from '../../../actions/findings/finding-actions';
import FindingList from './FindingList';

const Findings = () => {
  return (
    <>
      {/* First-level page: no breadcrumb (US.1, it appears from the second level). */}
      <FindingList
        searchDistinctFindings={searchStableFindings}
        filterLocalStorageKey="findings"
        showArchiveTabs
      />
    </>
  );
};

export default Findings;
