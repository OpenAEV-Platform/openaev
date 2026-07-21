import { useNavigate } from 'react-router';

import { searchAtomicTestings } from '../../../actions/atomic_testings/atomic-testing-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ButtonCreate from '../../../components/common/ButtonCreate';
import { initSorting } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import InjectResultList from './InjectResultList';

const AtomicTestings = () => {
  // Standard hooks
  const { t } = useFormatter();
  const navigate = useNavigate();

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('atomic-testing', buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }));

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Atomic testings'),
          current: true,
        }]}
      />
      <InjectResultList
        showActions
        fetchInjects={searchAtomicTestings}
        goTo={injectId => `/admin/atomic_testings/${injectId}`}
        queryableHelpers={queryableHelpers}
        searchPaginationInput={searchPaginationInput}
        createButton={(
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
            <ButtonCreate onClick={() => navigate('/admin/atomic_testings/create')} />
          </Can>
        )}
      />
    </>
  );
};

export default AtomicTestings;
