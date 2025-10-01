import * as R from 'ramda';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';

import { deleteCve, fetchCve, updateCve } from '../../../../actions/cve-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type CveOutput, type CveSimple, type CveUpdateInput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CveForm from './CveForm';

interface Props {
  onDelete?: (result: string) => void;
  onUpdate?: (result: CveSimple) => void;
  cve: CveSimple;
}

const CvePopover: FunctionComponent<Props> = ({ onDelete, onUpdate, cve }) => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  const [fullCve, setFullCve] = useState<CveOutput | null>(null);
  const [loading, setLoading] = useState(false);

  const [openEdit, setOpenEdit] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

  const handleOpenEdit = () => setOpenEdit(true);
  const handleCloseEdit = () => {
    setOpenEdit(false);
    setFullCve(null);
  };

  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);

  const handleDelete = () => {
    deleteCve(cve.cve_id)
      .then(() => {
        onDelete?.(cve.cve_id);
      })
      .catch(() => {
        MESSAGING$.notifyError(t('Failed to delete vulnerability.'));
      })
      .finally(() => {
        handleCloseDelete();
      });
  };

  const handleSubmitEdit = (data: CveUpdateInput) => {
    updateCve(cve.cve_id, data)
      .then((response) => {
        onUpdate?.(response.data);
      })
      .catch(() => {
        MESSAGING$.notifyError(t('Failed to update vulnerability.'));
      })
      .finally(() => {
        handleCloseEdit();
      });
  };

  useEffect(() => {
    if (!openEdit || !cve?.cve_id) return;

    setLoading(true);
    fetchCve(cve.cve_id)
      .then(res => setFullCve(res.data))
      .catch(() => setFullCve(null))
      .finally(() => setLoading(false));
  }, [openEdit, cve?.cve_id]);

  const entries: PopoverEntry[] = [
    {
      label: t('Update'),
      action: handleOpenEdit,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    },
    {
      label: t('Delete'),
      action: handleOpenDelete,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    },
  ];

  const initialValues = fullCve
    ? R.pick([
        'cve_external_id',
        'cve_cvss_v31',
        'cve_description',
        'cve_source_identifier',
        'cve_published',
        'cve_vuln_status',
        'cve_cisa_action_due',
        'cve_cisa_exploit_add',
        'cve_cisa_required_action',
        'cve_cisa_vulnerability_name',
        'cve_cwes',
        'cve_reference_urls',
        'cve_remediation',
      ], fullCve)
    : {};

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />

      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={handleDelete}
        text={`${t('Do you want to delete this vulnerability:')} ${cve.cve_external_id}?`}
      />

      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the vulnerability')}
      >
        {loading || !fullCve ? (
          <Loader />
        ) : (
          <CveForm
            editing
            initialValues={initialValues}
            onSubmit={handleSubmitEdit}
            handleClose={handleCloseEdit}
          />
        )}
      </Drawer>
    </>
  );
};

export default CvePopover;
