import * as R from 'ramda';
import { useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';

import { createAtomicTesting } from '../../../actions/atomic_testings/atomic-testing-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { type AtomicTestingInput, type InjectResultOverviewOutput } from '../../../utils/api-types';
import { EndpointContext } from '../../../utils/context/endpoint/EndpointContext';
import endpointContextForAtomicTesting from '../../../utils/context/endpoint/EndpointContextForAtomicTesting';
import { TeamContext } from '../common/Context';
import InjectContractPicker from '../common/injects/create/InjectContractPicker';
import InjectCreationConfig from '../common/injects/create/InjectCreationConfig';
import teamContextForAtomicTesting from './atomic_testing/context/TeamContextForAtomicTesting';

// Full-page atomic test creation: the Threat-Arsenal-style contract picker
// (atomic-capable contracts only, single-select) stays on screen; selecting a
// contract opens the configuration form in a drawer (deep links with a
// :contractId still open the drawer on load).
const AtomicTestingCreation = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const { contractId } = useParams() as { contractId?: string };
  // Deep links (e.g. the empty-state CTA of a TTP-scoped dashboard drill-down)
  // can pre-scope the contract picker on specific attack patterns:
  // /admin/atomic_testings/create?attack_patterns=<id>,<id>
  const [searchParams] = useSearchParams();
  const initialAttackPatternIds = useMemo(
    () => (searchParams.get('attack_patterns') ?? '').split(',').filter(Boolean),
    // Snapshot at mount: the picker only consumes the seed once.
    [],
  );

  const listUrl = '/admin/atomic_testings';
  const pickerUrl = `${listUrl}/create`;
  const endpointContext = endpointContextForAtomicTesting();

  const [selectedContractId, setSelectedContractId] = useState<string | null>(contractId ?? null);
  const closeConfig = () => {
    setSelectedContractId(null);
    // Normalize deep-linked URLs back to the picker without remounting it.
    if (contractId) {
      navigate(pickerUrl, { replace: true });
    }
  };

  const onCreateAtomicTesting = async (data: AtomicTestingInput) => {
    const toCreate = R.pipe(
      R.assoc('inject_tags', data.inject_tags),
      R.assoc('inject_title', data.inject_title),
      R.assoc('inject_all_teams', data.inject_all_teams),
      R.assoc('inject_asset_groups', data.inject_asset_groups),
      R.assoc('inject_assets', data.inject_assets),
      R.assoc('inject_content', data.inject_content),
      R.assoc('inject_injector_contract', data.inject_injector_contract),
      R.assoc('inject_injector', data.inject_injector),
      R.assoc('inject_description', data.inject_description),
      R.assoc('inject_documents', data.inject_documents),
      R.assoc('inject_teams', data.inject_teams),
    )(data);
    await createAtomicTesting(toCreate).then((result: { data: InjectResultOverviewOutput }) => {
      navigate(`${listUrl}/${result.data.inject_id}`);
    });
  };

  return (
    <TeamContext.Provider value={teamContextForAtomicTesting()}>
      <EndpointContext.Provider value={endpointContext}>
        <Breadcrumbs
          variant="list"
          elements={[
            {
              label: t('Atomic testings'),
              link: listUrl,
            },
            {
              label: t('Create a new atomic test'),
              current: true,
            },
          ]}
        />
        <InjectContractPicker
          title={t('Create a new atomic test')}
          isAtomic
          initialAttackPatternIds={initialAttackPatternIds}
          onSelectContract={contract => setSelectedContractId(contract.injector_contract_id)}
          onBack={() => navigate(listUrl)}
        />
        <Drawer
          open={!!selectedContractId}
          handleClose={closeConfig}
          title={t('Create a new atomic test')}
          disableEnforceFocus
        >
          {selectedContractId
            ? (
                <InjectCreationConfig
                  contractId={selectedContractId}
                  isAtomic
                  onCreateInject={onCreateAtomicTesting as (data: AtomicTestingInput) => Promise<void>}
                  onBack={closeConfig}
                />
              )
            : null}
        </Drawer>
      </EndpointContext.Provider>
    </TeamContext.Provider>
  );
};

export default AtomicTestingCreation;
