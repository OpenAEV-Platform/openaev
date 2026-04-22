import { PlayArrowOutlined, Stop } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type Dispatch, type SetStateAction, useContext, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { playInjectsAssistantForScenario } from '../../../../actions/Inject';
import { createRunningExerciseFromScenario, updateScenarioRecurrence } from '../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import LoaderDialog from '../../../../components/common/loader/LoaderDialog';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { SIMULATION_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import {
  type Exercise,
  type InjectAssistantInput,
  type Scenario,
} from '../../../../utils/api-types';
import { MESSAGING$, useQueryParameter } from '../../../../utils/Environment';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Cron } from '../../../../utils/period/Cron';
import handle from '../../../../utils/period/Period';
import { type PeriodExpressionHandler } from '../../../../utils/period/PeriodExpressionHandler';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { truncate } from '../../../../utils/String';
import { InjectContext } from '../../common/Context';
import ScenarioAssistantDrawer from './scenario_assistant/ScenarioAssistantDrawer';
import ScenarioPopover from './ScenarioPopover';
import ScenarioRecurringFormDialog from './ScenarioRecurringFormDialog';

const useStyles = makeStyles()(() => ({
  title: {
    float: 'left',
    marginRight: 10,
  },
  statusScheduled: {
    float: 'left',
    margin: '4px 0 0 5px',
    width: 20,
    height: 20,
    borderRadius: '50%',
    boxShadow: '0px 0px 5px 2px #4caf50',
    animation: 'pulse-green 1s linear infinite alternate',
  },
  statusNotScheduled: {
    float: 'left',
    margin: '4px 0 0 5px',
    width: 20,
    height: 20,
    borderRadius: '50%',
    boxShadow: '0px 0px 5px 2px #f44336',
  },
  actions: {
    margin: '-6px 0 0 0',
    float: 'right',
    display: 'flex',
  },
}));

interface ScenarioHeaderProps {
  cronObject: PeriodExpressionHandler | null;
  setCronObject: Dispatch<SetStateAction<PeriodExpressionHandler | null>>;
  setSelectRecurring: Dispatch<SetStateAction<string>>;
  selectRecurring: string;
  setOpenScenarioRecurringFormDialog: Dispatch<SetStateAction<boolean>>;
  setOpenInstantiateSimulationAndStart: Dispatch<SetStateAction<boolean>>;
  openScenarioRecurringFormDialog: boolean;
  openInstantiateSimulationAndStart: boolean;
  noRepeat: boolean;
}

const ScenarioHeader = ({
  cronObject,
  setCronObject,
  setSelectRecurring,
  selectRecurring,
  noRepeat,
  openScenarioRecurringFormDialog,
  setOpenScenarioRecurringFormDialog,
  openInstantiateSimulationAndStart,
  setOpenInstantiateSimulationAndStart,
}: ScenarioHeaderProps) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { classes } = useStyles();
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const [openScenarioAssistantQueryParam] = useQueryParameter(['openScenarioAssistant']);
  const { injects, setInjects } = useContext(InjectContext);
  const { canLaunch, canManage } = useScenarioPermissions(scenarioId);

  const [openScenarioAssistant, setOpenScenarioAssistant] = useState(openScenarioAssistantQueryParam === 'true');
  const [openLoaderDialog, setOpenLoaderDialog] = useState(false);
  const [isInjectAssistantLoading, setIsInjectAssistantLoading] = useState(false);
  // Fetching data
  const { scenario }: { scenario: Scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));

  // Local
  const ended = scenario.scenario_recurrence_end && new Date(scenario.scenario_recurrence_end).getTime() < new Date().getTime();
  const onSubmit = (cron: Cron, start: string, end?: string) => {
    dispatch(updateScenarioRecurrence(scenarioId, {
      scenario_recurrence: cron.toCronExpression(),
      scenario_recurrence_start: start,
      scenario_recurrence_end: end,
    })).then((result: { [x: string]: string }) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        setCronObject(cron);
      }
    });
    setOpenScenarioRecurringFormDialog(false);
  };

  const onScenarioInjectAssistantSubmit = (data: InjectAssistantInput) => {
    setOpenScenarioAssistant(false);
    setIsInjectAssistantLoading(true);
    setOpenLoaderDialog(true);
    playInjectsAssistantForScenario(scenarioId, data).then((results) => {
      setInjects([...injects, ...results.data]);
      setIsInjectAssistantLoading(false);
    }).catch(() => setOpenLoaderDialog(false));
  };

  useEffect(() => {
    if (scenario.scenario_recurrence != null) {
      const newCron = handle(scenario.scenario_recurrence);
      setCronObject(newCron);
      if (noRepeat) {
        setSelectRecurring('noRepeat');
      } else {
        setSelectRecurring(newCron?.getRecurrenceMagnitude() || 'daily');
      }
    } else {
      setCronObject(null);
    }
  }, [scenario.scenario_recurrence]);
  const stop = () => {
    setCronObject(null);
    dispatch(updateScenarioRecurrence(scenarioId, {
      scenario_recurrence: undefined,
      scenario_recurrence_start: undefined,
      scenario_recurrence_end: undefined,
    }));
  };

  return (
    <>
      <Tooltip title={scenario.scenario_name}>
        <Typography variant="h1" gutterBottom={true} classes={{ root: classes.title }}>
          {truncate(scenario.scenario_name, 80)}
        </Typography>
      </Tooltip>
      <div style={{
        float: 'left',
        margin: '4px 10px 0 8px',
        color: theme.palette.text?.disabled,
        borderLeft: `1px solid ${theme.palette.text?.disabled}`,
        height: 20,
      }}
      />
      <Tooltip title={t(scenario.scenario_recurrence ? 'Scheduled' : 'Not scheduled')}>
        <div className={scenario.scenario_recurrence ? classes.statusScheduled : classes.statusNotScheduled} />
      </Tooltip>
      <div className={classes.actions}>
        { canLaunch
          && scenario.scenario_recurrence && !ended ? (
              <Button
                style={{ marginRight: theme.spacing(1) }}
                startIcon={<Stop />}
                variant="outlined"
                color="inherit"
                size="small"
                onClick={stop}
              >
                {t('Stop')}
              </Button>
            )
          : (
              <>
                {canManage
                  && (
                    <Button
                      style={{
                        marginRight: theme.spacing(1),
                        lineHeight: 'initial',
                        borderColor: theme.palette.divider,
                      }}
                      variant="outlined"
                      color="inherit"
                      size="small"
                      onClick={() => setOpenScenarioAssistant(true)}
                    >
                      {t('Scenario assistant')}
                    </Button>
                  )}
                {canLaunch
                  && (
                    <Button
                      style={{
                        marginRight: theme.spacing(1),
                        lineHeight: 'initial',
                      }}
                      startIcon={<PlayArrowOutlined />}
                      variant="contained"
                      color="primary"
                      size="small"
                      onClick={() => setOpenInstantiateSimulationAndStart(true)}
                    >
                      {t('Launch now')}
                    </Button>
                  )}
              </>
            )}
        <ScenarioPopover
          scenario={scenario}
          actions={['Duplicate', 'Update', 'Delete', 'Export']}
          onDelete={() => navigate('/admin/scenarios')}
        />
      </div>
      <ScenarioRecurringFormDialog
        cronObject={cronObject}
        setCronObject={setCronObject}
        selectRecurring={selectRecurring}
        onSelectRecurring={setSelectRecurring}
        open={openScenarioRecurringFormDialog}
        setOpen={setOpenScenarioRecurringFormDialog}
        onSubmit={onSubmit}
        initialValues={scenario}
      />
      <Dialog
        open={openInstantiateSimulationAndStart}
        TransitionComponent={Transition}
        onClose={() => setOpenInstantiateSimulationAndStart(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('A simulation will be launched based on this scenario and will start immediately. Are you sure you want to proceed?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenInstantiateSimulationAndStart(false)}>
            {t('Cancel')}
          </Button>
          <Button
            color="secondary"
            onClick={async () => {
              setOpenInstantiateSimulationAndStart(false);
              const exercise: Exercise = (await createRunningExerciseFromScenario(scenarioId)).data;
              navigate(`${SIMULATION_BASE_URL}/${exercise.exercise_id}`);
              MESSAGING$.notifySuccess(t('New simulation successfully created and started'));
            }}
          >
            {t('Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
      <ScenarioAssistantDrawer
        open={openScenarioAssistant}
        onClose={() => setOpenScenarioAssistant(false)}
        onSubmit={(data: InjectAssistantInput) => onScenarioInjectAssistantSubmit(data)}
      />
      <div className="clearfix" />
      <LoaderDialog
        open={openLoaderDialog}
        isSubmitting={isInjectAssistantLoading}
        loadMessage={t('Injects generation in progress...')}
        successMessage={t('Injects successfully generated.')}
        redirectButtonLabel={t('Access these injects')}
        redirectLink={`/admin/scenarios/${scenarioId}/injects`}
        onClose={() => setOpenLoaderDialog(false)}
      />
    </>
  );
};

export default ScenarioHeader;
