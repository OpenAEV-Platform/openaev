import Drawer from '../../../components/common/Drawer';
import type { FunctionComponent } from "react";
import { useFormatter } from "../../../components/i18n";
import {InjectorContractActionOutput} from "../../../utils/api-types";
import { Stack } from "@mui/material";
import {AddCircleOutlined, MovieFilterOutlined } from "@mui/icons-material";
import {Target} from "mdi-material-ui";
import ThreatArsenalExecutionModeCardComponent from "./ThreatArsenalExecutionModeCardComponent";

interface Props {
  isExclusionMode: boolean;
  isOnlyOneItemSelected: boolean;
  selectedElements: Record<string, InjectorContractActionOutput>;
  deSelectedElements: Record<string, InjectorContractActionOutput>;
  open: boolean;
  onClose: (deselectAll?: boolean) => void;
}

const ThreatArsenalRunTestDrawer: FunctionComponent<Props> = ({ isExclusionMode, isOnlyOneItemSelected, selectedElements, deSelectedElements, open, onClose }) => {
  const { t } = useFormatter();

  const executionModes = [
    {
      icon: <MovieFilterOutlined fontSize="large" color="primary" />,
      title: t('Create a new scenario').toUpperCase(),
      description: t('Build a fully customized Scenario'),
      onClick: () => {},
      disabled: false
    },
    {
      icon: <AddCircleOutlined fontSize="large" color="primary" />,
      title: t('Add to an existing scenario').toUpperCase(),
      description: t('Easily insert new steps into an existing Scenario'),
      onClick: () => {},
      disabled: false
    },
    {
      icon: <Target fontSize="large" color={!isOnlyOneItemSelected ? "disabled" : "primary"} />,
      title: t('Run atomic test').toUpperCase(),
      description: t('Execute individually the selected actions immediately'),
      onClick: () => {},
      disabled: !isOnlyOneItemSelected
    }
  ];

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Choose Execution mode')}
    >
      <Stack flexDirection="column" alignItems="center">
        {executionModes.map((executionMode, index) =>
          <ThreatArsenalExecutionModeCardComponent key={`execution-mode-${index}`} executionMode={executionMode} />
        )}
      </Stack>
    </Drawer>
  );
};

export default ThreatArsenalRunTestDrawer;