import { type FunctionComponent, useRef, useState } from 'react';

import type { EndpointHelper } from '../../../../actions/assets/asset-helper';
import { fetchEndpoint, updateAssetMarkings, updateEndpoint } from '../../../../actions/assets/endpoint-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { useHelper } from '../../../../store';
import type { Endpoint, EndpointInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { type AssetCategory } from '../asset-categories';
import AssetForm from '../AssetForm';

interface Props {
  open: boolean;
  handleClose: () => void;
  agentless?: boolean;
  onUpdate?: (result: Endpoint) => void;
  endpointId: string;
}

const EndpointUpdate: FunctionComponent<Props> = ({
  open,
  handleClose,
  agentless,
  onUpdate,
  endpointId,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const [loading, setLoading] = useState(true);
  const dispatch = useAppDispatch();

  const { endpoint } = useHelper((helper: EndpointHelper) => ({ endpoint: helper.getEndpoint(endpointId) }));
  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchEndpoint(endpointId)).finally(() => setLoading(false));
  });

  // EndpointOverviewOutput - the shape PUT /api/endpoints/{id} actually responds with - has no
  // asset_markings field at all (it's a different DTO than the one the assets search list reads).
  // So the fresh entity `onSubmit` gets back can never carry markings, and passing it straight to
  // `onUpdate` would make the list row's markings go stale (or blank) until the next full reload.
  // Tracked here and patched in below, since we already know exactly what we just persisted -
  // onMarkingsChange always resolves before onSubmit runs (see AssetForm).
  const lastMarkingIdsRef = useRef<string[] | null>(null);

  const onSubmit = (data: EndpointInput) => {
    dispatch(updateEndpoint(endpointId, data)).then(
      (result: {
        result: string;
        entities: { endpoints: Record<string, Endpoint> };
      }) => {
        if (result.entities) {
          if (onUpdate) {
            const endpointUpdated = result.entities.endpoints[result.result];
            onUpdate(
              lastMarkingIdsRef.current === null
                ? endpointUpdated
                : {
                    ...endpointUpdated,
                    asset_markings: lastMarkingIdsRef.current,
                  },
            );
          }
          handleClose();
        }
        return result;
      },
    );
  };

  // Runs (and is awaited by AssetForm) before the main endpoint update - see AssetForm's
  // `onMarkingsChange` doc for why the two can't fire concurrently. The returned promise is what
  // AssetForm awaits, so this must return the dispatch, not just fire it. Suppressed success toast
  // (false) since the endpoint update below already confirms the save to the user.
  const onMarkingsChange = (markingIds: string[]) => {
    lastMarkingIdsRef.current = markingIds;
    return dispatch(updateAssetMarkings(endpointId, { asset_markings: markingIds }, false));
  };

  const category = (endpoint?.asset_category as AssetCategory) ?? 'HOST';
  // The drawer shell renders immediately so the slide-in animation plays and the
  // underlying screen never shows a full-page loader; the fetch only swaps the
  // drawer body content.
  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Update an asset')}
    >
      {loading || !endpoint
        ? <Loader variant="inElement" />
        : (
            <AssetForm
              category={category}
              initialValues={endpoint as unknown as Partial<EndpointInput> & { asset_markings?: string[] | null }}
              editing
              onSubmit={onSubmit}
              onMarkingsChange={onMarkingsChange}
              agentless={agentless}
              handleClose={handleClose}
            />
          )}
    </Drawer>
  );
};

export default EndpointUpdate;
