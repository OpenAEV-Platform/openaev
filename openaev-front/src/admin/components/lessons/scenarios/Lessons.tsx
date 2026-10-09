import { Button, Paper, Switch } from '@filigran/design-system';
import { BallotOutlined, ContentPasteGoOutlined, DeleteSweepOutlined, VisibilityOutlined } from '@mui/icons-material';
import { Box, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';

import { fetchLessonsTemplates } from '../../../../actions/Lessons';
import { Field } from '../../../../components/common/detail/EntityDetailCommon';
import LibHeaderRow from '../../../../components/common/LibHeaderRow';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type LessonsAnswer, type LessonsCategory, type LessonsQuestion, type LessonsTemplate, type Objective, type Team } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { Can, useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ConfigurationSection, { SECTION_HEADER_WITH_ACTION_HEIGHT } from '../../common/ConfigurationSection';
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

const PARAMETERS_GRID_SX = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
  gap: '12px',
  rowGap: '16px',
  alignContent: 'start',
} as const;

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
  const ability = useAbility();

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
      gap: 3,
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
        <ConfigurationSection
          title={t('Parameters')}
          withSurface
          padding={16}
          headerMinHeight={SECTION_HEADER_WITH_ACTION_HEIGHT}
        >
          <Box sx={PARAMETERS_GRID_SX}>
            {permissions.canManage && (
              <Field label={t('Questionnaire mode')}>
                <Switch
                  name="anonymized"
                  checked={source.lessons_anonymized}
                  onCheckedChange={() => {
                    if (!source.lessons_anonymized) {
                      setOpenAnonymize(true);
                    } else {
                      toggleAnonymize();
                    }
                  }}
                  label={t('Anonymize answers')}
                />
              </Field>
            )}
            {canApplyTemplate && (
              <Field label={t('Template')}>
                <Button type="button" priority="secondary" size="sm" startIcon={<ContentPasteGoOutlined fontSize="small" />} onClick={() => setOpenApplyTemplate(true)}>
                  {t('Apply')}
                </Button>
              </Field>
            )}
            <Field label={t('Check')}>
              <Button asChild priority="secondary" size="sm">
                <a href={`/lessons/${source.type}/${source.id}?preview=true`}>
                  <VisibilityOutlined fontSize="small" />
                  {t('Preview')}
                </a>
              </Button>
            </Field>
            {permissions.canManage && (
              <Field label={t('Categories and questions')}>
                <Button type="button" variant="destructive" priority="secondary" size="sm" startIcon={<DeleteSweepOutlined fontSize="small" />} onClick={() => setOpenEmptyLessons(true)}>
                  {t('Clear out')}
                </Button>
              </Field>
            )}
          </Box>
        </ConfigurationSection>
        <ConfigurationSection
          title={t('Objectives')}
          count={objectives.length}
          action={source.isUpdatable ? <CreateObjective /> : undefined}
          withSurface
          headerMinHeight={SECTION_HEADER_WITH_ACTION_HEIGHT}
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
        keepMounted={false}
        open={selectedObjective !== null}
        onClose={() => setSelectedObjective(null)}
        fullWidth
        maxWidth="md"
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
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
        onClose={() => setOpenEmptyLessons(false)}
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t(
              'Do you want to empty lessons learned categories and questions?',
            )}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button type="button" priority="secondary" onClick={() => setOpenEmptyLessons(false)}>
            {t('Cancel')}
          </Button>
          <Button type="button" onClick={emptyLessons}>
            {t('Clear out')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={openAnonymize}
        onClose={() => setOpenAnonymize(false)}
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to anonymize lessons learned questionnaire?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button type="button" priority="secondary" onClick={() => setOpenAnonymize(false)}>
            {t('Cancel')}
          </Button>
          <Button type="button" onClick={toggleAnonymize}>
            {t('Anonymize')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default Lessons;
