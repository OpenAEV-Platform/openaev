import { Alert, Dialog, DialogContent, DialogTitle, Grid, Step, StepButton, Stepper } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { fetchExecutors } from '../../../actions/executors/executor-action';
import { type ExecutorHelper } from '../../../actions/executors/executor-helper';
import { type LoggedHelper, type MeTokensHelper } from '../../../actions/helper';
import { meTokens } from '../../../actions/users/User';
import Breadcrumbs from '../../../components/Breadcrumbs';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type ExecutorOutput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import ExecutorDocumentationLink from './ExecutorDocumentationLink';
import ExecutorSelector from './ExecutorSelector';
import InstructionSelector from './InstructionSelector';
import PlatformSelector from './PlatformSelector';

const OPENAEV_CALDERA = 'openaev_caldera';
const OPENAEV_AGENT = 'openaev_agent';
const OPENAEV_CROWDSTRIKE = 'openaev_crowdstrike';
const OPENAEV_TANIUM = 'openaev_tanium';
const OPENAEV_SENTINELONE = 'openaev_sentinelone';

const Executors = () => {
  // Standard hooks
  const theme = useTheme();
  const { t } = useFormatter();
  const [platform, setPlatform] = useState<null | string>(null);
  const [selectedExecutor, setSelectedExecutor] = useState<null | ExecutorOutput>(null);
  const [activeStep, setActiveStep] = useState(0);
  const dispatch = useAppDispatch();

  // Fetching data
  const { settings, executors, tokens } = useHelper((helper: ExecutorHelper & MeTokensHelper & LoggedHelper) => ({
    settings: helper.getPlatformSettings(),
    executors: helper.getExistingExecutors(),
    tokens: helper.getMeTokens(),
  }));
  useDataLoader(() => {
    dispatch(fetchExecutors());
    dispatch(meTokens());
  });
  const userToken = tokens.length > 0 ? tokens[0] : undefined;

  const order = {
    openaev_agent: 0,
    openaev_caldera: 1,
    openaev_tanium: 2,
    openaev_crowdstrike: 3,
    openaev_sentinelone: 4,
  };

  const sortedExecutors = executors.sort((a: ExecutorOutput, b: ExecutorOutput) => order[a.executor_type as keyof typeof order] - order[b.executor_type as keyof typeof order]);
  const needInformationStepper = (selectedExecutor?.executor_type === OPENAEV_AGENT || selectedExecutor?.executor_type === OPENAEV_CALDERA);
  const showEEChip = (executor: ExecutorOutput) => !settings.platform_license?.license_is_validated
    && (executor.executor_type === OPENAEV_TANIUM || executor.executor_type === OPENAEV_CROWDSTRIKE || executor.executor_type === OPENAEV_SENTINELONE);

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
        <a
          href="https://docs.openaev.io/latest/deployment/ecosystem/executors/?h=agent#deploy-agents"
          target="_blank"
          rel="noreferrer"
        >
          {t('in the documentation')}
        </a>
        .
      </Alert>
      <Grid container spacing={3}>
        {sortedExecutors.map((executor: ExecutorOutput) => (
          <Grid key={executor.executor_id} style={{ width: '20%' }}>
            <ExecutorSelector
              executor={executor}
              setSelectedExecutor={setSelectedExecutor}
              showEEChip={showEEChip(executor)}
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
                      <StepButton
                        color="inherit"
                        onClick={() => setActiveStep(index)}
                      >
                        {label}
                      </StepButton>
                    </Step>
                  ))}
                </Stepper>
                {activeStep === 0 && (
                  <PlatformSelector
                    selectedExecutor={selectedExecutor}
                    setActiveStep={setActiveStep}
                    setPlatform={setPlatform}
                  />
                )}
                {activeStep === 1 && platform && (
                  <InstructionSelector
                    userToken={userToken}
                    platform={platform}
                    selectedExecutor={selectedExecutor}
                  />
                )}
              </>
            )}
          {!needInformationStepper && selectedExecutor && (
            <ExecutorDocumentationLink
              showEEChip={showEEChip(selectedExecutor)}
              executor={selectedExecutor}
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
};

export default Executors;
