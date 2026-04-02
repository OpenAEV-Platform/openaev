import Drawer from '../../../components/common/Drawer';
import {FunctionComponent, useState} from "react";
import { useFormatter } from "../../../components/i18n";
import {
  type InjectorContract,
  InjectorContractActionOutput,
  type SearchPaginationInput
} from "../../../utils/api-types";
import {Avatar, IconButton, Slide, Stack} from "@mui/material";
import {AddCircleOutlined, HelpOutlined, HighlightOffOutlined, MovieFilterOutlined} from "@mui/icons-material";
import {Target} from "mdi-material-ui";
import ThreatArsenalExecutionModeCardComponent from "./ThreatArsenalExecutionModeCardComponent";
import InjectCardComponent from "../common/injects/InjectCardComponent";
import InjectIcon from "../common/injects/InjectIcon";
import {isNotEmptyField} from "../../../utils/utils";
import InjectForm from "../common/injects/form/InjectForm";
import ThreatArsenalAtomicTestCreationComponent from "./ThreatArsenalAtomicTestCreationComponent";

interface Props {
  isExclusionMode: boolean;
  isOnlyOneItemSelected: boolean;
  selectedElements: Record<string, InjectorContractActionOutput>;
  deSelectedElements: Record<string, InjectorContractActionOutput>;
  searchPaginationInput: SearchPaginationInput;
  open: boolean;
  onClose: (deselectAll?: boolean) => void;
}

enum ExecutionMode {
  EMPTY = "EMPTY",
  SCENARIO_CREATE = "SCENARIO_CREATE",
  SCENARIO_UPDATE = "SCENARIO_UPDATE",
  ATOMIC_CREATE = "ATOMIC_CREATE",
}

const ThreatArsenalRunTestDrawer: FunctionComponent<Props> = ({ isExclusionMode, isOnlyOneItemSelected, selectedElements, deSelectedElements, open, searchPaginationInput, onClose }) => {
  const { t } = useFormatter();
  const [selectedExecutionMode, setSelectedExecutionMode] = useState<ExecutionMode>(ExecutionMode.EMPTY);

  const executionModes = [
    {
      icon: <MovieFilterOutlined fontSize="large" color="primary" />,
      title: t('Create a new scenario').toUpperCase(),
      description: t('Build a fully customized Scenario'),
      onClick: () => setSelectedExecutionMode(ExecutionMode.SCENARIO_CREATE),
      disabled: false
    },
    {
      icon: <AddCircleOutlined fontSize="large" color="primary" />,
      title: t('Add to an existing scenario').toUpperCase(),
      description: t('Easily insert new steps into an existing Scenario'),
      onClick: () => setSelectedExecutionMode(ExecutionMode.SCENARIO_UPDATE),
      disabled: false
    },
    {
      icon: <Target fontSize="large" color={isOnlyOneItemSelected ? "primary" : "disabled"} />,
      title: t('Run atomic test').toUpperCase(),
      description: t('Execute individually the selected actions immediately'),
      onClick: () => {
        setSelectedExecutionMode(ExecutionMode.ATOMIC_CREATE);
      },
      disabled: !isOnlyOneItemSelected
    }
  ];

  const drawerTitlesMap: Record<ExecutionMode, string> = {
    [ExecutionMode.EMPTY]: t('Choose Execution mode'),
    [ExecutionMode.SCENARIO_CREATE]: t('Create a new scenario'),
    [ExecutionMode.SCENARIO_UPDATE]: t('Select a scenario'),
    [ExecutionMode.ATOMIC_CREATE]: t('Empty'),
  };

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={drawerTitlesMap[selectedExecutionMode]}
    >
      <>
        {ExecutionMode.EMPTY === selectedExecutionMode && (
          <Stack flexDirection="column" alignItems="center">
            {executionModes.map((executionMode, index) =>
              <ThreatArsenalExecutionModeCardComponent key={`execution-mode-${index}`} executionMode={executionMode} />
            )}
          </Stack>
        )}
        {ExecutionMode.ATOMIC_CREATE === selectedExecutionMode && (
          <ThreatArsenalAtomicTestCreationComponent
            isExclusionMode={isExclusionMode}
            selectedElements={selectedElements}
            deSelectedElements={deSelectedElements}
            searchPaginationInput={searchPaginationInput}
          />
        )}
      </>
    </Drawer>
  );
};

export default ThreatArsenalRunTestDrawer;