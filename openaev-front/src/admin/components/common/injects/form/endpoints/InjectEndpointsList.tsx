import { useContext, useEffect, useMemo, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';

import { type EndpointHelper } from '../../../../../../actions/assets/asset-helper';
import { useHelper } from '../../../../../../store';
import type { EndpointOutput } from '../../../../../../utils/api-types';
import { EndpointContext } from '../../../../../../utils/context/endpoint/EndpointContext';
import { Can } from '../../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import EndpointPopover from '../../../../assets/endpoints/EndpointPopover';
import EndpointsList from '../../../../assets/endpoints/EndpointsList';
import InjectAddEndpoints from '../../../../simulations/simulation/injects/endpoints/InjectAddEndpoints';

interface Props {
  name: string;
  platforms?: string[];
  architectures?: string;
  disabled?: boolean;
  errorLabel?: string | null;
  label?: string | boolean;
}
const InjectEndpointsList = ({ name, platforms = [], architectures, disabled = false, errorLabel, label }: Props) => {
  const { control, setValue } = useFormContext();
  const { fetchEndpointsByIds } = useContext(EndpointContext);
  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
  const { endpointsMap } = useHelper((helper: EndpointHelper) => ({ endpointsMap: helper.getEndpointsMap() }));

  const endpointIdsWatched = useWatch({
    control,
    name,
  }) as string[];

  const endpointIds = useMemo(() => {
    return Array.isArray(endpointIdsWatched) ? endpointIdsWatched : [];
  }, [endpointIdsWatched]);

  useEffect(() => {
    const endpoints = endpointIds.map(id => endpointsMap[id]).filter(e => e !== undefined) as EndpointOutput[];
    const missingIds = endpointIds.filter(id => !endpointsMap[id]);

    if (missingIds.length > 0) {
      fetchEndpointsByIds(missingIds).then(result => setEndpoints([...result.data, ...endpoints]));
    } else {
      setEndpoints(endpoints);
    }
  }, [endpointIds]);

  const onEndpointChange = (endpointIds: string[]) => setValue(name, endpointIds, { shouldValidate: true });
  const onRemoveEndpoint = (endpointId: string) => setValue(name, endpointIds.filter(id => id !== endpointId), { shouldValidate: true });

  return (
    <>
      <EndpointsList
        endpoints={endpoints}
        renderActions={endpoint => (
          <EndpointPopover
            inline
            agentless={endpoint.asset_agents.length === 0}
            endpoint={endpoint}
            onRemoveFromContext={onRemoveEndpoint}
            removeFromContextLabel="Remove from the inject"
            onDelete={onRemoveEndpoint}
            disabled={disabled}
          />
        )}
      />
      <Can I={ACTIONS.ACCESS} a={SUBJECTS.ASSETS}>
        <InjectAddEndpoints
          endpointIds={endpointIds}
          onSubmit={onEndpointChange}
          platforms={platforms}
          payloadArch={architectures}
          disabled={disabled}
          errorLabel={errorLabel}
          label={label}
        />
      </Can>
    </>
  );
};

export default InjectEndpointsList;
