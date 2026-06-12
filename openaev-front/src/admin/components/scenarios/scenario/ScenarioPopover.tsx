import { CheckOutlined, MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, IconButton, ListItemIcon, ListItemText, Menu, MenuItem, TextField, ToggleButton } from '@mui/material';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { deleteScenario, duplicateScenario, exportScenarioUri } from '../../../../actions/scenarios/scenario-actions';
import DialogDelete from '../../../../components/common/DialogDelete';
import DialogDuplicate from '../../../../components/common/DialogDuplicate';
import ExportOptionsDialog from '../../../../components/common/export/ExportOptionsDialog';
import { useFormatter } from '../../../../components/i18n';
import { type Scenario } from '../../../../utils/api-types';
import { simpleCall, simplePutCall, simplePostCall } from '../../../../utils/Action';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { type ScenarioVariant } from './ScenarioVariantContext';
import ScenarioUpdate from './ScenarioUpdate';

type ScenarioActionType = 'Duplicate' | 'Update' | 'Delete' | 'Export';

interface Props {
  scenario: Scenario;
  actions: ScenarioActionType[];
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const ScenarioPopover: FunctionComponent<Props> = ({
  scenario,
  actions = [],
  onDelete,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { canManage, canDelete } = useScenarioPermissions(scenario.scenario_id);
  const ability = useContext(AbilityContext);

  // Variants — loaded directly (not via context to avoid lifecycle issues)
  const [variants, setVariants] = useState<ScenarioVariant[]>([]);

  const loadVariants = async () => {
    if (inList) return; // only needed on detail page
    try {
      const result = await simpleCall(`/api/scenarios/${scenario.scenario_id}/variants`);
      // eslint-disable-next-line no-console
      console.log('[ScenarioPopover] variants result:', result?.data);
      if (result?.data && Array.isArray(result.data)) {
        setVariants(result.data as ScenarioVariant[]);
      } else {
        // eslint-disable-next-line no-console
        console.warn('[ScenarioPopover] variants data is not array:', result);
      }
    } catch (err) {
      // eslint-disable-next-line no-console
      console.error('[ScenarioPopover] variants fetch error:', err);
    }
  };

  useEffect(() => {
    loadVariants();
  }, [scenario.scenario_id, inList]);

  const handleSwitchVariant = async (variantId: string) => {
    try {
      await simplePutCall(`/api/scenarios/${scenario.scenario_id}/variants/${variantId}/activate`, {});
      await loadVariants();
      // reload page so variant config takes effect everywhere
      window.location.reload();
    } catch {
      // ignore
    }
  };

  const handleCreateVariant = async (name: string) => {
    try {
      await simplePostCall(`/api/scenarios/${scenario.scenario_id}/variants`, { variant_name: name });
      await loadVariants();
    } catch {
      // ignore
    }
  };

  // Menu anchor
  const [anchorEl, setAnchorEl] = useState<Element | null>(null);

  // Duplicate
  const [duplicate, setDuplicate] = useState(false);
  const handleOpenDuplicate = () => setDuplicate(true);
  const handleCloseDuplicate = () => setDuplicate(false);
  const submitDuplicate = () => {
    dispatch(duplicateScenario(scenario.scenario_id)).then((result: {
      result: string;
      entities: { scenarios: Record<string, Scenario> };
    }) => {
      handleCloseDuplicate();
      navigate(`/admin/scenarios/${result.result}`);
    });
  };

  // Edition
  const [edition, setEdition] = useState(false);
  const handleOpenEdit = () => setEdition(true);
  const handleCloseEdit = () => setEdition(false);

  // Deletion
  const [deletion, setDeletion] = useState(false);
  const handleOpenDelete = () => setDeletion(true);
  const handleCloseDelete = () => setDeletion(false);
  const submitDelete = () => {
    dispatch(deleteScenario(scenario.scenario_id)).then(() => {
      handleCloseDelete();
      if (onDelete) onDelete(scenario.scenario_id);
    });
  };

  // Export
  const [exportation, setExportation] = useState(false);
  const handleOpenExport = () => setExportation(true);
  const handleCloseExport = () => setExportation(false);
  const submitExport = (exportPlayers: boolean, exportTeams: boolean, exportVariableValues: boolean) => {
    const link = document.createElement('a');
    link.href = exportScenarioUri(scenario.scenario_id, exportTeams, exportPlayers, exportVariableValues);
    link.click();
  };

  // New variant dialog
  const [openCreateVariant, setOpenCreateVariant] = useState(false);
  const [newVariantName, setNewVariantName] = useState('');

  return (
    <>
      {inList
        ? (
            <IconButton
              size="large"
              color="primary"
              onClick={(e) => { e.stopPropagation(); setAnchorEl(e.currentTarget); }}
            >
              <MoreVert fontSize="medium" color="primary" />
            </IconButton>
          )
        : (
            <ToggleButton
              value="popover"
              size="small"
              color="primary"
              onClick={(e) => { e.stopPropagation(); setAnchorEl(e.currentTarget); }}
            >
              <MoreVert fontSize="small" color="primary" />
            </ToggleButton>
          )}

      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
        {actions.includes('Update') && canManage && (
          <MenuItem onClick={() => { handleOpenEdit(); setAnchorEl(null); }}>{t('Update')}</MenuItem>
        )}
        {actions.includes('Duplicate') && ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT) && (
          <MenuItem onClick={() => { handleOpenDuplicate(); setAnchorEl(null); }}>{t('Duplicate')}</MenuItem>
        )}
        {actions.includes('Export') && (
          <MenuItem onClick={() => { handleOpenExport(); setAnchorEl(null); }}>{t('Export')}</MenuItem>
        )}
        {actions.includes('Delete') && canDelete && (
          <MenuItem onClick={() => { handleOpenDelete(); setAnchorEl(null); }}>{t('Delete')}</MenuItem>
        )}

        {/* Variants section — only on detail page (not in list) */}
        {!inList && variants.length > 0 && <Divider />}
        {!inList && variants.map(v => (
          <MenuItem
            key={v.variant_id}
            selected={v.variant_is_active}
            onClick={async () => {
              setAnchorEl(null);
              if (!v.variant_is_active) await handleSwitchVariant(v.variant_id);
            }}
          >
            {v.variant_is_active
              ? (
                  <>
                    <ListItemIcon><CheckOutlined fontSize="small" color="primary" /></ListItemIcon>
                    <ListItemText>{v.variant_name}</ListItemText>
                  </>
                )
              : (
                  <ListItemText inset>{v.variant_name}</ListItemText>
                )}
          </MenuItem>
        ))}
        {!inList && (
          <MenuItem onClick={() => { setAnchorEl(null); setNewVariantName(''); setOpenCreateVariant(true); }}>
            <ListItemText inset>{t('New variant...')}</ListItemText>
          </MenuItem>
        )}
      </Menu>

      {actions.includes(('Update'))
        && (
          <ScenarioUpdate
            scenario={scenario}
            open={edition}
            handleClose={handleCloseEdit}
          />
        )}
      {actions.includes('Duplicate')
        && (
          <DialogDuplicate
            open={duplicate}
            handleClose={handleCloseDuplicate}
            handleSubmit={submitDuplicate}
            text={`${t('Do you want to duplicate this scenario:')} ${scenario.scenario_name} ?`}
          />
        )}
      {actions.includes('Export')
        && (
          <ExportOptionsDialog
            title={t('Export the scenario')}
            open={exportation}
            onCancel={handleCloseExport}
            onClose={handleCloseExport}
            onSubmit={submitExport}
          />
        )}
      {actions.includes('Delete')
        && (
          <DialogDelete
            open={deletion}
            handleClose={handleCloseDelete}
            handleSubmit={submitDelete}
            text={`${t('Do you want to delete this scenario:')} ${scenario.scenario_name} ?`}
          />
        )}

      {/* Create variant dialog */}
      <Dialog open={openCreateVariant} onClose={() => setOpenCreateVariant(false)} maxWidth="xs" fullWidth PaperProps={{ elevation: 1 }}>
        <DialogTitle>{t('Create variant')}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus fullWidth size="small"
            label={t('Variant name')}
            value={newVariantName}
            onChange={e => setNewVariantName(e.target.value)}
            onKeyDown={async (e) => {
              if (e.key === 'Enter' && newVariantName.trim()) {
                await handleCreateVariant(newVariantName.trim());
                setOpenCreateVariant(false);
              }
            }}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenCreateVariant(false)}>{t('Cancel')}</Button>
          <Button color="secondary" disabled={!newVariantName.trim()} onClick={async () => { await handleCreateVariant(newVariantName.trim()); setOpenCreateVariant(false); }}>
            {t('Create')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ScenarioPopover;
