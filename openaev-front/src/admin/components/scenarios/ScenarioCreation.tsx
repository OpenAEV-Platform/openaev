import { AutoAwesome, CheckCircleRounded, DashboardCustomizeOutlined, RadioButtonUncheckedRounded } from '@mui/icons-material';
import { Box, Card, CardActionArea, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, type ReactNode, useCallback, useState } from 'react';
import { useNavigate } from 'react-router';

import { type LoggedHelper } from '../../../actions/helper';
import { addScenario } from '../../../actions/scenarios/scenario-actions';
import ButtonCreate from '../../../components/common/ButtonCreate';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { SCENARIO_BASE_URL } from '../../../constants/BaseUrls';
import { useHelper } from '../../../store';
import { type PlatformSettings, type Scenario, type ScenarioInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EngineTypeSelection, { type EngineType } from '../common/EngineTypeSelection';
import EEChip from '../common/entreprise_edition/EEChip';
import ScenarioFormChaining from './ScenarioFormChaining';

const ScenarioCreation: FunctionComponent = () => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const [engineType, setEngineType] = useState<EngineType>(null);
  // Post-creation intent set by the two companion entries. "Generate with AI" (chained) marks the
  // new scenario to auto-open the AI builder drawer (?openAiBuilder); "Scenario assistant"
  // (time-based) marks it to open the guided assistant (?openScenarioAssistant). Both are simple
  // enabled/disabled toggles - the scenario is created normally, then the right surface opens on the
  // scenario page. They are mutually exclusive.
  const [openAiBuilderAfterCreate, setOpenAiBuilderAfterCreate] = useState(false);
  const [openAssistantAfterCreate, setOpenAssistantAfterCreate] = useState(false);
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const { settings: authSettings } = useAuth();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const dispatch = useAppDispatch();

  // The AI builder is driven by the XTM One orchestrator, so its entry is only offered when chaining
  // is on and XTM One is connected (and agentic AI is not disabled) - the same gate the entity-level
  // Autonomous attack action uses. EE is enforced when the operator actually toggles it on.
  const aiReady
    = authSettings.platform_xtm_one_configured === true
      && authSettings.filigran_chatbot_ai_cgu_status !== 'disabled';

  // A plain card selection is a manual build - clear both companion intents so re-picking a card
  // means "without the AI builder / assistant".
  const handleTypeSelected = useCallback((type: EngineType) => {
    setEngineType(type);
    setOpenAiBuilderAfterCreate(false);
    setOpenAssistantAfterCreate(false);
  }, []);

  const handleClose = () => {
    setOpen(false);
    setOpenAiBuilderAfterCreate(false);
    setOpenAssistantAfterCreate(false);
  };

  // "Generate with AI" toggle (Chained column): select the chained engine and mark the creation so
  // the new scenario auto-opens the AI builder drawer. Clicking again turns it back off. EE-gated.
  const toggleAiBuilderMode = () => {
    if (openAiBuilderAfterCreate) {
      setOpenAiBuilderAfterCreate(false);
      return;
    }
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Autonomous attack path'));
      openEnterpriseEditionDialog();
      return;
    }
    setEngineType('chaining');
    setOpenAiBuilderAfterCreate(true);
    setOpenAssistantAfterCreate(false);
  };

  // "Scenario assistant" toggle (Time-based column): select the time-based engine and mark the
  // creation so the new scenario lands directly in the guided assistant. Clicking again turns off.
  const toggleAssistantMode = () => {
    if (openAssistantAfterCreate) {
      setOpenAssistantAfterCreate(false);
      return;
    }
    setEngineType('time-based');
    setOpenAssistantAfterCreate(true);
    setOpenAiBuilderAfterCreate(false);
  };

  const onSubmit = (data: ScenarioInput, isScenarioAssistantChecked?: boolean) => {
    dispatch(addScenario({
      ...data,
      scenario_is_chaining: engineType === 'chaining',
    })).then(
      (result: {
        result: string;
        entities: { scenarios: Record<string, Scenario> };
      }) => {
        if (result.entities) {
          // Redirect to the new scenario, opening the requested companion surface. The AI builder
          // intent wins if somehow both are set; otherwise the assistant opens from the legacy form
          // checkbox or the time-based "Scenario assistant" entry.
          let query: string;
          if (openAiBuilderAfterCreate) {
            query = '?openAiBuilder=true';
          } else {
            const shouldOpenAssistant = isScenarioAssistantChecked || openAssistantAfterCreate;
            query = `?openScenarioAssistant=${shouldOpenAssistant}`;
          }
          navigate(`${SCENARIO_BASE_URL}/${result.result}${query}`);
          setOpen(false);
        }
      },
    );
  };

  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const initialValues: ScenarioInput = {
    scenario_name: '',
    scenario_category: 'attack-scenario',
    scenario_main_focus: 'incident-response',
    scenario_severity: 'high',
    scenario_default_kill_chain: '',
    scenario_subtitle: '',
    scenario_description: '',
    scenario_external_reference: '',
    scenario_external_url: '',
    scenario_tags: [],
    scenario_message_header: t('SIMULATION HEADER'),
    scenario_message_footer: t('SIMULATION FOOTER'),
    scenario_mail_from_name: settings.default_mailer_name ?? '',
    scenario_mails_reply_to: [settings.default_reply_to ?? ''],
  };
  const renderDrawerContent = (): ReactElement => {
    // "Generate with AI" entry, tied to the Chained engine: a toggle that marks the new scenario to
    // auto-open the AI builder drawer after creation. Offered only when XTM One is connected (EE is
    // enforced on toggle-on via toggleAiBuilderMode). Selected state highlights the card.
    const chainingFooter = aiReady
      ? (
          <Card
            variant="outlined"
            sx={{
              'borderColor': openAiBuilderAfterCreate ? theme.palette.ai.main : alpha(theme.palette.ai.main, 0.5),
              'backgroundColor': alpha(theme.palette.ai.main, openAiBuilderAfterCreate ? 0.14 : 0.06),
              '&:hover': { borderColor: theme.palette.ai.main },
            }}
          >
            <CardActionArea
              onClick={toggleAiBuilderMode}
              data-testid="scenario-generate-with-ai-toggle"
              sx={{
                height: '100%',
                padding: theme.spacing(1.5),
              }}
            >
              <Stack direction="row" spacing={1} alignItems="center">
                <AutoAwesome sx={{
                  color: theme.palette.ai.main,
                  flexShrink: 0,
                }}
                />
                <Box sx={{ flex: 1 }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography
                      variant="subtitle2"
                      sx={{
                        fontWeight: 'bold',
                        color: theme.palette.ai.main,
                      }}
                    >
                      {t('Generate with AI')}
                    </Typography>
                    {!isEnterpriseEdition && <EEChip />}
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    {t('Let the orchestrator design this chained scenario from an objective, templates, agents and a scope.')}
                  </Typography>
                </Box>
                {/* Selected-state affordance: a filled check when enabled, a faint empty ring when
                    not, so the toggle reads clearly at a glance (mirrors a checkbox on the right). */}
                {openAiBuilderAfterCreate
                  ? (
                      <CheckCircleRounded sx={{
                        color: theme.palette.ai.main,
                        flexShrink: 0,
                      }}
                      />
                    )
                  : (
                      <RadioButtonUncheckedRounded sx={{
                        color: alpha(theme.palette.ai.main, 0.4),
                        flexShrink: 0,
                      }}
                      />
                    )}
              </Stack>
            </CardActionArea>
          </Card>
        )
      : undefined;

    // "Scenario assistant" entry, tied to the Time-based engine: a toggle that marks the new
    // scenario to open the guided assistant (coverage matrix + inject generation) right after
    // creation. Selected state highlights the card.
    const timeBasedFooter = (
      <Card
        variant="outlined"
        sx={{
          'borderColor': openAssistantAfterCreate ? theme.palette.primary.main : alpha(theme.palette.primary.main, 0.5),
          'backgroundColor': alpha(theme.palette.primary.main, openAssistantAfterCreate ? 0.14 : 0.06),
          '&:hover': { borderColor: theme.palette.primary.main },
        }}
      >
        <CardActionArea
          onClick={toggleAssistantMode}
          data-testid="scenario-assistant-toggle"
          sx={{
            height: '100%',
            padding: theme.spacing(1.5),
          }}
        >
          <Stack direction="row" spacing={1} alignItems="center">
            <DashboardCustomizeOutlined sx={{
              color: theme.palette.primary.main,
              flexShrink: 0,
            }}
            />
            <Box sx={{ flex: 1 }}>
              <Typography
                variant="subtitle2"
                sx={{
                  fontWeight: 'bold',
                  color: theme.palette.primary.main,
                }}
              >
                {t('Scenario assistant')}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t('Build this time-based scenario with the guided assistant - coverage matrix and generated injects.')}
              </Typography>
            </Box>
            {/* Selected-state affordance: filled check when enabled, faint empty ring otherwise. */}
            {openAssistantAfterCreate
              ? (
                  <CheckCircleRounded sx={{
                    color: theme.palette.primary.main,
                    flexShrink: 0,
                  }}
                  />
                )
              : (
                  <RadioButtonUncheckedRounded sx={{
                    color: alpha(theme.palette.primary.main, 0.4),
                    flexShrink: 0,
                  }}
                  />
                )}
          </Stack>
        </CardActionArea>
      </Card>
    );

    // The companion entry (Generate with AI / Scenario assistant) is rendered full-width at the END
    // of the form once a mode is picked - not under a single engine card - so it reads as an action
    // on the whole scenario being created. Chained -> Generate with AI (when XTM One is ready);
    // time-based -> Scenario assistant.
    let footerSlot: ReactNode;
    if (engineType === 'chaining') {
      footerSlot = chainingFooter;
    } else if (engineType === 'time-based') {
      footerSlot = timeBasedFooter;
    }

    // The scenario type selection is always shown; picking one reveals the form, whose footer slot
    // carries the mode-appropriate companion toggle.
    return (
      <>
        <EngineTypeSelection
          selected={engineType}
          onSelect={handleTypeSelected}
        />
        {engineType !== null && (
          <ScenarioFormChaining
            onSubmit={onSubmit}
            initialValues={initialValues}
            handleClose={handleClose}
            isChaining={engineType === 'chaining'}
            footerSlot={footerSlot}
          />
        )}
      </>
    );
  };
  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new scenario')}
      >
        {renderDrawerContent}
      </Drawer>
    </>
  );
};
export default ScenarioCreation;
