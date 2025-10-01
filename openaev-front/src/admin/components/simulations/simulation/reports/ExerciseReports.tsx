import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import { addReportForExercise, deleteReportForExercise, fetchReportsForExercise, updateReportForExercise } from '../../../../../actions/reports/report-actions';
import { type ReportsHelper } from '../../../../../actions/reports/report-helper';
import Dialog from '../../../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Report, type ReportInput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, ReportContext, type ReportContextType } from '../../../common/Context';
import Reports from '../../../components/reports/Reports';
import ExerciseReportForm from './ExerciseReportForm';

interface ReportListProps {
  exerciseId: string;
  exerciseName: string;
}

const ExerciseReports: FunctionComponent<ReportListProps> = ({ exerciseId, exerciseName }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const navigateToReportPage = (reportId: string) => navigate(`/reports/${reportId}/exercise/${exerciseId}`);

  const [openCreate, setOpenCreate] = useState(false);
  const handleOpenCreate = () => setOpenCreate(true);
  const handleCloseCreate = () => setOpenCreate(false);
  const onCreateReportSubmit = (data: ReportInput) => dispatch(addReportForExercise(exerciseId, data)).finally(() => handleCloseCreate());

  // Fetching data
  const { reports } = useHelper((helper: ReportsHelper) => ({ reports: helper.getExerciseReports(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchReportsForExercise(exerciseId));
  });

  // Context
  const { permissions } = useContext(PermissionsContext);
  const context: ReportContextType = {
    onDeleteReport: (report: Report) => dispatch(deleteReportForExercise(exerciseId, report.report_id)),
    onUpdateReport: (reportId: Report['report_id'], data: ReportInput) => dispatch(updateReportForExercise(exerciseId, reportId, data)),
    renderReportForm: (onSubmitForm, onHandleCancel, report) => {
      return (
        <ExerciseReportForm
          onSubmit={onSubmitForm}
          handleCancel={onHandleCancel}
          initialValues={report}
          editing
        />
      );
    },
  };

  return (
    <ReportContext.Provider value={context}>
      <Reports reports={reports} navigateToReportPage={navigateToReportPage} />
      {permissions.canManage && (
        <>
          <Fab
            onClick={handleOpenCreate}
            color="primary"
            aria-label="Add"
            sx={{
              position: 'fixed',
              bottom: '30px',
              right: '30px',
            }}
          >
            <Add />
          </Fab>
          <Dialog
            title={t('Create a new report')}
            open={openCreate}
            handleClose={handleCloseCreate}
          >
            <ExerciseReportForm
              onSubmit={onCreateReportSubmit}
              handleCancel={handleCloseCreate}
              initialValues={{ report_name: exerciseName } as Report}
            />
          </Dialog>
        </>
      )}
    </ReportContext.Provider>
  );
};

export default ExerciseReports;
