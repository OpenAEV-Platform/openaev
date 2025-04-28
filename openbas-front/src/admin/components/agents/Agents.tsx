import { Alert, Dialog, DialogContent, DialogTitle, Grid, Step, StepButton, Stepper } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { fetchExecutors } from '../../../actions/Executor';
import { type ExecutorHelper } from '../../../actions/executors/executor-helper';
import { type MeTokensHelper } from '../../../actions/helper';
import { meTokens } from '../../../actions/User';
import Breadcrumbs from '../../../components/Breadcrumbs';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type Executor } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import ExecutorDocumentationLink from './ExecutorDocumentationLink';
import ExecutorSelector from './ExecutorSelector';
import InstructionSelector from './InstructionSelector';
import PlatformSelector from './PlatformSelector';

const OPENBAS_CALDERA = 'openbas_caldera';
const OPENBAS_AGENT = 'openbas_agent';
const OPENBAS_CROWDSTRIKE = 'openbas_crowdstrike';
const OPENBAS_TANIUM = 'openbas_tanium';

const Executors = () => {
  // Standard hooks
  const theme = useTheme();
  const { t } = useFormatter();
  const [platform, setPlatform] = useState<null | string>(null);
  const [selectedExecutor, setSelectedExecutor] = useState<null | Executor>(null);
  const [activeStep, setActiveStep] = useState(0);
  const dispatch = useAppDispatch();

  // Fetching data
  const { executors, tokens } = useHelper((helper: ExecutorHelper & MeTokensHelper) => ({
    executors: helper.getExecutors(),
    tokens: helper.getMeTokens(),
  }));
  useDataLoader(() => {
    dispatch(fetchExecutors());
    dispatch(meTokens());
  });
  const userToken = tokens.length > 0 ? tokens[0] : undefined;

  const order = {
    openbas_agent: 0,
    openbas_caldera: 1,
    openbas_tanium: 2,
    openbas_crowdstrike: 3,
  };

  const sortedExecutors = executors.sort((a: Executor, b: Executor) => order[a.executor_type as keyof typeof order] - order[b.executor_type as keyof typeof order]);
  const needInformationStepper = (selectedExecutor?.executor_type === OPENBAS_AGENT || selectedExecutor?.executor_type === OPENBAS_CALDERA);

  // -- Manage Dialogs
  const steps = [t('Choose your platform'), t('Installation Instructions')];

  const closeInstall = () => {
    setPlatform(null);
    setSelectedExecutor(null);
    setActiveStep(0);
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Agents'),
          current: true,
        }]}
      />
      <Alert variant="outlined" severity="info" style={{ marginBottom: theme.spacing(2) }}>
        {`${t('Here, you can download and install simulation agents available in your executors. Depending on the integrations you have enabled, some of them may be unavailable. Each agent can be installed on Windows, Linux and MacOS using x86_64 or arm64 architectures.')} ${t('Learn more information about how to setup simulation agents')} `}
        <a href="https://docs.openbas.io/latest/deployment/ecosystem/executors/?h=agent#deploy-agents" target="_blank" rel="noreferrer">{t('in the documentation')}</a>
        .
      </Alert>
      <Grid container spacing={3}>
        {sortedExecutors.map((executor: Executor) => (
          <Grid key={executor.executor_id} style={{ width: '20%' }}>
            <ExecutorSelector
              executor={executor}
              setSelectedExecutor={setSelectedExecutor}
              isEEExecutor={executor.executor_type == OPENBAS_TANIUM || executor.executor_type == OPENBAS_CROWDSTRIKE}
            />
          </Grid>
        ))}
      </Grid>
      <Dialog
        open={selectedExecutor !== null}
        slots={{ transition: Transition }}
        onClose={closeInstall}
        slotProps={{ paper: { elevation: 1 } }}
        maxWidth={needInformationStepper ? 'md' : 'sm'}
      >
        <DialogTitle>
          {`${selectedExecutor?.executor_name} `}
        </DialogTitle>
        <DialogContent>
          {needInformationStepper
            && (
              <>
                <Stepper activeStep={activeStep} style={{ padding: theme.spacing(0, 1, 3, 0) }}>
                  {steps.map((label, index) => (
                    <Step key={label}>
                      <StepButton color="inherit" onClick={() => setActiveStep(index)}>{label}</StepButton>
                    </Step>
                  ))}
                </Stepper>
                {activeStep === 0 && (
                  <PlatformSelector selectedExecutor={selectedExecutor} setActiveStep={setActiveStep} setPlatform={setPlatform} />
                )}
                {activeStep === 1 && platform && (
                  <InstructionSelector userToken={userToken} platform={platform} selectedExecutor={selectedExecutor} />
                )}
              </>
            )}
          {!needInformationStepper && selectedExecutor && (
            <ExecutorDocumentationLink executor={selectedExecutor} />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
};

export default Executors;
