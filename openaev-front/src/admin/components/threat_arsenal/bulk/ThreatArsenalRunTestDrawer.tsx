import { PlaylistAddOutlined, RouteOutlined } from '@mui/icons-material';
import { Box, Chip, Step, StepButton, StepLabel, Stepper, Typography } from '@mui/material';
import { Target } from 'mdi-material-ui';
import { type FunctionComponent, useState } from 'react';

import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../../utils/api-types';
import ThreatArsenalExecutionModeCardComponent from '../ThreatArsenalExecutionModeCardComponent';
import ThreatArsenalAtomicTestCreationComponent from './ThreatArsenalAtomicTestCreationComponent';
import ThreatArsenalScenarioCreationComponent from './ThreatArsenalScenarioCreationComponent';
import ThreatArsenalScenarioUpdateComponent from './ThreatArsenalScenarioUpdateComponent';

interface Props {
  isExclusionMode: boolean;
  isOnlyOneItemSelected: boolean;
  selectionCount: number;
  selectedElements: Record<string, ThreatArsenalAction>;
  deSelectedElements: Record<string, ThreatArsenalAction>;
  searchPaginationInput: SearchPaginationInput;
  open: boolean;
  onClose: (deselectAll?: boolean) => void;
}

enum ExecutionMode {
  EMPTY = 'EMPTY',
  SCENARIO_CREATE = 'SCENARIO_CREATE',
  SCENARIO_UPDATE = 'SCENARIO_UPDATE',
  ATOMIC_CREATE = 'ATOMIC_CREATE',
}

const ThreatArsenalRunTestDrawer: FunctionComponent<Props> = ({
  isExclusionMode,
  isOnlyOneItemSelected,
  selectionCount,
  selectedElements,
  deSelectedElements,
  open,
  searchPaginationInput,
  onClose,
}) => {
  const { t } = useFormatter();
  const [selectedExecutionMode, setSelectedExecutionMode] = useState<ExecutionMode>(ExecutionMode.EMPTY);
  const activeStep = selectedExecutionMode === ExecutionMode.EMPTY ? 0 : 1;
  const handleBack = () => setSelectedExecutionMode(ExecutionMode.EMPTY);

  const executionModes = [
    {
      icon: <RouteOutlined color="primary" />,
      title: t('Create a new scenario'),
      description: t('Build a fully customized Scenario'),
      onClick: () => setSelectedExecutionMode(ExecutionMode.SCENARIO_CREATE),
      disabled: false,
    },
    {
      icon: <PlaylistAddOutlined color="primary" />,
      title: t('Add to an existing scenario'),
      description: t('Easily insert new steps into an existing Scenario'),
      onClick: () => setSelectedExecutionMode(ExecutionMode.SCENARIO_UPDATE),
      disabled: false,
    },
    {
      icon: <Target color={isOnlyOneItemSelected ? 'primary' : 'disabled'} />,
      title: t('Run atomic test'),
      description: t('Execute individually the selected actions immediately'),
      onClick: () => setSelectedExecutionMode(ExecutionMode.ATOMIC_CREATE),
      disabled: !isOnlyOneItemSelected,
      tooltip: isOnlyOneItemSelected ? '' : t('Atomic testing validates one action in isolation. Select a single action to run the test'),
    },
  ];

  // Second step label: neutral placeholder before a mode is chosen, the chosen
  // mode afterwards, so the stepper doubles as a breadcrumb of the decision.
  const modeStepLabels: Record<ExecutionMode, string> = {
    [ExecutionMode.EMPTY]: t('Configuration'),
    [ExecutionMode.SCENARIO_CREATE]: t('Create a new scenario'),
    [ExecutionMode.SCENARIO_UPDATE]: t('Add to an existing scenario'),
    [ExecutionMode.ATOMIC_CREATE]: t('Run atomic test'),
  };

  return (
    <Drawer
      open={open}
      handleClose={() => onClose()}
      title={t('Run a test')}
      headerActions={(
        <Chip
          size="small"
          variant="outlined"
          color="primary"
          label={selectionCount === 1
            ? t('1 action selected')
            : t('{count} actions selected', { count: selectionCount })}
        />
      )}
    >
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2.5,
        paddingTop: 1,
      }}
      >
        <Stepper activeStep={activeStep}>
          <Step completed={activeStep > 0}>
            {/* StepButton (not a bare StepLabel onClick) so going back is focusable
                and keyboard-activatable. */}
            <StepButton onClick={handleBack} disabled={activeStep === 0}>
              {t('Execution mode')}
            </StepButton>
          </Step>
          <Step>
            <StepLabel>{modeStepLabels[selectedExecutionMode]}</StepLabel>
          </Step>
        </Stepper>

        {ExecutionMode.EMPTY === selectedExecutionMode && (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 1.5,
          }}
          >
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t('How do you want to execute the selected actions?')}
            </Typography>
            {executionModes.map(executionMode => (
              <ThreatArsenalExecutionModeCardComponent
                key={executionMode.title}
                executionMode={executionMode}
              />
            ))}
          </Box>
        )}
        {ExecutionMode.SCENARIO_CREATE === selectedExecutionMode && (
          <ThreatArsenalScenarioCreationComponent
            isExclusionMode={isExclusionMode}
            selectedElements={selectedElements}
            deSelectedElements={deSelectedElements}
            searchPaginationInput={searchPaginationInput}
            handleClose={handleBack}
          />
        )}
        {ExecutionMode.SCENARIO_UPDATE === selectedExecutionMode && (
          <ThreatArsenalScenarioUpdateComponent
            isExclusionMode={isExclusionMode}
            selectedElements={selectedElements}
            deSelectedElements={deSelectedElements}
            searchPaginationInput={searchPaginationInput}
            handleClose={handleBack}
          />
        )}
        {ExecutionMode.ATOMIC_CREATE === selectedExecutionMode && (
          <ThreatArsenalAtomicTestCreationComponent
            isExclusionMode={isExclusionMode}
            selectedElements={selectedElements}
            deSelectedElements={deSelectedElements}
            searchPaginationInput={searchPaginationInput}
            handleClose={handleBack}
          />
        )}
      </Box>
    </Drawer>
  );
};

export default ThreatArsenalRunTestDrawer;
