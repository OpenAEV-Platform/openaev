import { type FunctionComponent, useCallback, useState } from 'react';

import { deleteReporting, updateReporting } from '../../../actions/reporting/reporting-actions';
import ButtonPopover from '../../../components/common/ButtonPopover';
import DialogDelete from '../../../components/common/DialogDelete';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { type Reporting, type ReportingInput } from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ReportingForm from './form/ReportingForm';

interface Props {
  reporting: Reporting;
  onUpdate?: (result: Reporting) => void;
  onDelete?: (reportingId: string) => void;
  inList?: boolean;
}

/**
 * Kebab menu of a report (card, list row and detail header): update opens the
 * wizard in edit mode, delete asks for confirmation.
 */
const ReportingPopover: FunctionComponent<Props> = ({ reporting, onUpdate, onDelete, inList = false }) => {
  const { t } = useFormatter();
  const ability = useAbility();

  const [modal, setModal] = useState<'edit' | 'delete' | null>(null);

  const onSubmitEdit = useCallback(
    async (input: ReportingInput) => {
      try {
        const response = await updateReporting(reporting.reporting_id, input);
        if (response.data) {
          onUpdate?.(response.data);
        }
      } finally {
        setModal(null);
      }
    },
    [reporting.reporting_id, onUpdate],
  );

  const submitDelete = useCallback(async () => {
    try {
      await deleteReporting(reporting.reporting_id);
      onDelete?.(reporting.reporting_id);
    } finally {
      setModal(null);
    }
  }, [reporting.reporting_id, onDelete]);

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.REPORTINGS);
  const entries = [
    {
      label: 'Update',
      action: () => setModal('edit'),
      userRight: canManage,
    },
    {
      label: 'Delete',
      action: () => setModal('delete'),
      userRight: canManage,
    },
  ];

  return (
    <>
      <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />
      <Drawer
        open={modal === 'edit'}
        handleClose={() => setModal(null)}
        title={t('Update the report')}
      >
        <ReportingForm
          onSubmit={onSubmitEdit}
          handleClose={() => setModal(null)}
          initialValues={reporting}
          editing
        />
      </Drawer>
      <DialogDelete
        open={modal === 'delete'}
        handleClose={() => setModal(null)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this report?')}
      />
    </>
  );
};

export default ReportingPopover;
