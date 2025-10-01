import { HelpOutlined } from '@mui/icons-material';
import { TabContext, TabPanel } from '@mui/lab';
import { Avatar, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchInject } from '../../../../actions/Inject';
import { type InjectOutputType, type InjectStore } from '../../../../actions/injects/Inject';
import { fetchDocumentsPayloadByInject } from '../../../../actions/injects/inject-action';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { useHelper } from '../../../../store';
import {
  type Article,
  type AttackPattern, type Document,
  type Inject,
  type InjectInput,
  type KillChainPhase, type Variable,
} from '../../../../utils/api-types';
import { type InjectorContractConverted } from '../../../../utils/api-types-custom';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, INHERITED_CONTEXT, SUBJECTS } from '../../../../utils/permissions/types';
import { arrayToRecord, isNotEmptyField } from '../../../../utils/utils';
import PayloadComponent from '../../payloads/PayloadComponent';
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

const useStyles = makeStyles()(() => ({ tabPanel: { padding: 0 } }));

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
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const [isInjectLoading, setIsInjectLoading] = useState(true);

  const { permissions, inherited_context } = useContext(PermissionsContext);
  const ability = useContext(AbilityContext);

  // Setup tabs
  const [availableTabs, setAvailableTabs] = useState<string[]>(['Inject details', 'Logical chains']);
  const [activeTab, setActiveTab] = useState<string>(availableTabs[0]);

  // Fetching data
  const { inject }: { inject: InjectStore } = useHelper((helper: InjectHelper) => ({ inject: helper.getInject(injectId) }));
  const contractPayload = inject?.inject_injector_contract?.injector_contract_payload;
  const injectorContract = inject?.inject_injector_contract;
  const [documentsMap, setDocumentsMap] = useState<Record<string, Document> | null>(null);

  useDataLoader(() => {
    setIsInjectLoading(true);
    dispatch(fetchInject(injectId)).then(() => {
      const payloadId = inject?.inject_injector_contract?.injector_contract_payload?.payload_id;
      if (payloadId) {
        setAvailableTabs(['Inject details', 'Payload info', 'Logical chains']);
      }
      setIsInjectLoading(false);
    });
  });

  // Selection
  const handleTabChange = (_: SyntheticEvent, newValue: string) => {
    setActiveTab(newValue);

    if (newValue === 'Payload info' && !documentsMap) {
      fetchDocumentsPayloadByInject(injectId, contractPayload?.payload_id)
        .then(documents => setDocumentsMap(arrayToRecord<Document, 'document_id'>(documents, 'document_id')));
    }
  };

  const [injectorContractContent, setInjectorContractContent] = useState<InjectorContractConverted['convertedContent']>();
  useEffect(() => {
    if (inject?.inject_injector_contract?.convertedContent) {
      setInjectorContractContent(inject.inject_injector_contract?.convertedContent);
    }
  }, [inject]);

  const getInjectHeaderTitle = (): string => {
    if (injectorContract?.injector_contract_needs_executor && inject?.inject_attack_patterns?.length !== 0) {
      return `${inject?.inject_kill_chain_phases?.map((value: KillChainPhase) => value.phase_name)?.join(', ')} / ${inject?.inject_attack_patterns?.map((value: AttackPattern) => value.attack_pattern_external_id)?.join(', ')}`;
    }
    if (injectorContract?.injector_contract_needs_executor) {
      return t('TTP Unknown');
    }
    return injectorContract?.injector_contract_injector_type_name ? t(injectorContract?.injector_contract_injector_type_name) : '';
  };

  const injectFormContent = (
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
            platform => (
              <PlatformIcon
                key={platform}
                width={20}
                platform={platform}
                marginRight={theme.spacing(2)}
              />
            ),
          )}
        </div>
      )}
      content={inject?.inject_title}
    />

  );
  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Update the inject')}
      disableEnforceFocus
      containerStyle={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
    >
      <TabContext value={activeTab}>
        {!isAtomic && (
          <Tabs value={activeTab} onChange={handleTabChange} variant="fullWidth">
            {availableTabs.map(tab => (
              <Tab key={tab} label={t(tab)} value={tab} />
            ))}
          </Tabs>
        )}
        {/* Inject details */}
        <TabPanel value="Inject details" keepMounted className={classes.tabPanel}>
          {injectFormContent}
          {!isInjectLoading && (
            <InjectForm
              handleClose={handleClose}
              disabled={
                !injectorContractContent
                || permissions.readOnly
                || (inherited_context === INHERITED_CONTEXT.NONE
                  && ability.cannot(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId))
              }
              isAtomic={isAtomic}
              defaultInject={inject}
              injectorContractContent={injectorContractContent}
              onSubmitInject={(data: InjectInput) => onUpdateInject(data as Inject)}
              articlesFromExerciseOrScenario={articlesFromExerciseOrScenario}
              uriVariable={uriVariable}
              variablesFromExerciseOrScenario={variablesFromExerciseOrScenario}
            />
          )}
        </TabPanel>

        {/* Payload info */}
        {contractPayload && !isAtomic && (
          <TabPanel value="Payload info" keepMounted className={classes.tabPanel}>
            {!isInjectLoading && (
              <PayloadComponent
                documentsMap={documentsMap}
                selectedPayload={contractPayload}
              />
            )}
          </TabPanel>
        )}

        {/* Logical chains */}
        <TabPanel value="Logical chains" keepMounted className={classes.tabPanel}>
          {injectFormContent}
          {!isInjectLoading && !isAtomic && (
            <UpdateInjectLogicalChains
              inject={inject}
              handleClose={handleClose}
              onUpdateInject={massUpdateInject}
              injects={injects}
              isDisabled={
                !permissions.canManage
                && ability.cannot(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId)
              }
            />
          )}
        </TabPanel>
      </TabContext>
    </Drawer>
  );
};

export default UpdateInject;
