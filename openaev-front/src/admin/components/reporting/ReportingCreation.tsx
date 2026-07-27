import { type FunctionComponent, useCallback, useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import { createReporting, createReportingSchedule } from '../../../actions/reporting/reporting-actions';
import ButtonCreate from '../../../components/common/ButtonCreate';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { type Reporting, type ReportingInput, type ReportingScheduleInput } from '../../../utils/api-types';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ReportingForm from './form/ReportingForm';

/**
 * "Create report" button + wizard drawer of the reports list page. On success
 * the optional first schedule is created too, then the user lands on the new
 * report detail page.
 */
const ReportingCreation: FunctionComponent = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);

  const [open, setOpen] = useState(false);

  const onSubmit = useCallback(
    async (input: ReportingInput, schedule?: ReportingScheduleInput) => {
      try {
        const response = await createReporting(input);
        const created: Reporting | undefined = response.data;
        if (created) {
          if (schedule) {
            await createReportingSchedule(created.reporting_id, schedule);
          }
          navigate(`/admin/reporting/${created.reporting_id}`);
        }
      } finally {
        setOpen(false);
      }
    },
    [navigate],
  );

  if (!ability.can(ACTIONS.MANAGE, SUBJECTS.REPORTINGS)) {
    return null;
  }

  return (
    <>
      <ButtonCreate label={t('Create report')} onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a report')}
      >
        <ReportingForm onSubmit={onSubmit} handleClose={() => setOpen(false)} />
      </Drawer>
    </>
  );
};

export default ReportingCreation;
