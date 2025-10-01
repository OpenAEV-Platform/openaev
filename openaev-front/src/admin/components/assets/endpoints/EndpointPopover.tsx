import { type FunctionComponent, useContext, useState } from 'react';

import { updateAssetsOnAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import { deleteEndpoint } from '../../../../actions/assets/endpoint-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../components/i18n';
import { type EndpointOutput, type EndpointOverviewOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import EndpointUpdate from './EndpointUpdate';

export interface EndpointPopoverProps {
  inline?: boolean;
  endpoint: EndpointOutput & EndpointOverviewOutput;
  assetGroupId?: string;
  assetGroupEndpointIds?: string[];
  removeFromContextLabel?: string | null;
  onRemoveFromContext?: (assetId: string) => void;
  onRemoveEndpointFromAssetGroup?: (asset: EndpointOutput) => void;
  onUpdate?: (result: EndpointOverviewOutput) => void;
  onDelete?: (result: string) => void;
  disabled?: boolean;
  agentless?: boolean;
}

const EndpointPopover: FunctionComponent<EndpointPopoverProps> = ({
  inline,
  endpoint,
  assetGroupId,
  assetGroupEndpointIds,
  removeFromContextLabel = null,
  onRemoveFromContext,
  onRemoveEndpointFromAssetGroup,
  onUpdate,
  onDelete,
  disabled = false,
  agentless,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const [edition, setEdition] = useState(false);
  const handleOpenEdit = () => setEdition(true);
  const handleCloseEdit = () => setEdition(false);

  // Removal
  const [removalFromAssetGroup, setRemovalFromAssetGroup] = useState(false);

  const handleRemoveFromAssetGroup = () => {
    setRemovalFromAssetGroup(true);
  };
  const submitRemoveFromAssetGroup = () => {
    if (assetGroupId) {
      dispatch(
        updateAssetsOnAssetGroup(assetGroupId, { asset_group_assets: assetGroupEndpointIds?.filter(id => id !== endpoint.asset_id) }),
      ).then(() => {
        if (onRemoveEndpointFromAssetGroup) {
          onRemoveEndpointFromAssetGroup(endpoint);
        }
        setRemovalFromAssetGroup(false);
      });
    }
  };

  // Deletion
  const [deletion, setDeletion] = useState(false);

  const handleDelete = () => {
    setDeletion(true);
  };
  const submitDelete = () => {
    dispatch(deleteEndpoint(endpoint.asset_id)).then(
      () => {
        if (onDelete) {
          onDelete(endpoint.asset_id);
        }
      },
    );
    setDeletion(false);
  };

  // Button Popover
  const entries = [];
  if (onUpdate) entries.push({
    label: 'Update',
    action: () => handleOpenEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.ASSETS),
  });
  if (onRemoveFromContext && removeFromContextLabel) entries.push({
    label: removeFromContextLabel,
    action: () => onRemoveFromContext(endpoint.asset_id),
    userRight: true,
  });
  if ((assetGroupId && endpoint.is_static)) entries.push({
    label: 'Remove from the asset group',
    action: () => handleRemoveFromAssetGroup(),
    userRight: true,
  });
  if (onDelete) entries.push({
    label: 'Delete',
    action: () => handleDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.ASSETS),
  });

  return entries.length > 0 && (
    <>
      <ButtonPopover disabled={disabled} entries={entries} variant={inline ? 'icon' : 'toggle'} />
      {edition && onUpdate && (
        <EndpointUpdate
          open
          handleClose={handleCloseEdit}
          endpointId={endpoint.asset_id}
          agentless={agentless}
          onUpdate={result => onUpdate(result as EndpointOverviewOutput)}
        />
      )}
      <DialogDelete
        open={removalFromAssetGroup}
        handleClose={() => setRemovalFromAssetGroup(false)}
        handleSubmit={submitRemoveFromAssetGroup}
        text={t('Do you want to remove the endpoint from the asset group?')}
      />
      <DialogDelete
        open={deletion}
        handleClose={() => setDeletion(false)}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete the endpoint:')} ${endpoint.asset_name}?`}
      />
    </>
  );
};

export default EndpointPopover;
