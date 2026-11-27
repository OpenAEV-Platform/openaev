import { searchFindings } from '../../../actions/findings/finding-actions';
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
        searchFindings={searchFindings}
        filterLocalStorageKey="findings"
      />
    </>
  );
};

export default Findings;
