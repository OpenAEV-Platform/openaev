import { Paper } from '@filigran/design-system';
import { BallotOutlined, ContactMailOutlined, ContentPasteGoOutlined, DeleteSweepOutlined, SendOutlined, SpeakerNotesOutlined, SportsScoreOutlined, VisibilityOutlined } from '@mui/icons-material';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Switch,
  Typography,
  useTheme,
} from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';

import { fetchLessonsTemplates } from '../../../../actions/Lessons';
import { SECTION_LABEL_SX } from '../../../../components/common/detail/detailStyles';
import { Field, HeroStat, HeroStats, InformationGrid, Section } from '../../../../components/common/detail/EntityDetailCommon';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type Inject, type LessonsAnswer, type LessonsCategory, type LessonsQuestion, type LessonsSendInput, type LessonsTemplate, type Objective, type Team, type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext, Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ConfigurationSection from '../../common/ConfigurationSection';
import { LessonContext, PermissionsContext } from '../../common/Context';
import CreateLessonsCategory from '../categories/CreateLessonsCategory';
import CreateObjective from '../CreateObjective';
import LessonsApplyTemplateDialog from '../LessonsApplyTemplateDialog';
import LessonsObjectives from '../LessonsObjectives';
import LessonsPlaceholder from '../LessonsPlaceholder';
import ObjectiveEvaluations from '../ObjectiveEvaluations';
import SendLessonsForm from '../SendLessonsForm';
import AnswersByQuestionDialog from './AnswersByQuestionDialog';
import CrysisIntensity from './CrysisIntensity';
import LessonsCategories from './LessonsCategories';

interface GenericSource {
  id: string;
  type: string;
  name: string;
  score: number;
  lessons_answers_number: number;
  communications_number: number;
  start_date: string;
  end_date: string;
  users_number: number;
  logs_number: number;
  lessons_anonymized: boolean;
  isReadOnly: boolean;
  isUpdatable: boolean;
}

interface Props {
  source: GenericSource;
  objectives: Objective[];
  injects: Inject[];
  teamsMap: Record<string, Team>;
  teams: Team[];
  lessonsCategories: LessonsCategory[];
  lessonsQuestions: LessonsQuestion[];
  lessonsAnswers: LessonsAnswer[];
  lessonsTemplates: LessonsTemplate[];
  usersMap: Record<string, User>;
}

const Lessons: FunctionComponent<Props> = ({
  source,
  objectives,
  injects,
  teams,
  teamsMap,
  lessonsCategories,
  lessonsQuestions,
  lessonsAnswers,
  lessonsTemplates,
  usersMap,
}) => {
  // Standard hooks
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);

  const [selectedObjective, setSelectedObjective] = useState<string | null>(null);
  const [openApplyTemplate, setOpenApplyTemplate] = useState<boolean>(false);
  const [openResetAnswers, setOpenResetAnswers] = useState<boolean>(false);
  const [openEmptyLessons, setOpenEmptyLessons] = useState<boolean>(false);
  const [openSendLessons, setOpenSendLessons] = useState<boolean>(false);
  const [openAnonymize, setOpenAnonymize] = useState<boolean>(false);
  const [selectedQuestion, setSelectedQuestion] = useState<LessonsQuestion | null>(null);
  const ability = useContext(AbilityContext);

  useEffect(() => {
    if (openApplyTemplate) {
      dispatch(fetchLessonsTemplates());
    }
  }, [openApplyTemplate]);

  // Context
  const {
    onApplyLessonsTemplate,
    onResetLessonsAnswers,
    onEmptyLessonsCategories,
    onUpdateSourceLessons,
    onSendLessons,
  } = useContext(LessonContext);

  const resetAnswers = async () => {
    if (onResetLessonsAnswers) {
      await onResetLessonsAnswers();
    }
    return setOpenResetAnswers(false);
  };
  const emptyLessons = async () => {
    await onEmptyLessonsCategories();
    return setOpenEmptyLessons(false);
  };
  const toggleAnonymize = async () => {
    await onUpdateSourceLessons(!source.lessons_anonymized);
    return setOpenAnonymize(false);
  };

  const handleSubmitSendLessons = async (data: LessonsSendInput) => {
    if (onSendLessons) {
      await onSendLessons(data);
    }
    return setOpenSendLessons(false);
  };
  const answers = R.groupBy(R.prop('lessons_answer_question'), lessonsAnswers);
  const selectedQuestionAnswers = selectedQuestion && selectedQuestion.lessonsquestion_id
    ? answers[selectedQuestion.lessonsquestion_id] || []
    : [];
  const getHoursDiff = (startDate: Date, endDate: Date): number => {
    const msInHour = 1000 * 60 * 60;
    return Math.round(Math.abs(endDate.getTime() - startDate.getTime()) / msInHour);
  };
  const canApplyTemplate = permissions.canManage && ability.can(ACTIONS.ACCESS, SUBJECTS.LESSONS_LEARNED);
  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      paddingBottom: 5,
    }}
    >
      {/* Headline metrics */}
      {/* padding=16 (ISO) : la gouttière droite des HeroStat est structurelle,
          elle reste — PAPER-GAP-INVENTORY §5.3. */}
      <Paper padding={16}>
        <HeroStats>
          <HeroStat
            icon={SportsScoreOutlined}
            label={t('Overall objectives score')}
            value={`${source.score}%`}
          />
          <HeroStat
            icon={SpeakerNotesOutlined}
            label={t('Simulation logs')}
            value={source.logs_number}
          />
          <HeroStat
            icon={BallotOutlined}
            label={t('Poll replies')}
            value={source.lessons_answers_number}
          />
          <HeroStat
            icon={ContactMailOutlined}
            label={t('Messages')}
            value={source.communications_number}
          />
        </HeroStats>
      </Paper>

      {/* Details / Parameters / Control */}
      <Box sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: {
          xs: 'minmax(0, 1fr)',
          lg: permissions.canManage ? 'repeat(3, minmax(0, 1fr))' : 'repeat(2, minmax(0, 1fr))',
        },
        alignItems: 'stretch',
      }}
      >
        <InformationGrid title={t('Details')}>
          <Field label={t('Start date')}>{nsdt(source.start_date)}</Field>
          <Field label={t('End date')}>{nsdt(source.end_date)}</Field>
          <Field label={t('Duration')}>
            {getHoursDiff(
              source.start_date ? new Date(source.start_date) : new Date(),
              source.end_date ? new Date(source.end_date) : new Date(),
            )}
            {' '}
            {t('hours')}
          </Field>
          <Field label={t('Team')}>
            {source.users_number}
            {' '}
            {t('players')}
          </Field>
        </InformationGrid>
        <InformationGrid title={t('Parameters')}>
          {permissions.canManage && (
            <Field label={t('Questionnaire mode')}>
              <FormControlLabel
                control={(
                  <Switch
                    disabled={source.lessons_anonymized}
                    checked={source.lessons_anonymized}
                    onChange={() => setOpenAnonymize(true)}
                    name="anonymized"
                    size="small"
                  />
                )}
                label={t('Anonymize answers')}
              />
            </Field>
          )}
          {canApplyTemplate && (
            <Field label={t('Template')}>
              <Button
                variant="outlined"
                size="small"
                color="primary"
                startIcon={<ContentPasteGoOutlined />}
                onClick={() => setOpenApplyTemplate(true)}
              >
                {t('Apply')}
              </Button>
            </Field>
          )}
          <Field label={t('Check')}>
            <Button
              variant="outlined"
              size="small"
              color="primary"
              startIcon={<VisibilityOutlined />}
              href={`/lessons/${source.type}/${source.id}?preview=true`}
            >
              {t('Preview')}
            </Button>
          </Field>
          {permissions.canManage && (
            <Field label={t('Categories and questions')}>
              <Button
                variant="outlined"
                size="small"
                color="error"
                startIcon={<DeleteSweepOutlined />}
                onClick={() => setOpenEmptyLessons(true)}
              >
                {t('Clear out')}
              </Button>
            </Field>
          )}
        </InformationGrid>
        {permissions.canManage && (
          <Section title={t('Control')}>
            <Alert severity="info" sx={{ marginBottom: 2 }}>
              {t(
                'Sending the questionnaire will emit an email to each player with a unique link to access and fill it.',
              )}
            </Alert>
            <Box sx={{
              display: 'grid',
              gap: 2,
              gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
            }}
            >
              <Field label={t('Questionnaire')}>
                <Button
                  variant="outlined"
                  size="small"
                  color="primary"
                  startIcon={<SendOutlined />}
                  onClick={() => setOpenSendLessons(true)}
                >
                  {t('Send')}
                </Button>
              </Field>
              <Field label={t('Answers')}>
                <Button
                  variant="outlined"
                  size="small"
                  color="error"
                  startIcon={<DeleteSweepOutlined />}
                  onClick={() => setOpenResetAnswers(true)}
                >
                  {t('Reset')}
                </Button>
              </Field>
            </Box>
          </Section>
        )}
      </Box>

      {/* Objectives + crisis intensity */}
      <Box sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: {
          xs: 'minmax(0, 1fr)',
          lg: 'minmax(0, 1fr) minmax(0, 2fr)',
        },
        alignItems: 'stretch',
      }}
      >
        <ConfigurationSection
          title={t('Objectives')}
          count={objectives.length}
          action={source.isUpdatable ? <CreateObjective /> : undefined}
        >
          <LessonsObjectives
            objectives={objectives}
            setSelectedObjective={setSelectedObjective}
            source={source}
          />
        </ConfigurationSection>
        <ConfigurationSection title={t('Crisis intensity (injects by hour)')}>
          <CrysisIntensity injects={injects} />
        </ConfigurationSection>
      </Box>

      {/* Categories and questions */}
      <section>
        <header style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
          marginBottom: theme.spacing(1.5),
        }}
        >
          <Typography sx={{
            ...SECTION_LABEL_SX,
            marginBottom: 0,
          }}
          >
            {t('Categories and questions')}
          </Typography>
          <div style={{ flex: 1 }} />
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
            <CreateLessonsCategory />
          </Can>
        </header>
        {lessonsCategories.length === 0 ? (
          /* padding=32 porté par le Paper, et les 32px du placeholder retirés
             ICI, au site d'appel : le composant partagé garde son rendu pour
             ses autres consommateurs — PAPER-GAP-INVENTORY §6.2. */
          <Paper padding={32}>
            <LessonsPlaceholder
              disablePadding
              icon={BallotOutlined}
              message={t('No lessons learned categories yet. Apply a template or create a category to build the questionnaire.')}
            />
          </Paper>
        ) : (
          <LessonsCategories
            lessonsCategories={lessonsCategories}
            lessonsAnswers={lessonsAnswers}
            setSelectedQuestion={setSelectedQuestion}
            lessonsQuestions={lessonsQuestions}
            teamsMap={teamsMap}
            teams={teams}
            isReport={false}
          />
        )}
      </section>

      {/* Dialogs */}
      <Dialog
        TransitionComponent={Transition}
        keepMounted={false}
        open={selectedObjective !== null}
        onClose={() => setSelectedObjective(null)}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Objective achievement evaluation')}</DialogTitle>
        <DialogContent>
          <ObjectiveEvaluations
            objectiveId={selectedObjective}
            isUpdatable={source.isUpdatable}
            handleClose={() => setSelectedObjective(null)}
          />
        </DialogContent>
      </Dialog>
      <LessonsApplyTemplateDialog
        open={openApplyTemplate}
        onClose={() => setOpenApplyTemplate(false)}
        onApply={templateId => onApplyLessonsTemplate(templateId)}
        lessonsTemplates={lessonsTemplates}
        variant="simulation"
      />
      <Dialog
        open={openResetAnswers}
        TransitionComponent={Transition}
        onClose={() => setOpenResetAnswers(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to reset lessons learned answers?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={() => setOpenResetAnswers(false)}>
            {t('Cancel')}
          </Button>
          <Button variant="contained" color="primary" onClick={resetAnswers}>
            {t('Reset')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={openEmptyLessons}
        TransitionComponent={Transition}
        onClose={() => setOpenEmptyLessons(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t(
              'Do you want to empty lessons learned categories and questions?',
            )}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={() => setOpenEmptyLessons(false)}>
            {t('Cancel')}
          </Button>
          <Button variant="contained" color="primary" onClick={emptyLessons}>
            {t('Clear out')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={openSendLessons}
        TransitionComponent={Transition}
        onClose={() => setOpenSendLessons(false)}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Send the lessons learned questionnaire')}</DialogTitle>
        <DialogContent style={{ overflowX: 'hidden' }}>
          <SendLessonsForm
            onSubmit={handleSubmitSendLessons}
            initialValues={{
              subject: t('[{exerciseName}] Lessons learned questionnaire', { exerciseName: source.name }),
              body: `${t('Hello')},<br /><br />${t(
                // eslint-disable-next-line no-template-curly-in-string
                'We would like thank your for your participation in this simulation. You are kindly requested to fill this lessons learned questionnaire: <a href="${lessons_uri}">${lessons_uri}</a>.',
              )}<br /><br />${t('Best regards')},<br />${t(
                'The simulation control team',
              )}`,
            }}
            handleClose={() => setOpenSendLessons(false)}
          />
        </DialogContent>
      </Dialog>
      <AnswersByQuestionDialog
        open={!!selectedQuestion}
        onClose={() => setSelectedQuestion(null)}
        question={selectedQuestion?.lessons_question_content || ''}
        answers={selectedQuestionAnswers}
        anonymized={source.lessons_anonymized}
        usersMap={usersMap}
      />
      <Dialog
        open={openAnonymize}
        TransitionComponent={Transition}
        onClose={() => setOpenAnonymize(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to anonymize lessons learned questionnaire?')}
          </DialogContentText>
          <Alert severity="warning" style={{ marginTop: 10 }}>
            {t('You will not be able to change this setting later.')}
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={() => setOpenAnonymize(false)}>
            {t('Cancel')}
          </Button>
          <Button variant="contained" color="primary" onClick={toggleAnonymize}>
            {t('Anonymize')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default Lessons;
