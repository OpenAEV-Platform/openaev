import { type FunctionComponent, useEffect, useState } from 'react';

import { updateAssetsOnAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import { fetchAiTargetById, updateAiTarget, updateAiTargetMarkings } from '../../../../actions/assets/aiTarget-actions';
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
// asset_markings isn't part of AiTargetInput (it's written through its own endpoint, not this
// form's submit - see AiTargetForm), but it still needs to flow into the picker's initial value.
const AI_TARGET_INPUT_KEYS: (keyof AiTargetInput | 'asset_markings')[] = [
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
  'asset_markings',
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
  const [aiTargetValues, setAiTargetValues] = useState<(AiTargetInput & { asset_markings?: string[] | null }) | null>(null);
  useEffect(() => {
    if (edition && isAiTarget && !aiTargetValues) {
      fetchAiTargetById(endpoint.asset_id).then((response: { data: Record<string, unknown> }) => {
        const asset = response.data;
        const values = Object.fromEntries(
          AI_TARGET_INPUT_KEYS.map(key => [key, asset[key]]),
        ) as unknown as AiTargetInput & { asset_markings?: string[] | null };
        setAiTargetValues(values);
      });
    }
    if (!edition) {
      setAiTargetValues(null);
    }
  }, [edition, isAiTarget, endpoint.asset_id, aiTargetValues]);

  // Returns whether the update actually succeeded - AiTargetForm awaits this (SubmitHandler's own
  // return type, `unknown`, already permits it) to decide whether to revert a markings change that
  // committed earlier in the same submit. See AssetForm's `onSubmit` doc for the full rationale.
  //
  // handleCloseEdit is now conditional on success (it previously ran unconditionally, closing the
  // drawer even on a failed save) - closing on failure would both hide the error from the user and
  // contradict the "form stays open on a half-applied save" guarantee the revert logic relies on.
  const submitEditAiTarget = (data: AiTargetInput) => {
    return dispatch(updateAiTarget(endpoint.asset_id, data)).then(
      (result: {
        result: string;
        entities?: { aitargets: Record<string, EndpointOverviewOutput> };
      }) => {
        if (result.entities) {
          if (onUpdate) {
            onUpdate(result.entities.aitargets[result.result]);
          }
          handleCloseEdit();
        }
        return !!result.entities;
      },
    );
  };

  // Awaited by AiTargetForm before submitEditAiTarget runs - see AssetForm's `onMarkingsChange`
  // doc for why the two can't fire concurrently. Suppressed success toast (false) since the
  // asset update above already confirms the save; updateAiTarget's own response already carries
  // asset_markings fresh from the DB (Asset is returned as-is, unlike EndpointOverviewOutput), so
  // there's nothing to patch in afterwards the way EndpointUpdate has to.
  const onMarkingsChangeAiTarget = async (markingIds: string[]): Promise<boolean> => {
    const result = await dispatch(updateAiTargetMarkings(endpoint.asset_id, { asset_markings: markingIds }, false));
    // Same "does it have entities" success check used above: putReferential resolves either the
    // normalized {entities, result} on success, or a buildError(...) object on failure - it never
    // rejects, so this is the only way to tell them apart.
    return !!(result as { entities?: unknown } | undefined)?.entities;
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
                  onMarkingsChange={onMarkingsChangeAiTarget}
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
