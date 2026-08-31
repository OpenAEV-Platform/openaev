import { type FunctionComponent, useState } from 'react';
import { useNavigate } from 'react-router';

import { deleteScenario, duplicateScenario, exportScenarioUri } from '../../../../actions/scenarios/scenario-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import DialogDuplicate from '../../../../components/common/DialogDuplicate';
import ExportOptionsDialog from '../../../../components/common/export/ExportOptionsDialog';
import { useFormatter } from '../../../../components/i18n';
import { type Scenario } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import ScenarioUpdate from './ScenarioUpdate';

type ScenarioActionType = 'Duplicate' | 'Update' | 'Delete' | 'Export';

interface Props {
  scenario: Scenario;
  actions: ScenarioActionType[];
  onDelete?: (result: string) => void;
  inList?: boolean;
  /** Extra entries prepended into the same kebab (setup actions from the hero),
   *  so the header exposes a single overflow menu instead of a row of icons. */
  leadingEntries?: PopoverEntry[];
  /** Disable the Delete entry (e.g. an autonomous scenario whose run is still active): the entry
   *  stays visible but greyed out with an explanatory tooltip. */
  deleteDisabled?: boolean;
  deleteDisabledMessage?: string;
}

const ScenarioPopover: FunctionComponent<Props> = ({
  scenario,
  actions = [],
  onDelete,
  inList = false,
  leadingEntries = [],
  deleteDisabled = false,
  deleteDisabledMessage,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { canManage, canDelete } = useScenarioPermissions(scenario.scenario_id);
  const ability = useAbility();

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
  const submitExport = (exportPlayers: boolean, exportTeams: boolean, exportVariableValues: boolean, exportScopeDefinition: boolean) => {
    const link = document.createElement('a');
    link.href = exportScenarioUri(scenario.scenario_id, exportTeams, exportPlayers, exportVariableValues, exportScopeDefinition);
    link.click();
    handleCloseExport();
  };

  // Button Popover. Setup actions (leadingEntries) come first, then the
  // lifecycle CRUD actions - the first CRUD entry draws a divider so the two
  // groups read as distinct sections in the single overflow menu.
  const entries: PopoverEntry[] = [...leadingEntries];
  const crudStartIndex = entries.length;
  if (actions.includes('Update')) entries.push({
    label: 'Update',
    action: () => handleOpenEdit(),
    userRight: canManage,
  });
  if (actions.includes('Duplicate')) entries.push({
    label: 'Duplicate',
    action: () => handleOpenDuplicate(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT),
  });
  if (actions.includes('Export')) entries.push({
    label: 'Export',
    action: () => handleOpenExport(),
    userRight: true,
  });
  if (actions.includes('Delete')) entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: canDelete,
    disabled: deleteDisabled,
    disabledMessage: deleteDisabledMessage,
  });
  // Separate the setup group from the CRUD group when both are present. The
  // divider goes on the first CRUD entry the user can actually see (hidden
  // entries are filtered out by ButtonPopover).
  const firstVisibleCrudIndex = entries.findIndex((entry, index) => index >= crudStartIndex && entry.userRight);
  if (crudStartIndex > 0 && firstVisibleCrudIndex !== -1) {
    entries[firstVisibleCrudIndex] = {
      ...entries[firstVisibleCrudIndex],
      dividerBefore: true,
    };
  }

  return (
    <>
      {(actions.length > 0 || leadingEntries.length > 0) && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
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
            isChaining={!!(scenario as unknown as Record<string, unknown>).scenario_workflow_id}
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
    </>
  );
};

export default ScenarioPopover;
