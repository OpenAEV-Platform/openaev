import { Alert, Button, Paper, ToggleButtonGroup, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';

import { updateReportForExercise, updateReportInjectCommentForExercise } from '../../../../../actions/reports/report-actions';
import ExportPdfButton from '../../../../../components/ExportPdfButton';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { type Exercise, type LessonsQuestion, type Report, type ReportInput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useSimulationPermissions from '../../../../../utils/permissions/useSimulationPermissions';
import { ReportContext, type ReportContextType } from '../../../common/Context';
import ResponsePie from '../../../common/injects/ResponsePie';
import ReportComment from '../../../components/reports/ReportComment';
import ReportPopover from '../../../components/reports/ReportPopover';
import AnswersByQuestionDialog from '../../../lessons/simulations/AnswersByQuestionDialog';
import LessonsCategories from '../../../lessons/simulations/LessonsCategories';
import ExerciseDistribution from '../overview/ExerciseDistribution';
import SimulationMainInformation from '../SimulationMainInformation';
import ExerciseReportForm from './ExerciseReportForm';
import getExerciseReportPdfDocDefinition from './getExerciseReportPdfDoc';
import InjectReportResult from './InjectReportResult';
import ReportInformationType from './ReportInformationType';
import useExerciseReportData from './useExerciseReportData';

const SimulationReportPage: FunctionComponent = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const { t, tPick, fldt } = useFormatter();
  const theme = useTheme();

  const { exerciseId, reportId } = useParams() as {
    exerciseId: Exercise['exercise_id'];
    reportId: Report['report_id'];
  };
  const { loading, report, displayModule, setReloadReportDataCount, reportData } = useExerciseReportData(reportId, exerciseId);
  const [selectedQuestion, setSelectedQuestion] = useState<LessonsQuestion | null>(null);
  const selectedQuestionAnswers = selectedQuestion && selectedQuestion.lessonsquestion_id
    ? reportData.lessonsAnswers.filter(answer => answer.lessons_answer_question === selectedQuestion.lessonsquestion_id)
    : [];

  const permissions = useSimulationPermissions(exerciseId);
  const [canEditReport, setCanEditReport] = useState(permissions.canManage);
  useEffect(() => {
    setCanEditReport(permissions.canManage);
  }, [permissions.canManage]);

  // Context
  const context: ReportContextType = {
    onUpdateReport: (_reportId: Report['report_id'], data: ReportInput) => dispatch(updateReportForExercise(exerciseId, reportId, data))
      .then(() => setReloadReportDataCount((prev: number) => prev + 1)),
    renderReportForm: (onSubmitForm, onHandleCancel, _report) => {
      return (
        <ExerciseReportForm
          onSubmit={onSubmitForm}
          handleCancel={onHandleCancel}
          initialValues={report}
          editing
        />
      );
    },
  } as ReportContextType;

  const saveGlobalObservation = (comment: string) => dispatch(updateReportForExercise(
    exerciseId,
    report.report_id,
    {
      ...report,
      report_global_observation: comment,
    } as ReportInput,
  ));

  if (loading) {
    return <Loader />;
  }
  if (!report) {
    return <Alert severity="warning">{t('This report is not available')}</Alert>;
  }

  return (
    <ReportContext.Provider value={context}>
      <div style={{
        margin: 20,
        display: 'flex',
      }}
      >
        <Button
          color="primary"
          variant="outlined"
          component={Link}
          to={`/admin/simulations/${exerciseId}`}
        >
          {t('Back to administration')}
        </Button>

        <ToggleButtonGroup style={{ marginLeft: 'auto' }}>
          <ExportPdfButton
            pdfName={report.report_name}
            getPdfDocDefinition={() => getExerciseReportPdfDocDefinition({
              report,
              reportData,
              displayModule,
              tPick,
              fldt,
              t,
            })}
          />
          {permissions.canManage && <ReportPopover variant="toggle" report={report} actions={['Update']} />}
        </ToggleButtonGroup>
      </div>

      <div
        id={`reportId_${report.report_id}`}
        style={{
          display: 'flex',
          flexFlow: 'wrap',
          maxWidth: '1400px',
          margin: 'auto',
          gap: theme.spacing(2),
        }}
      >
        <div style={{
          width: '100%',
          textAlign: 'center',
          fontSize: 25,
          fontWeight: 500,
        }}
        >
          {report.report_name}
        </div>
        {displayModule(ReportInformationType.MAIN_INFORMATION)
          && (
            <div style={{ width: `calc(50% - ${theme.spacing(1)})` }}>
              <Typography variant="h4" gutterBottom>
                {t('General information')}
              </Typography>
              <SimulationMainInformation exercise={reportData.exercise} />
            </div>
          )}
        {displayModule(ReportInformationType.SCORE_DETAILS)
          && (
            <div
              style={{
                width: `calc(50% - ${theme.spacing(1)})`,
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              <Typography variant="h4" gutterBottom>
                {t('Results')}
              </Typography>
              <Paper
                variant="outlined"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  flex: 1,
                }}
              >
                { reportData.exerciseExpectationResults.length > 0 ? (
                  <ResponsePie
                    expectationResultsByTypes={reportData.exerciseExpectationResults}
                    disableChartAnimation
                  />
                ) : (
                  <div
                    id="score_details"
                    style={{ height: 1 }}
                  >
                    <p>&nbsp;</p>
                  </div>
                )}
              </Paper>
            </div>
          )}
        {displayModule(ReportInformationType.INJECT_RESULT)
          && (
            <InjectReportResult
              canEditComment={canEditReport}
              injectsComments={report?.report_injects_comments}
              injects={reportData.injects}
              style={{ width: '100%' }}
              onCommentSubmit={value => dispatch(updateReportInjectCommentForExercise(exerciseId, report.report_id, value))}
            />
          )}
        {displayModule(ReportInformationType.GLOBAL_OBSERVATION)
          && (
            <div style={{ width: '100%' }}>
              <Typography variant="h4" gutterBottom>
                {t('Global observation')}
              </Typography>

              <Paper variant="outlined" sx={{ padding: '10px 15px 10px 15px' }}>
                <ReportComment canEditComment={canEditReport} initialComment={report.report_global_observation || ''} saveComment={saveGlobalObservation} />
              </Paper>
            </div>
          )}
        {displayModule(ReportInformationType.PLAYER_SURVEYS)
          && (
            <LessonsCategories
              style={{ width: '100%' }}
              lessonsCategories={reportData.lessonsCategories}
              lessonsAnswers={reportData.lessonsAnswers}
              lessonsQuestions={reportData.lessonsQuestions}
              teamsMap={reportData.teamsMap}
              teams={reportData.teams}
              setSelectedQuestion={setSelectedQuestion}
              isReport
            />
          )}
        {displayModule(ReportInformationType.EXERCISE_DETAILS)
          && <ExerciseDistribution exerciseId={exerciseId} isReport />}
        <AnswersByQuestionDialog
          open={!!selectedQuestion}
          onClose={() => setSelectedQuestion(null)}
          question={selectedQuestion?.lessons_question_content || ''}
          answers={selectedQuestionAnswers}
          anonymized={!!reportData.exercise.exercise_lessons_anonymized}
          usersMap={reportData.usersMap}
        />
      </div>
    </ReportContext.Provider>
  );
};

export default SimulationReportPage;
