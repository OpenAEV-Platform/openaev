import {
  AddOutlined,
  AutoAwesomeOutlined,
  ChevronLeft,
  RemoveOutlined,
  RouteOutlined,
} from '@mui/icons-material';
import { alpha, Box, Button, IconButton, Paper, SvgIcon, TextField, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { findEndpoints } from '../../../../../actions/assets/endpoint-actions';
import { fetchAttackPatterns } from '../../../../../actions/AttackPattern';
import { playInjectsAssistantForScenario } from '../../../../../actions/Inject';
import { fetchInjectorsContracts } from '../../../../../actions/InjectorContracts';
import { fetchKillChainPhases } from '../../../../../actions/KillChainPhase';
import LoaderDialog from '../../../../../components/common/loader/LoaderDialog';
import { useFormatter } from '../../../../../components/i18n';
import SearchInput from '../../../../../components/SearchFilter';
import {
  type EndpointOutput,
  type InjectAssistantInput,
  type Scenario,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useAI from '../../../../../utils/hooks/useAI';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useEnterpriseEdition from '../../../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext, Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import FiligranAiCguDialog from '../../../ariane/FiligranAiCguDialog';
import AssetGroupPopover from '../../../assets/asset_groups/AssetGroupPopover';
import AssetGroupsList from '../../../assets/asset_groups/AssetGroupsList';
import AssetPopover from '../../../assets/endpoints/AssetPopover';
import AssetsList from '../../../assets/endpoints/AssetsList';
import EEChip from '../../../common/entreprise_edition/EEChip';
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

// Full-page Scenario assistant: pick targets, cover the ATT&CK matrix (manually
// or with the XTM One AI assistant), and generate injects. Replaces the former
// cramped drawer + nested TTP drawer.
const ScenarioAssistant: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);
  const { enabled: aiEnabled, isCguPending } = useAI();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const listUrl = `/admin/scenarios/${scenarioId}/injects`;
  const ai = theme.palette.ai.main;

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
  const [onlyWithPayloads, setOnlyWithPayloads] = useState(false);

  // -- Dialogs
  const [openAiDialog, setOpenAiDialog] = useState(false);
  const [openValidateTermsOfUse, setOpenValidateTermsOfUse] = useState(false);
  const [openLoaderDialog, setOpenLoaderDialog] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);

  // -- Resolve endpoint objects for the selected asset ids (for the list view)
  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
  useEffect(() => {
    if (assetIds.length > 0 && ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
      findEndpoints(assetIds).then(result => setEndpoints(result.data));
    } else {
      setEndpoints([]);
    }
  }, [assetIds]);

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

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      paddingBottom: canSubmit ? 12 : 4,
    }}
    >
      {/* Hero band (AI-accented, distinct from the standard scenario hero) */}
      <Paper
        variant="outlined"
        sx={{
          position: 'relative',
          overflow: 'hidden',
          borderRadius: 1,
          padding: {
            xs: 2,
            md: 3,
          },
          borderColor: alpha(ai, 0.3),
          background: `linear-gradient(135deg, ${alpha(ai, 0.14)}, transparent 60%)`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          flexWrap: 'wrap',
        }}
      >
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            top: -100,
            right: -40,
            width: 260,
            height: 260,
            borderRadius: '50%',
            background: alpha(ai, 0.16),
            filter: 'blur(70px)',
            pointerEvents: 'none',
          }}
        />
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          minWidth: 0,
        }}
        >
          <Box sx={{
            width: 52,
            height: 52,
            borderRadius: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
            color: ai,
            backgroundColor: alpha(ai, 0.16),
            border: `1px solid ${alpha(ai, 0.4)}`,
          }}
          >
            <AutoAwesomeOutlined />
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography
              variant="h1"
              sx={{
                margin: 0,
                fontSize: 22,
              }}
            >
              {t('Scenario assistant')}
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t('Pick your targets, cover the ATT&CK matrix, and auto-generate injects.')}
            </Typography>
          </Box>
        </Box>
        <Button
          variant="outlined"
          color="inherit"
          size="small"
          startIcon={<ChevronLeft />}
          onClick={() => navigate(listUrl)}
          sx={{ borderColor: theme.palette.divider }}
        >
          {t('Back')}
        </Button>
      </Paper>

      {/* Two-column body: config rail + matrix */}
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
        {/* Left rail (sticky): targets + generation options */}
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
              <RouteOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
              <Typography sx={sectionLabelSx}>{t('Target')}</Typography>
            </Box>

            <Box>
              <Typography variant="h3" sx={{ marginBottom: 0.5 }}>{t('Assets')}</Typography>
              <AssetsList
                endpoints={endpoints}
                renderActions={asset => (
                  <AssetPopover
                    inline
                    endpoint={asset}
                    removeFromContextLabel="Remove"
                    onRemoveFromContext={assetId => setAssetIds(assetIds.filter(id => id !== assetId))}
                  />
                )}
              />
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
              <AssetGroupsList
                assetGroupIds={assetGroupIds}
                renderActions={assetGroup => (
                  <AssetGroupPopover
                    assetGroup={assetGroup}
                    inline
                    onRemoveAssetGroupFromList={assetGroupId => setAssetGroupIds(assetGroupIds.filter(id => id !== assetGroupId))}
                    removeAssetGroupFromListMessage="Remove"
                    actions={['remove']}
                  />
                )}
              />
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
              <AutoAwesomeOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
              <Typography sx={sectionLabelSx}>{t('Generation')}</Typography>
            </Box>

            {aiEnabled && (
              <Button
                variant="outlined"
                fullWidth
                onClick={onUseAiClick}
                startIcon={<SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />}
                endIcon={isEnterpriseEdition ? undefined : <EEChip />}
                sx={{
                  'justifyContent': 'flex-start',
                  'color': ai,
                  'borderColor': alpha(ai, 0.5),
                  'backgroundColor': alpha(ai, 0.06),
                  'textTransform': 'none',
                  'fontWeight': 600,
                  '&:hover': {
                    borderColor: ai,
                    backgroundColor: alpha(ai, 0.12),
                  },
                }}
              >
                {t('Suggest TTPs with XTM One')}
              </Button>
            )}

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

        {/* Right column: ATT&CK matrix */}
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
            <Typography sx={sectionLabelSx}>{t('Scenario coverage')}</Typography>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
            >
              <SearchInput onChange={value => setSearch(value ?? '')} placeholder={`${t('Search a technique')}...`} />
              <Tooltip title={t('Only techniques with available injects')}>
                <Button
                  size="small"
                  variant={onlyWithPayloads ? 'contained' : 'outlined'}
                  color={onlyWithPayloads ? 'primary' : 'inherit'}
                  onClick={() => setOnlyWithPayloads(v => !v)}
                  sx={{
                    textTransform: 'none',
                    borderColor: onlyWithPayloads ? undefined : theme.palette.divider,
                  }}
                >
                  {t('With injects')}
                </Button>
              </Tooltip>
            </Box>
          </Box>
          <AttackMatrixSelector
            selectedIds={attackPatternIds}
            onToggle={toggleTtp}
            search={search}
            onlyWithPayloads={onlyWithPayloads}
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
            color="secondary"
            startIcon={<AutoAwesomeOutlined fontSize="small" />}
            onClick={onSubmit}
            sx={{
              borderRadius: 999,
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
