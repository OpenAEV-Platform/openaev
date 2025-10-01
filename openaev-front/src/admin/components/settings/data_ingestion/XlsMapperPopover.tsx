import { type FunctionComponent, useContext, useState } from 'react';

import { deleteMapper, duplicateMapper, exportMapper } from '../../../../actions/mapper/mapper-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import DialogDuplicate from '../../../../components/common/DialogDuplicate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type RawPaginationImportMapper } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { download } from '../../../../utils/utils';
import XlsMapperUpdate from './xls_mapper/XlsMapperUpdate';

interface Props {
  mapper: RawPaginationImportMapper;
  onDuplicate?: (result: RawPaginationImportMapper) => void;
  onUpdate?: (result: RawPaginationImportMapper) => void;
  onDelete?: (result: string) => void;
  onExport?: (result: string) => void;
}

const XlsMapperPopover: FunctionComponent<Props> = ({
  mapper,
  onDuplicate,
  onUpdate,
  onDelete,
  onExport,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  // Duplication
  const [openDuplicate, setOpenDuplicate] = useState(false);
  const handleOpenDuplicate = () => setOpenDuplicate(true);
  const handleCloseDuplicate = () => setOpenDuplicate(false);
  const submitDuplicate = () => {
    duplicateMapper(mapper.import_mapper_id).then(
      (result: { data: RawPaginationImportMapper }) => {
        onDuplicate?.(result.data);
        return result;
      },
    );
    handleCloseDuplicate();
  };

  // Edition
  const [openEdit, setOpenEdit] = useState(false);

  const handleOpenEdit = () => setOpenEdit(true);
  const handleCloseEdit = () => setOpenEdit(false);

  // Deletion
  const [openDelete, setOpenDelete] = useState(false);

  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    deleteMapper(mapper.import_mapper_id);
    if (onDelete) {
      onDelete(mapper.import_mapper_id);
    }
    handleCloseDelete();
  };

  const exportMapperAction = () => {
    exportMapper({
      ids_to_export: [mapper.import_mapper_id],
      export_mapper_name: mapper.import_mapper_name,
    }).then(
      (result: {
        data: string;
        filename: string;
      }) => {
        download(JSON.stringify(result.data, null, 2), result.filename, 'application/json');
      },
    );
    if (onExport) {
      onExport(mapper.import_mapper_id);
    }
  };

  const entries: PopoverEntry[] = [
    {
      label: 'Duplicate',
      action: handleOpenDuplicate,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    },
    {
      label: 'Update',
      action: handleOpenEdit,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    },
    {
      label: 'Delete',
      action: handleOpenDelete,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    },
    {
      label: 'Export',
      action: exportMapperAction,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    },
  ];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the xls mapper')}
      >
        <XlsMapperUpdate
          xlsMapperId={mapper.import_mapper_id}
          onUpdate={onUpdate}
          handleClose={handleCloseEdit}
        />
      </Drawer>
      <DialogDuplicate
        open={openDuplicate}
        handleClose={handleCloseDuplicate}
        handleSubmit={submitDuplicate}
        text={`${t('Do you want to duplicate this XLS mapper :')} ${mapper.import_mapper_name} ?`}
      />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this XLS mapper?')}
      />
    </>
  );
};

export default XlsMapperPopover;
