import { searchDistinctFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import FindingList from './FindingList';

const Findings = () => {
  const { t } = useFormatter();

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
        filterLocalStorageKey="findings"
      />
    </>
  );
};

export default Findings;
