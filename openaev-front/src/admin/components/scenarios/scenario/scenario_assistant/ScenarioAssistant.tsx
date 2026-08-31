import {
  AddOutlined,
  ArrowBackOutlined,
  AutoAwesomeOutlined,
  CloseOutlined,
  DevicesOtherOutlined,
  RemoveOutlined,
  TrackChangesOutlined,
  TuneOutlined,
} from '@mui/icons-material';
import {
  alpha,
  Box,
  Button,
  IconButton,
  Paper,
  SvgIcon,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { findAssetGroups } from '../../../../../actions/asset_groups/assetgroup-action';
import { findEndpoints } from '../../../../../actions/assets/endpoint-actions';
import { fetchAttackPatterns } from '../../../../../actions/AttackPattern';
import { playInjectsAssistantForScenario } from '../../../../../actions/Inject';
import { fetchInjectorsContracts } from '../../../../../actions/InjectorContracts';
import { fetchKillChainPhases } from '../../../../../actions/KillChainPhase';
import LoaderDialog from '../../../../../components/common/loader/LoaderDialog';
import { useFormatter } from '../../../../../components/i18n';
import PlatformIcon from '../../../../../components/PlatformIcon';
import SearchInput from '../../../../../components/SearchFilter';
import {
  type AssetGroupOutput,
  type EndpointOutput,
  type InjectAssistantInput,
  type Scenario,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useAI from '../../../../../utils/hooks/useAI';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useEnterpriseEdition from '../../../../../utils/hooks/useEnterpriseEdition';
import { Can, useAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import FiligranAiCguDialog from '../../../ariane/FiligranAiCguDialog';
import EEChip from '../../../common/entreprise_edition/EEChip';
import KillChainSelect from '../../../common/filters/KillChainSelect';
import useKillChains from '../../../common/filters/useKillChains';
import InjectAddAssetGroups from '../../../simulations/simulation/injects/asset_groups/InjectAddAssetGroups';
import InjectAddEndpoints from '../../../simulations/simulation/injects/endpoints/InjectAddEndpoints';
import AttackMatrixSelector from './AttackMatrixSelector';
import AttackPatternAIAssistantDialog from './AttackPatternAIAssistantDialog';

const MIN_INJECTS_BY_TTP = 1;
const MAX_INJECTS_BY_TTP = 5;

const sectionLabelSx = {
  fontFamily: '"Geologica", sans-serif',
  fontWeight: 600,
  fontSize: 11,
  letterSpacing: '0.12em',
  textTransform: 'uppercase' as const,
  color: 'text.secondary',
};

// Compact target row: icon + name + remove. The standard list fragments
// (platform / tags / type columns) don't fit the narrow rail, so targets render
// as minimal rows here.
const TargetRow: FunctionComponent<{
  icon: ReactNode;
  label: string;
  removeLabel: string;
  onRemove: () => void;
}> = ({ icon, label, removeLabel, onRemove }) => {
  const theme = useTheme();
  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1,
      height: 36,
      paddingInline: 1,
      borderRadius: 1,
      border: `1px solid ${theme.palette.divider}`,
      backgroundColor: theme.palette.background.default,
    }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        flexShrink: 0,
      }}
      >
        {icon}
      </Box>
      <Typography sx={{
        fontSize: 13,
        flex: 1,
        minWidth: 0,
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
      }}
      >
        {label}
      </Typography>
      <Tooltip title={removeLabel}>
        <IconButton size="small" aria-label={removeLabel} onClick={onRemove}>
          <CloseOutlined sx={{ fontSize: 16 }} />
        </IconButton>
      </Tooltip>
    </Box>
  );
};

// Full-page Scenario assistant: pick targets, cover the attack matrix (manually
// or with the XTM One AI assistant), and generate injects.
const ScenarioAssistant: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const ability = useAbility();
  const { enabled: aiEnabled, isCguPending } = useAI();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const listUrl = `/admin/scenarios/${scenarioId}/injects`;

  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
    dispatch(fetchKillChainPhases());
    dispatch(fetchInjectorsContracts());
  });

  // -- Form state
  const [assetIds, setAssetIds] = useState<string[]>([]);
  const [assetGroupIds, setAssetGroupIds] = useState<string[]>([]);
  const [attackPatternIds, setAttackPatternIds] = useState<string[]>([]);
  const [injectsByTtp, setInjectsByTtp] = useState<number>(1);
  const [showErrors, setShowErrors] = useState(false);
  const [search, setSearch] = useState('');
  const [onlyWithArsenal, setOnlyWithArsenal] = useState(false);

  // -- Kill chain switcher (the assistant supports every kill chain, not only ATT&CK)
  const { killChains, activeKillChain, selectKillChain } = useKillChains();

  // -- Dialogs
  const [openAiDialog, setOpenAiDialog] = useState(false);
  const [openValidateTermsOfUse, setOpenValidateTermsOfUse] = useState(false);
  const [openLoaderDialog, setOpenLoaderDialog] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);

  // -- Resolve endpoint / asset group objects for the compact target rows
  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
  useEffect(() => {
    if (assetIds.length > 0 && ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
      findEndpoints(assetIds).then(result => setEndpoints(result.data));
    } else {
      setEndpoints([]);
    }
  }, [assetIds]);
  const [assetGroups, setAssetGroups] = useState<AssetGroupOutput[]>([]);
  useEffect(() => {
    if (assetGroupIds.length > 0 && ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
      findAssetGroups(assetGroupIds).then(result => setAssetGroups(result.data));
    } else {
      setAssetGroups([]);
    }
  }, [assetGroupIds]);

  const hasTargets = assetIds.length > 0 || assetGroupIds.length > 0;
  const hasTtps = attackPatternIds.length > 0;
  const canSubmit = hasTargets && hasTtps;
  const estimatedInjects = attackPatternIds.length * injectsByTtp;

  const toggleTtp = (attackPatternId: string) => {
    setAttackPatternIds(prev => (prev.includes(attackPatternId)
      ? prev.filter(id => id !== attackPatternId)
      : [...prev, attackPatternId]));
  };

  const onUseAiClick = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('XTM One AI'));
      openEnterpriseEditionDialog();
    } else if (isCguPending) {
      setOpenValidateTermsOfUse(true);
    } else {
      setOpenAiDialog(true);
    }
  };

  const onAiAttackPatternIdsFind = (ids: string[]) => {
    // Union with the current manual selection so AI augments rather than resets.
    setAttackPatternIds(prev => Array.from(new Set([...prev, ...ids])));
    setOpenAiDialog(false);
  };

  const onSubmit = () => {
    if (!canSubmit) {
      setShowErrors(true);
      return;
    }
    const input: InjectAssistantInput = {
      asset_ids: assetIds,
      asset_group_ids: assetGroupIds,
      attack_pattern_ids: attackPatternIds,
      inject_by_ttp_number: injectsByTtp,
    };
    setIsGenerating(true);
    setOpenLoaderDialog(true);
    playInjectsAssistantForScenario(scenarioId, input)
      .then(() => setIsGenerating(false))
      .catch(() => setOpenLoaderDialog(false));
  };

  // AI gradient, aligned with the Ask Ariane top-bar button: borderless,
  // gradient-painted label + AI-colored icon, subtle AI-tinted hover.
  const aiGradient = `linear-gradient(90deg, ${theme.palette.ai.light} 0%, ${theme.palette.ai.main} 100%)`;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      paddingBottom: canSubmit ? 12 : 4,
    }}
    >
      {/* Compact header: back to the injects list + page title (no hero band) */}
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        <Tooltip title={t('Back')}>
          <IconButton onClick={() => navigate(listUrl)} aria-label={t('Back')} size="small">
            <ArrowBackOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Typography variant="h1" sx={{ margin: 0 }}>
          {t('Scenario assistant')}
        </Typography>
        {aiEnabled && (
          <Button
            variant="text"
            onClick={onUseAiClick}
            startIcon={(
              <SvgIcon
                component={LogoXtmOneIcon}
                inheritViewBox
                sx={{
                  fontSize: '20px !important',
                  color: theme.palette.ai.main,
                }}
              />
            )}
            endIcon={!isEnterpriseEdition ? <span><EEChip /></span> : undefined}
            sx={{
              'marginLeft': 'auto',
              'height': 36,
              'paddingInline': 1.5,
              'borderRadius': 1,
              'fontWeight': 600,
              'whiteSpace': 'nowrap',
              '&:hover': { backgroundColor: alpha(theme.palette.ai.main, 0.15) },
              '& .assistant-ai-label': {
                background: aiGradient,
                backgroundClip: 'text',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              },
              '& .MuiButton-startIcon': { marginRight: '6px' },
            }}
          >
            <span className="assistant-ai-label">{t('Suggest TTPs with XTM One')}</span>
          </Button>
        )}
      </Box>

      {/* Two-column body: configuration rail + matrix */}
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: {
          xs: '1fr',
          md: 'minmax(320px, 360px) 1fr',
        },
        gap: 2,
        alignItems: 'start',
      }}
      >
        {/* Left rail (sticky): targets + configuration */}
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          position: {
            xs: 'static',
            md: 'sticky',
          },
          top: theme.spacing(2),
        }}
        >
          <Paper
            variant="outlined"
            sx={{
              padding: 2,
              borderRadius: 1,
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
            }}
          >
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
            >
              <TrackChangesOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
              <Typography sx={sectionLabelSx}>{t('Target')}</Typography>
            </Box>

            <Box>
              <Typography variant="h3" sx={{ marginBottom: 0.5 }}>{t('Assets')}</Typography>
              {endpoints.length > 0 && (
                <Box sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 0.5,
                  marginBottom: 0.5,
                }}
                >
                  {endpoints.map(endpoint => (
                    <TargetRow
                      key={endpoint.asset_id}
                      icon={endpoint.endpoint_platform
                        ? <PlatformIcon platform={endpoint.endpoint_platform} width={16} />
                        : <DevicesOtherOutlined color="primary" sx={{ fontSize: 16 }} />}
                      label={endpoint.asset_name}
                      removeLabel={t('Remove')}
                      onRemove={() => setAssetIds(assetIds.filter(id => id !== endpoint.asset_id))}
                    />
                  ))}
                </Box>
              )}
              <Can I={ACTIONS.ACCESS} a={SUBJECTS.ASSETS}>
                <InjectAddEndpoints
                  endpointIds={assetIds}
                  onSubmit={setAssetIds}
                  errorLabel={showErrors && !hasTargets ? t('Should have at least one asset or one asset group') : null}
                />
              </Can>
            </Box>

            <Box>
              <Typography variant="h3" sx={{ marginBottom: 0.5 }}>{t('Asset groups')}</Typography>
              {assetGroups.length > 0 && (
                <Box sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 0.5,
                  marginBottom: 0.5,
                }}
                >
                  {assetGroups.map(assetGroup => (
                    <TargetRow
                      key={assetGroup.asset_group_id}
                      icon={<SelectGroup color="primary" sx={{ fontSize: 16 }} />}
                      label={assetGroup.asset_group_name}
                      removeLabel={t('Remove')}
                      onRemove={() => setAssetGroupIds(assetGroupIds.filter(id => id !== assetGroup.asset_group_id))}
                    />
                  ))}
                </Box>
              )}
              <Can I={ACTIONS.ACCESS} a={SUBJECTS.ASSETS}>
                <InjectAddAssetGroups
                  assetGroupIds={assetGroupIds}
                  onSubmit={setAssetGroupIds}
                  errorLabel={showErrors && !hasTargets ? t('Should have at least one asset or one asset group') : null}
                />
              </Can>
            </Box>
          </Paper>

          <Paper
            variant="outlined"
            sx={{
              padding: 2,
              borderRadius: 1,
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
            }}
          >
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
            >
              <TuneOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
              <Typography sx={sectionLabelSx}>{t('Configuration')}</Typography>
            </Box>

            <Box>
              <Typography variant="h3" sx={{ marginBottom: 0.5 }}>{t('Number of injects by TTP')}</Typography>
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
              >
                <IconButton
                  size="small"
                  aria-label={t('Decrease')}
                  disabled={injectsByTtp <= MIN_INJECTS_BY_TTP}
                  onClick={() => setInjectsByTtp(v => Math.max(MIN_INJECTS_BY_TTP, v - 1))}
                  sx={{ border: `1px solid ${theme.palette.divider}` }}
                >
                  <RemoveOutlined fontSize="small" />
                </IconButton>
                <TextField
                  value={injectsByTtp}
                  size="small"
                  type="number"
                  onChange={(event) => {
                    const next = Number(event.target.value);
                    if (Number.isNaN(next)) return;
                    setInjectsByTtp(Math.min(MAX_INJECTS_BY_TTP, Math.max(MIN_INJECTS_BY_TTP, next)));
                  }}
                  slotProps={{
                    htmlInput: {
                      min: MIN_INJECTS_BY_TTP,
                      max: MAX_INJECTS_BY_TTP,
                      style: { textAlign: 'center' },
                    },
                  }}
                  sx={{ width: 72 }}
                />
                <IconButton
                  size="small"
                  aria-label={t('Increase')}
                  disabled={injectsByTtp >= MAX_INJECTS_BY_TTP}
                  onClick={() => setInjectsByTtp(v => Math.min(MAX_INJECTS_BY_TTP, v + 1))}
                  sx={{ border: `1px solid ${theme.palette.divider}` }}
                >
                  <AddOutlined fontSize="small" />
                </IconButton>
              </Box>
            </Box>

            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 0.5,
              padding: 1.5,
              borderRadius: 1,
              backgroundColor: alpha(theme.palette.primary.main, 0.06),
              border: `1px solid ${alpha(theme.palette.primary.main, 0.15)}`,
            }}
            >
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {t('Selected TTPs')}
              </Typography>
              <Typography sx={{
                fontFamily: '"Geologica", sans-serif',
                fontSize: 20,
                fontWeight: 500,
                color: hasTtps ? 'text.primary' : 'text.disabled',
              }}
              >
                {attackPatternIds.length}
              </Typography>
              {showErrors && !hasTtps && (
                <Typography variant="caption" sx={{ color: 'error.main' }}>
                  {t('Should not be empty')}
                </Typography>
              )}
            </Box>
          </Paper>
        </Box>

        {/* Right column: attack matrix (any kill chain) */}
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderRadius: 1,
            display: 'flex',
            flexDirection: 'column',
            gap: 1.5,
            minWidth: 0,
          }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 1,
            flexWrap: 'wrap',
          }}
          >
            <KillChainSelect
              killChains={killChains}
              value={activeKillChain}
              onChange={selectKillChain}
            />
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
            >
              <SearchInput onChange={value => setSearch(value ?? '')} placeholder={`${t('Search a technique')}...`} />
              <ToggleButtonGroup
                size="small"
                exclusive
                value={onlyWithArsenal ? 'arsenal' : 'all'}
                onChange={(_, value) => {
                  if (value) setOnlyWithArsenal(value === 'arsenal');
                }}
              >
                <ToggleButton
                  value="all"
                  sx={{
                    textTransform: 'none',
                    paddingInline: 1.5,
                  }}
                >
                  {t('All techniques')}
                </ToggleButton>
                <ToggleButton
                  value="arsenal"
                  sx={{
                    textTransform: 'none',
                    paddingInline: 1.5,
                  }}
                >
                  {t('With actions')}
                </ToggleButton>
              </ToggleButtonGroup>
            </Box>
          </Box>
          <AttackMatrixSelector
            selectedIds={attackPatternIds}
            onToggle={toggleTtp}
            search={search}
            killChain={activeKillChain}
            onlyWithArsenal={onlyWithArsenal}
          />
        </Paper>
      </Box>

      {/* Floating create bar */}
      {canSubmit && (
        <Box
          role="region"
          aria-label={t('Generation toolbar')}
          sx={{
            position: 'fixed',
            left: '50%',
            bottom: 24,
            transform: 'translateX(-50%)',
            zIndex: theme.zIndex.snackbar,
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            paddingBlock: 1.25,
            paddingInline: 2,
            borderRadius: 999,
            backgroundColor: alpha(theme.palette.background.paper, 0.92),
            border: `1px solid ${theme.palette.divider}`,
            boxShadow: `0 24px 64px -24px ${alpha('#000', 0.6)}, 0 0 0 1px ${alpha(theme.palette.primary.main, 0.12)}`,
            backdropFilter: 'blur(12px)',
            maxWidth: 'calc(100vw - 48px)',
          }}
        >
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            paddingRight: 1,
            borderRight: `1px solid ${theme.palette.divider}`,
          }}
          >
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {attackPatternIds.length === 1
                ? t('1 TTP selected')
                : t('{count} TTPs selected', { count: attackPatternIds.length })}
            </Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {t('~{count} injects', { count: estimatedInjects })}
            </Typography>
          </Box>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AutoAwesomeOutlined fontSize="small" />}
            onClick={onSubmit}
            sx={{
              borderRadius: 1,
              textTransform: 'none',
              fontWeight: 600,
              paddingInline: 2,
            }}
          >
            {t('Create injects')}
          </Button>
        </Box>
      )}

      <AttackPatternAIAssistantDialog
        open={openAiDialog}
        onClose={() => setOpenAiDialog(false)}
        onAttackPatternIdsFind={onAiAttackPatternIdsFind}
      />
      {openValidateTermsOfUse && (
        <FiligranAiCguDialog
          open={openValidateTermsOfUse}
          onClose={() => setOpenValidateTermsOfUse(false)}
        />
      )}
      <LoaderDialog
        open={openLoaderDialog}
        isSubmitting={isGenerating}
        loadMessage={t('Injects generation in progress...')}
        successMessage={t('Injects successfully generated.')}
        redirectButtonLabel={t('Access these injects')}
        redirectLink={listUrl}
        onClose={() => setOpenLoaderDialog(false)}
      />
    </Box>
  );
};

export default ScenarioAssistant;
