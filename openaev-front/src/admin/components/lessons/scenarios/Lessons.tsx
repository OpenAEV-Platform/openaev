import { Paper } from '@filigran/design-system';
import { BallotOutlined, ContentPasteGoOutlined, DeleteSweepOutlined, VisibilityOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Switch,
} from '@mui/material';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';

import { fetchLessonsTemplates } from '../../../../actions/Lessons';
import { Field, InformationGrid } from '../../../../components/common/detail/EntityDetailCommon';
import LibHeaderRow from '../../../../components/common/LibHeaderRow';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type LessonsAnswer, type LessonsCategory, type LessonsQuestion, type LessonsTemplate, type Objective, type Team } from '../../../../utils/api-types';
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
import LessonsCategories from './LessonsCategories';

interface GenericSource {
  id: string;
  type: string;
  name: string;
  lessons_anonymized: boolean;
  isReadOnly: boolean;
  isUpdatable: boolean;
}

interface Props {
  source: GenericSource;
  objectives: Objective[];
  teamsMap: Record<string, Team>;
  teams: Team[];
  lessonsCategories: LessonsCategory[];
  lessonsQuestions: LessonsQuestion[];
  lessonsAnswers?: LessonsAnswer[];
  lessonsTemplates: LessonsTemplate[];
}

const Lessons: FunctionComponent<Props> = ({
  source,
  objectives,
  teams,
  teamsMap,
  lessonsCategories,
  lessonsQuestions,
  lessonsTemplates,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);

  const [selectedObjective, setSelectedObjective] = useState<string | null>(null);
  const [openApplyTemplate, setOpenApplyTemplate] = useState<boolean>(false);
  const [openEmptyLessons, setOpenEmptyLessons] = useState<boolean>(false);
  const [openAnonymize, setOpenAnonymize] = useState<boolean>(false);
  const ability = useContext(AbilityContext);

  useEffect(() => {
    if (openApplyTemplate) {
      dispatch(fetchLessonsTemplates());
    }
  }, [openApplyTemplate]);

  // Context
  const {
    onApplyLessonsTemplate,
    onEmptyLessonsCategories,
    onUpdateSourceLessons,
  } = useContext(LessonContext);

  const emptyLessons = async () => {
    await onEmptyLessonsCategories();
    return setOpenEmptyLessons(false);
  };
  const toggleAnonymize = async () => {
    await onUpdateSourceLessons(!source.lessons_anonymized);
    return setOpenAnonymize(false);
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
      {/* Parameters + objectives */}
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
        {/* action={null} is now a NO-OP, kept only to avoid churn: the
            library's header row is a constant 24px with or without an action,
            so this panel top-aligns with the Objectives column (which carries a create
            button in its header). */}
        <InformationGrid title={t('Parameters')} action={null}>
          {permissions.canManage && (
            <Field label={t('Questionnaire mode')}>
              <FormControlLabel
                control={(
                  <Switch
                    checked={source.lessons_anonymized}
                    onChange={() => {
                      if (!source.lessons_anonymized) {
                        setOpenAnonymize(true);
                      } else {
                        toggleAnonymize();
                      }
                    }}
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
        <ConfigurationSection
          title={t('Objectives')}
          count={objectives.length}
          action={source.isUpdatable ? <CreateObjective /> : undefined}
          withSurface
        >
          <LessonsObjectives
            objectives={objectives}
            setSelectedObjective={setSelectedObjective}
            source={source}
          />
        </ConfigurationSection>
      </Box>

      {/* Categories and questions */}
      <section>
        <LibHeaderRow
          title={t('Categories and questions')}
          action={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
              <CreateLessonsCategory />
            </Can>
          )}
        >
          {lessonsCategories.length === 0 ? (
          /* padding=32 carried by the Paper, and the placeholder's own 32px
             dropped HERE, at the call site: the shared component keeps its
             default rendering for its other consumers — PAPER-GAP-INVENTORY §5.6. */
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
              lessonsQuestions={lessonsQuestions}
              teamsMap={teamsMap}
              teams={teams}
              isReport={false}
            />
          )}
        </LibHeaderRow>
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
        variant="scenario"
      />
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
        open={openAnonymize}
        TransitionComponent={Transition}
        onClose={() => setOpenAnonymize(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to anonymize lessons learned questionnaire?')}
          </DialogContentText>
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
