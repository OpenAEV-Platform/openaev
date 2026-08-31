import { type FunctionComponent, useEffect, useState } from 'react';

import { updateAssetsOnAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import { fetchAiTargetById, updateAiTarget } from '../../../../actions/assets/aiTarget-actions';
import { deleteAsset } from '../../../../actions/assets/endpoint-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type AiTargetInput, type AssetOutput, type EndpointOverviewOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import AiTargetForm from '../ai_targets/AiTargetForm';
import EndpointUpdate from './EndpointUpdate';

export interface AssetPopoverProps {
  inline?: boolean;
  // A popover row is a generic asset: the inventory and asset-group drawers hold any asset type
  // (endpoints, AI targets, identities, cloud / web / network / generic). Edit and delete are
  // dispatched by asset type; contextual removal (asset group / inject) is gated by its own props.
  endpoint: AssetOutput & Partial<EndpointOverviewOutput>;
  assetGroupId?: string;
  assetGroupEndpointIds?: string[];
  removeFromContextLabel?: string | null;
  onRemoveFromContext?: (assetId: string) => void;
  onRemoveEndpointFromAssetGroup?: (asset: AssetOutput) => void;
  onUpdate?: (result: EndpointOverviewOutput) => void;
  onDelete?: (result: string) => void;
  disabled?: boolean;
  agentless?: boolean;
}

// Fields prefilled into the AI target edit form; the inventory row only carries the shared asset
// fields, so the full connection config is fetched from /api/ai_targets/{id} on edit.
const AI_TARGET_INPUT_KEYS: (keyof AiTargetInput)[] = [
  'asset_name',
  'ai_target_provider',
  'ai_target_modality',
  'ai_target_endpoint',
  'ai_target_model',
  'ai_target_system_prompt',
  'ai_target_token',
  'ai_target_configuration',
  'asset_criticality',
  'asset_description',
  'asset_tags',
];

const AssetPopover: FunctionComponent<AssetPopoverProps> = ({
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
  const ability = useAbility();

  const isAiTarget = endpoint.asset_category === 'AI_TARGET';

  const [edition, setEdition] = useState(false);
  const handleOpenEdit = () => setEdition(true);
  const handleCloseEdit = () => setEdition(false);

  // AI target edit: fetch the full connection config lazily when the drawer opens.
  const [aiTargetValues, setAiTargetValues] = useState<AiTargetInput | null>(null);
  useEffect(() => {
    if (edition && isAiTarget && !aiTargetValues) {
      fetchAiTargetById(endpoint.asset_id).then((response: { data: Record<string, unknown> }) => {
        const asset = response.data;
        const values = Object.fromEntries(
          AI_TARGET_INPUT_KEYS.map(key => [key, asset[key]]),
        ) as unknown as AiTargetInput;
        setAiTargetValues(values);
      });
    }
    if (!edition) {
      setAiTargetValues(null);
    }
  }, [edition, isAiTarget, endpoint.asset_id, aiTargetValues]);

  const submitEditAiTarget = (data: AiTargetInput) => {
    dispatch(updateAiTarget(endpoint.asset_id, data)).then(
      (result: {
        result: string;
        entities?: { aitargets: Record<string, EndpointOverviewOutput> };
      }) => {
        if (result.entities && onUpdate) {
          onUpdate(result.entities.aitargets[result.result]);
        }
        handleCloseEdit();
        return result;
      },
    );
  };

  // Removal from an asset group (contextual, not a deletion)
  const [removalFromAssetGroup, setRemovalFromAssetGroup] = useState(false);
  const handleRemoveFromAssetGroup = () => setRemovalFromAssetGroup(true);
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

  // Deletion (generic: works for any asset type)
  const [deletion, setDeletion] = useState(false);
  const handleDelete = () => setDeletion(true);
  const submitDelete = () => {
    deleteAsset(endpoint.asset_id).then(() => {
      if (onDelete) {
        onDelete(endpoint.asset_id);
      }
    });
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
      {edition && onUpdate && isAiTarget && (
        <Drawer open handleClose={handleCloseEdit} title={t('Update the AI target')}>
          {aiTargetValues
            ? (
                <AiTargetForm
                  initialValues={aiTargetValues}
                  editing
                  onSubmit={submitEditAiTarget}
                  handleClose={handleCloseEdit}
                />
              )
            : <Loader variant="inElement" />}
        </Drawer>
      )}
      {edition && onUpdate && !isAiTarget && (
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
        text={t('Do you want to remove the asset from the asset group?')}
      />
      <DialogDelete
        open={deletion}
        handleClose={() => setDeletion(false)}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete the asset:')} ${endpoint.asset_name}?`}
      />
    </>
  );
};

export default AssetPopover;
