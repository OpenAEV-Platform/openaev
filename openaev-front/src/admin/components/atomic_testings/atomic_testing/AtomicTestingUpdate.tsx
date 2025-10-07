import * as R from 'ramda';
import { type FunctionComponent, useContext } from 'react';

import { updateAtomicTesting } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { type Inject, type InjectResultOutput, type InjectResultOverviewOutput } from '../../../../utils/api-types';
import { EndpointContext } from '../../../../utils/context/endpoint/EndpointContext';
import endpointContextForAtomicTesting from '../../../../utils/context/endpoint/EndpointContextForAtomicTesting';
import UpdateInject from '../../common/injects/UpdateInject';
import { InjectResultOverviewOutputContext, type InjectResultOverviewOutputContextType } from '../InjectResultOverviewOutputContext';

interface Props {
  atomic: InjectResultOutput | InjectResultOverviewOutput;
  open: boolean;
  handleClose: () => void;
}

const AtomicTestingUpdate: FunctionComponent<Props> = ({
  atomic,
  open,
  handleClose,
}) => {
  const { updateInjectResultOverviewOutput } = useContext<InjectResultOverviewOutputContextType>(InjectResultOverviewOutputContext);
  const onUpdateAtomicTesting = async (data: Inject) => {
    const toUpdate = R.pipe(
      R.pick([
        'inject_tags',
        'inject_title',
        'inject_type',
        'inject_injector_contract',
        'inject_description',
        'inject_content',
        'inject_all_teams',
        'inject_documents',
        'inject_assets',
        'inject_asset_groups',
        'inject_teams',
        'inject_tags',
      ]),
    )(data);
    updateAtomicTesting(atomic.inject_id, toUpdate).then((result: { data: InjectResultOverviewOutput }) => {
      updateInjectResultOverviewOutput(result.data);
    });
  };

  const endpointContext = endpointContextForAtomicTesting();
  return (
    <EndpointContext.Provider value={endpointContext}>
      <UpdateInject
        open={open}
        handleClose={handleClose}
        onUpdateInject={onUpdateAtomicTesting}
        injectId={atomic.inject_id}
        isAtomic
      />
    </EndpointContext.Provider>
  );
};

export default AtomicTestingUpdate;
