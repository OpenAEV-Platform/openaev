import { type FunctionComponent, useState } from 'react';

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

  // Returns whether the update actually succeeded - AssetForm awaits this (SubmitHandler's own
  // return type, `unknown`, already permits it) to decide whether to revert a markings change that
  // committed earlier in the same submit. See AssetForm's `onSubmit` doc.
  const onSubmit = (data: EndpointInput) => {
    return dispatch(updateEndpoint(endpointId, data)).then(
      (result: {
        result: string;
        entities?: { endpoints: Record<string, Endpoint> };
      }) => {
        if (result.entities) {
          if (onUpdate) {
            const endpointUpdated = result.entities.endpoints[result.result];
            onUpdate(endpointUpdated);
          }
          handleClose();
        }
        return !!result.entities;
      },
    );
  };

  // Runs (and is awaited by AssetForm) before the main endpoint update - see AssetForm's
  // `onMarkingsChange` doc for why the two can't fire concurrently, and for why returning `false`
  // here cancels the endpoint update too. Suppressed success toast (false) since the endpoint
  // update below already confirms the save to the user; a failure still notifies (putReferential
  // always does), so the user sees why nothing happened.
  const onMarkingsChange = async (markingIds: string[]): Promise<boolean> => {
    const result = await dispatch(updateAssetMarkings(endpointId, { asset_markings: markingIds }, false));
    // Same "does it have entities" success check used for the endpoint update's own response
    // below: putReferential resolves either the normalized {entities, result} on success, or a
    // buildError(...) object on failure - it never rejects, so this is the only way to tell them
    // apart.
    return !!(result as { entities?: unknown } | undefined)?.entities;
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
