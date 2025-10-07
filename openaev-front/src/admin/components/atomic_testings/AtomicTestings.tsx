import * as R from 'ramda';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { createAtomicTesting, searchAtomicTestings } from '../../../actions/atomic_testings/atomic-testing-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ButtonCreate from '../../../components/common/ButtonCreate';
import { initSorting } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { type AtomicTestingInput, type InjectResultOverviewOutput } from '../../../utils/api-types';
import { EndpointContext } from '../../../utils/context/endpoint/EndpointContext';
import endpointContextForAtomicTesting from '../../../utils/context/endpoint/EndpointContextForAtomicTesting';
import { Can } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { TeamContext } from '../common/Context';
import CreateInject from '../common/injects/CreateInject';
import teamContextForAtomicTesting from './atomic_testing/context/TeamContextForAtomicTesting';
import InjectResultList from './InjectResultList';

const AtomicTestings = () => {
  // Standard hooks
  const { t } = useFormatter();
  const navigate = useNavigate();
  const [openCreateDrawer, setOpenCreateDrawer] = useState(false);

  const onCreateAtomicTesting = async (data: AtomicTestingInput) => {
    const toCreate = R.pipe(
      R.assoc('inject_tags', data.inject_tags),
      R.assoc('inject_title', data.inject_title),
      R.assoc('inject_all_teams', data.inject_all_teams),
      R.assoc('inject_asset_groups', data.inject_asset_groups),
      R.assoc('inject_assets', data.inject_assets),
      R.assoc('inject_content', data.inject_content),
      R.assoc('inject_injector_contract', data.inject_injector_contract),
      R.assoc('inject_description', data.inject_description),
      R.assoc('inject_documents', data.inject_documents),
      R.assoc('inject_teams', data.inject_teams),
    )(data);
    await createAtomicTesting(toCreate).then((result: { data: InjectResultOverviewOutput }) => {
      navigate(`/admin/atomic_testings/${result.data.inject_id}`);
    });
  };

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('atomic-testing', buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }));
  const endpointContext = endpointContextForAtomicTesting();

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
      />

      <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
        <>
          <ButtonCreate onClick={() => setOpenCreateDrawer(true)} />
          <TeamContext.Provider value={teamContextForAtomicTesting()}>
            <EndpointContext.Provider value={endpointContext}>
              <CreateInject
                title={t('Create a new atomic test')}
                onCreateInject={onCreateAtomicTesting}
                isAtomic
                open={openCreateDrawer}
                handleClose={() => setOpenCreateDrawer(false)}
              />
            </EndpointContext.Provider>
          </TeamContext.Provider>
        </>
      </Can>
    </>
  );
};

export default AtomicTestings;
