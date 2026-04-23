import { AddCircleOutlined, MovieFilterOutlined } from '@mui/icons-material';
import { Stack } from '@mui/material';
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
  selectedElements,
  deSelectedElements,
  open,
  searchPaginationInput,
  onClose,
}) => {
  const { t } = useFormatter();
  const [selectedExecutionMode, setSelectedExecutionMode] = useState<ExecutionMode>(ExecutionMode.EMPTY);

  const executionModes = [
    {
      icon: <MovieFilterOutlined fontSize="large" color="primary" />,
      title: t('Create a new scenario'),
      description: t('Build a fully customized Scenario'),
      onClick: () => setSelectedExecutionMode(ExecutionMode.SCENARIO_CREATE),
      disabled: false,
    },
    {
      icon: <AddCircleOutlined fontSize="large" color="primary" />,
      title: t('Add to an existing scenario'),
      description: t('Easily insert new steps into an existing Scenario'),
      onClick: () => setSelectedExecutionMode(ExecutionMode.SCENARIO_UPDATE),
      disabled: false,
    },
    {
      icon: <Target fontSize="large" color={isOnlyOneItemSelected ? 'primary' : 'disabled'} />,
      title: t('Run atomic test'),
      description: t('Execute individually the selected actions immediately'),
      onClick: () => {
        setSelectedExecutionMode(ExecutionMode.ATOMIC_CREATE);
      },
      disabled: !isOnlyOneItemSelected,
      tooltip: isOnlyOneItemSelected ? '' : t('Atomic testing validates one attack action in isolation. Select a single action to run the test'),
    },
  ];

  const drawerTitlesMap: Record<ExecutionMode, string> = {
    [ExecutionMode.EMPTY]: t('Choose Execution mode'),
    [ExecutionMode.SCENARIO_CREATE]: t('Create a new scenario'),
    [ExecutionMode.SCENARIO_UPDATE]: t('Select a scenario'),
    [ExecutionMode.ATOMIC_CREATE]: t('Create an atomic test'),
  };

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={drawerTitlesMap[selectedExecutionMode]}
    >
      <>
        {ExecutionMode.EMPTY === selectedExecutionMode && (
          <Stack flexDirection="column">
            {executionModes.map((executionMode, index) =>
              <ThreatArsenalExecutionModeCardComponent key={`execution-mode-${index}`} executionMode={executionMode} />,
            )}
          </Stack>
        )}
        {ExecutionMode.SCENARIO_CREATE === selectedExecutionMode && (
          <ThreatArsenalScenarioCreationComponent
            isExclusionMode={isExclusionMode}
            selectedElements={selectedElements}
            deSelectedElements={deSelectedElements}
            searchPaginationInput={searchPaginationInput}
            handleClose={() => setSelectedExecutionMode(ExecutionMode.EMPTY)}
          />
        )}
        {ExecutionMode.SCENARIO_UPDATE === selectedExecutionMode && (
          <ThreatArsenalScenarioUpdateComponent
            isExclusionMode={isExclusionMode}
            selectedElements={selectedElements}
            deSelectedElements={deSelectedElements}
            searchPaginationInput={searchPaginationInput}
            handleClose={() => setSelectedExecutionMode(ExecutionMode.EMPTY)}
          />
        )}
        {ExecutionMode.ATOMIC_CREATE === selectedExecutionMode && (
          <ThreatArsenalAtomicTestCreationComponent
            isExclusionMode={isExclusionMode}
            selectedElements={selectedElements}
            deSelectedElements={deSelectedElements}
            searchPaginationInput={searchPaginationInput}
            handleClose={() => setSelectedExecutionMode(ExecutionMode.EMPTY)}
          />
        )}
      </>
    </Drawer>
  );
};

export default ThreatArsenalRunTestDrawer;
