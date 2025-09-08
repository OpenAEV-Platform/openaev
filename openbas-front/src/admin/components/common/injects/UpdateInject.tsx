import { HelpOutlined } from '@mui/icons-material';
import { Avatar, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useContext, useEffect, useRef, useState } from 'react';

import { fetchInject } from '../../../../actions/Inject';
import { type InjectOutputType, type InjectStore } from '../../../../actions/injects/Inject';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { useHelper } from '../../../../store';
import {
  type Article,
  type AttackPattern,
  type Inject,
  type InjectInput,
  type KillChainPhase, type Variable,
} from '../../../../utils/api-types';
import { type InjectorContractConverted } from '../../../../utils/api-types-custom';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isNotEmptyField } from '../../../../utils/utils';
import { PermissionsContext } from '../Context';
import InjectForm from './form/InjectForm';
import InjectCardComponent from './InjectCardComponent';
import InjectIcon from './InjectIcon';
import UpdateInjectLogicalChains from './UpdateInjectLogicalChains';
interface Props {
  open: boolean;
  handleClose: () => void;
  onUpdateInject: (data: Inject) => Promise<void>;
  massUpdateInject?: (data: Inject[]) => Promise<void>;
  injectId: string;
  isAtomic?: boolean;
  injects?: InjectOutputType[];
  articlesFromExerciseOrScenario?: Article[];
  uriVariable?: string;
  variablesFromExerciseOrScenario?: Variable[];
}

const UpdateInject: React.FC<Props> = ({
  open,
  handleClose,
  onUpdateInject,
  massUpdateInject,
  injectId,
  isAtomic = false,
  injects,
  articlesFromExerciseOrScenario = [],
  uriVariable = '',
  variablesFromExerciseOrScenario = [],
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const drawerRef = useRef(null);
  const [availableTabs] = useState<string[]>(['Inject details', 'Logical chains']);
  const [activeTab, setActiveTab] = useState<null | string>(availableTabs[0]);
  const [isInjectLoading, setIsInjectLoading] = useState(true);
  const { permissions } = useContext(PermissionsContext);
  const ability = useContext(AbilityContext);

  // Fetching data
  const { inject }: { inject: InjectStore } = useHelper((helper: InjectHelper) => ({ inject: helper.getInject(injectId) }));

  useDataLoader(() => {
    setIsInjectLoading(true);
    dispatch(fetchInject(injectId)).then(() => setIsInjectLoading(false));
  });

  // Selection
  const handleTabChange = (_: SyntheticEvent, newValue: string) => {
    setActiveTab(newValue);
  };

  const [injectorContractContent, setInjectorContractContent] = useState<InjectorContractConverted['convertedContent']>();
  useEffect(() => {
    if (inject?.inject_injector_contract?.convertedContent) {
      setInjectorContractContent(inject.inject_injector_contract?.convertedContent);
    }
  }, [inject]);

  const contractPayload = inject?.inject_injector_contract?.injector_contract_payload;
  const injectorContract = inject?.inject_injector_contract;
  const getInjectHeaderTitle = (): string => {
    if (injectorContract?.injector_contract_needs_executor && inject?.inject_attack_patterns?.length !== 0) {
      return `${inject?.inject_kill_chain_phases?.map((value: KillChainPhase) => value.phase_name)?.join(', ')} / ${inject?.inject_attack_patterns?.map((value: AttackPattern) => value.attack_pattern_external_id)?.join(', ')}`;
    }
    if (injectorContract?.injector_contract_needs_executor) {
      return t('TTP Unknown');
    }
    return injectorContract?.injector_contract_injector_type_name ? t(injectorContract?.injector_contract_injector_type_name) : '';
  };
  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Update the inject')}
      PaperProps={{ ref: drawerRef }}
      disableEnforceFocus
      containerStyle={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
    >
      <>
        {!isAtomic && (
          <Tabs value={activeTab} onChange={handleTabChange} variant="fullWidth">
            {availableTabs.map((tab) => {
              return (
                <Tab key={tab} label={tab} value={tab} />
              );
            })}
          </Tabs>
        )}
        <InjectCardComponent
          avatar={injectorContractContent
            ? (
                <InjectIcon
                  type={contractPayload ? (contractPayload.payload_collector_type ?? contractPayload.payload_type) : injectorContract?.injector_contract_injector_type}
                  isPayload={isNotEmptyField(contractPayload?.payload_collector_type ?? contractPayload?.payload_type)}
                />
              ) : (
                <Avatar sx={{
                  width: 24,
                  height: 24,
                }}
                >
                  <HelpOutlined />
                </Avatar>
              )}
          title={getInjectHeaderTitle()}
          action={(
            <div style={{
              display: 'flex',
              alignItems: 'center',
            }}
            >
              {inject?.inject_injector_contract?.injector_contract_platforms?.map(
                platform => <PlatformIcon key={platform} width={20} platform={platform} marginRight={theme.spacing(2)} />,
              )}
            </div>
          )}
          content={inject?.inject_title}
        />

        {!isInjectLoading && (isAtomic || activeTab === 'Inject details') && (
          <InjectForm
            handleClose={handleClose}
            openDetails
            disabled={!injectorContractContent || permissions.canManage || ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId)}
            drawerRef={drawerRef}
            isAtomic={isAtomic}
            defaultInject={inject}
            injectorContractContent={injectorContractContent}
            onSubmitInject={(data: InjectInput) => onUpdateInject(data as Inject)}
            articlesFromExerciseOrScenario={articlesFromExerciseOrScenario}
            uriVariable={uriVariable}
            variablesFromExerciseOrScenario={variablesFromExerciseOrScenario}
          />
        )}
        {(!isInjectLoading && !isAtomic && activeTab === 'Logical chains') && (
          <UpdateInjectLogicalChains
            inject={inject}
            handleClose={handleClose}
            onUpdateInject={massUpdateInject}
            injects={injects}
            isDisabled={!permissions.canManage && ability.cannot(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId)}
          />
        )}
      </>
    </Drawer>
  );
};

export default UpdateInject;
