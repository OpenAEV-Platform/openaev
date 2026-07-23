import { useContext } from 'react';
import { useNavigate } from 'react-router';

import { bulkDeleteAtomicTestings, searchAtomicTestings } from '../../../actions/atomic_testings/atomic-testing-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ButtonCreate from '../../../components/common/ButtonCreate';
import { initSorting } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { AbilityContext, Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import InjectResultList from './InjectResultList';

const AtomicTestings = () => {
  // Standard hooks
  const { t } = useFormatter();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('atomic-testing', buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }));

  const bulkDelete = (params: {
    selectAll: boolean;
    selectedIds: string[];
    deSelectedIds: string[];
  }) => {
    return bulkDeleteAtomicTestings({
      search_pagination_input: params.selectAll ? searchPaginationInput : undefined,
      inject_ids_to_process: params.selectAll ? undefined : params.selectedIds,
      inject_ids_to_ignore: params.deSelectedIds,
    }).then(result => (result.data ?? []) as string[]);
  };

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
        onBulkDelete={canManage ? bulkDelete : undefined}
        deleteConfirmation={count => (count === 1
          ? t('Do you want to delete this atomic testing?')
          : t('Do you want to delete these {count} atomic testings?', { count: String(count) }))}
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
