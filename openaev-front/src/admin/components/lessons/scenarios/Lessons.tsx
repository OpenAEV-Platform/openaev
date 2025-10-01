import { ContentPasteGoOutlined, DeleteSweepOutlined, VisibilityOutlined } from '@mui/icons-material';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, FormControl, FormControlLabel, Grid, Link, Paper, Radio, RadioGroup, Switch, Typography, useTheme } from '@mui/material';
import { type ChangeEvent, type FunctionComponent, useContext, useEffect, useState } from 'react';

import { fetchLessonsTemplates } from '../../../../actions/Lessons';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type LessonsAnswer, type LessonsCategory, type LessonsQuestion, type LessonsTemplate, type Objective, type Team } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext, Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { LessonContext, PermissionsContext } from '../../common/Context';
import CreateLessonsTemplate from '../../components/lessons/CreateLessonsTemplate';
import CreateLessonsCategory from '../categories/CreateLessonsCategory';
import CreateObjective from '../CreateObjective';
import LessonsObjectives from '../LessonsObjectives';
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
  const theme = useTheme();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);

  const [selectedObjective, setSelectedObjective] = useState<string | null>(null);
  const [openApplyTemplate, setOpenApplyTemplate] = useState<boolean>(false);
  const [openEmptyLessons, setOpenEmptyLessons] = useState<boolean>(false);
  const [openAnonymize, setOpenAnonymize] = useState<boolean>(false);
  const [templateValue, setTemplateValue] = useState<string | null>(null);
  const ability = useContext(AbilityContext);

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setTemplateValue(event.target.value);
  };

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

  const applyTemplate = async () => {
    if (templateValue !== null) {
      await onApplyLessonsTemplate(templateValue);
      return setOpenApplyTemplate(false);
    }
    return setOpenApplyTemplate(true);
  };
  const emptyLessons = async () => {
    await onEmptyLessonsCategories();
    return setOpenEmptyLessons(false);
  };
  const toggleAnonymize = async () => {
    const updatedSource = { ...source };
    await onUpdateSourceLessons(!updatedSource.lessons_anonymized);
    updatedSource.lessons_anonymized = !updatedSource.lessons_anonymized;
    return setOpenAnonymize(false);
  };
  return (
    <>
      <div style={{
        display: 'grid',
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: '1fr 2fr',
      }}
      >
        <Typography variant="h4" style={{ alignContent: 'center' }}>{t('Parameters')}</Typography>
        <Typography variant="h4">
          {t('Objectives')}
          {
            source.isUpdatable && (<CreateObjective />)
          }
        </Typography>
        <Paper variant="outlined" sx={{ padding: theme.spacing(3) }}>
          <Grid container spacing={3}>
            {permissions.canManage && (
              <Grid size={{ xs: 6 }}>
                <Typography variant="h3">{t('Questionnaire mode')}</Typography>
                <FormControlLabel
                  control={(
                    <Switch
                      disabled={false}
                      checked={source.lessons_anonymized}
                      onChange={() => {
                        if (!source.lessons_anonymized) {
                          setOpenAnonymize(true);
                        } else {
                          toggleAnonymize();
                        }
                      }}
                      name="anonymized"
                    />
                  )}
                  label={t('Anonymize answers')}
                />
              </Grid>
            )}

            {(permissions.canManage && ability.can(ACTIONS.ACCESS, SUBJECTS.LESSONS_LEARNED)) && (
              <Grid size={{ xs: 6 }}>
                <Typography variant="h3">{t('Template')}</Typography>
                <Button
                  startIcon={<ContentPasteGoOutlined />}
                  color="primary"
                  variant="contained"
                  onClick={() => setOpenApplyTemplate(true)}
                >
                  {t('Apply')}
                </Button>
              </Grid>
            )}
            <Grid size={{ xs: 6 }}>
              <Typography variant="h3">{t('Check')}</Typography>
              <Button
                startIcon={<VisibilityOutlined />}
                color="secondary"
                variant="contained"
                component={Link}
                href={`/lessons/${source.type}/${source.id}?preview=true`}
              >
                {t('Preview')}
              </Button>
            </Grid>
            {permissions.canManage && (
              <Grid size={{ xs: 6 }}>
                <Typography variant="h3">
                  {t('Categories and questions')}
                </Typography>

                <Button
                  startIcon={<DeleteSweepOutlined />}
                  color="error"
                  variant="contained"
                  onClick={() => setOpenEmptyLessons(true)}
                >
                  {t('Clear out')}
                </Button>
              </Grid>
            )}
          </Grid>
        </Paper>
        <LessonsObjectives
          objectives={objectives}
          setSelectedObjective={setSelectedObjective}
          source={source}
        />
      </div>
      <div style={{ marginTop: theme.spacing(2) }}>
        <LessonsCategories
          lessonsCategories={lessonsCategories}
          lessonsQuestions={lessonsQuestions}
          teamsMap={teamsMap}
          teams={teams}
          isReport={false}
        />
      </div>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
        <CreateLessonsCategory />
      </Can>
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
      <Dialog
        TransitionComponent={Transition}
        keepMounted={false}
        open={openApplyTemplate}
        onClose={() => setOpenApplyTemplate(false)}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Apply a lessons learned template')}</DialogTitle>
        <DialogContent>
          <Alert severity="info">
            {t(
              `Applying a template will add all categories and questions of the selectedtemplate to this ${source.type}.`,
            )}
          </Alert>
          <FormControl style={{
            margin: '10px 0 0 5px',
            width: '100%',
          }}
          >
            <RadioGroup
              style={{ width: '100%' }}
              aria-labelledby="controlled-radio-buttons-group"
              name="template"
              value={templateValue}
              onChange={handleChange}
            >
              {lessonsTemplates.map((template: LessonsTemplate) => {
                return (
                  <FormControlLabel
                    key={template.lessonstemplate_id}
                    style={{
                      width: '100%',
                      borderBottom: `1px solid ${theme.palette.background.paper}`,
                      margin: 0,
                    }}
                    value={template.lessonstemplate_id}
                    control={<Radio />}
                    label={(
                      <div style={{ margin: '15px 0 15px 10px' }}>
                        <Typography variant="h4">
                          {template.lessons_template_name}
                        </Typography>
                        <Typography variant="body2">
                          {template.lessons_template_description || t('No description')}
                        </Typography>
                      </div>
                    )}
                  />
                );
              })}
            </RadioGroup>
          </FormControl>
          <CreateLessonsTemplate inline />
          <div className="clearfix" />
          <div style={{
            float: 'right',
            marginTop: 20,
          }}
          >
            <Button
              onClick={() => setOpenApplyTemplate(false)}
              style={{ marginRight: 10 }}
            >
              {t('Cancel')}
            </Button>
            <Can I={ACTIONS.ACCESS} a={SUBJECTS.LESSONS_LEARNED}>
              <Button
                color="secondary"
                onClick={applyTemplate}
                disabled={templateValue === null}
              >
                {t('Apply')}
              </Button>
            </Can>

          </div>
        </DialogContent>
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
          <Button onClick={() => setOpenEmptyLessons(false)}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={emptyLessons}>
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
          <Button onClick={() => setOpenAnonymize(false)}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={toggleAnonymize}>
            {t('Anonymize')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default Lessons;
