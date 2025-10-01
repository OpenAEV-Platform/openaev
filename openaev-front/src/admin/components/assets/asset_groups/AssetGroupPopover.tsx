import { Drawer as MuiDrawer } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { deleteAssetGroup, updateAssetGroup, updateAssetsOnAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import Dialog from '../../../../components/common/dialog/Dialog';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { emptyFilterGroup } from '../../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../../components/i18n';
import { type AssetGroup, type AssetGroupInput, type AssetGroupOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import EndpointsDialogAdding from '../endpoints/EndpointsDialogAdding';
import AssetGroupForm from './AssetGroupForm';
import AssetGroupManagement from './AssetGroupManagement';

const useStyles = makeStyles()(() => ({
  drawerPaper: {
    minHeight: '100vh',
    width: '50%',
    padding: 0,
  },
}));

export interface AssetGroupPopoverProps {
  inline?: boolean;
  assetGroup: AssetGroup | AssetGroupOutput;
  onRemoveAssetGroupFromList?: (assetGroupId: string) => void;
  onRemoveEndpointFromAssetGroup?: (assetId: string) => void;
  removeAssetGroupFromListMessage?: string;
  openEditOnInit?: boolean;
  onUpdate?: (result: AssetGroup) => void;
  onDelete?: (result: string) => void;
  disabled?: boolean;
  actions?: 'update' | 'delete' | 'manage-asset' | 'remove'[];
}

const AssetGroupPopover: FunctionComponent<AssetGroupPopoverProps> = ({
  inline,
  assetGroup,
  onRemoveAssetGroupFromList,
  onRemoveEndpointFromAssetGroup,
  removeAssetGroupFromListMessage = 'Remove from the inject',
  openEditOnInit = false,
  onUpdate,
  onDelete,
  disabled = false,
  actions = ['update', 'delete', 'manage-asset', 'remove'],
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const initialValues = (({
    asset_group_name,
    asset_group_description,
    asset_group_tags,
    asset_group_dynamic_filter,
  }) => ({
    asset_group_name: asset_group_name ?? '',
    asset_group_description: asset_group_description ?? '',
    asset_group_tags: asset_group_tags ?? [],
    asset_group_dynamic_filter: asset_group_dynamic_filter ?? emptyFilterGroup,
  }))(assetGroup);

  // Edition
  const [edition, setEdition] = useState(openEditOnInit);

  const handleEdit = () => {
    setEdition(true);
  };
  const submitEdit = (data: AssetGroupInput) => {
    dispatch(updateAssetGroup(assetGroup.asset_group_id, data)).then(
      (result: {
        result: string;
        entities: { asset_groups: Record<string, AssetGroup> };
      }) => {
        if (result.entities) {
          if (onUpdate) {
            const updated = result.entities.asset_groups[result.result];
            onUpdate(updated);
          }
        }
        setEdition(false);
        return result;
      },
    );
  };

  // Manage assets
  const [selected, setSelected] = useState<boolean>(false);

  const handleManage = () => {
    setSelected(true);
  };
  const sumitManage = (endpointIds: string[]) => {
    return dispatch(updateAssetsOnAssetGroup(assetGroup.asset_group_id, { asset_group_assets: endpointIds }));
  };

  // Deletion
  const [deletion, setDeletion] = useState(false);

  const handleDelete = () => {
    setDeletion(true);
  };
  const submitDelete = () => {
    dispatch(deleteAssetGroup(assetGroup.asset_group_id)).then(
      () => {
        if (onDelete) {
          onDelete(assetGroup.asset_group_id);
        }
        setDeletion(false);
      },
    );
  };

  // Button Popover
  const entries = [];
  if (actions.includes('update')) entries.push({
    label: 'Update',
    action: () => handleEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.ASSETS),
  });
  if (actions.includes('manage-asset')) entries.push({
    label: 'Manage assets',
    action: () => handleManage(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.ASSETS),
  });
  if (actions.includes('remove') && onRemoveAssetGroupFromList) entries.push({
    label: removeAssetGroupFromListMessage,
    action: () => onRemoveAssetGroupFromList(assetGroup.asset_group_id),
    userRight: true,
  });
  if (actions.includes('delete')) entries.push({
    label: 'Delete',
    action: () => handleDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.ASSETS),
  });

  return (
    <>
      <ButtonPopover disabled={disabled} entries={entries} variant="icon" />

      <DialogDelete
        open={deletion}
        handleClose={() => setDeletion(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete the asset group?')}
      />

      {inline ? (
        <Dialog
          open={edition}
          handleClose={() => setEdition(false)}
          title={t('Update the asset group')}
        >
          <AssetGroupForm
            initialValues={initialValues}
            editing={true}
            onSubmit={submitEdit}
            handleClose={() => setEdition(false)}
          />
        </Dialog>
      ) : (
        <Drawer
          open={edition}
          handleClose={() => setEdition(false)}
          title={t('Update the asset group')}
        >
          <AssetGroupForm
            initialValues={initialValues}
            editing={true}
            onSubmit={submitEdit}
            handleClose={() => setEdition(false)}
          />
        </Drawer>
      )}

      {inline ? (
        <>
          {selected !== undefined && (
            <EndpointsDialogAdding
              initialState={assetGroup.asset_group_assets ?? []}
              open={selected}
              onClose={() => setSelected(false)}
              onSubmit={sumitManage}
              title={t('Add assets in this asset group')}
            />
          )}
        </>
      ) : (
        <MuiDrawer
          open={selected}
          keepMounted={false}
          anchor="right"
          sx={{ zIndex: 1202 }}
          classes={{ paper: classes.drawerPaper }}
          onClose={() => setSelected(false)}
          elevation={1}
        >
          {selected && (
            <AssetGroupManagement
              assetGroupId={assetGroup.asset_group_id}
              handleClose={() => setSelected(false)}
              onUpdate={onUpdate}
              onRemoveEndpointFromAssetGroup={onRemoveEndpointFromAssetGroup}
            />
          )}
        </MuiDrawer>
      )}
    </>
  );
};

export default AssetGroupPopover;
