import { CheckCircleOutlined, RadioButtonUncheckedOutlined, SearchOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  ButtonBase,
  Chip,
  CircularProgress,
  InputAdornment,
  Skeleton,
  Slide,
  TextField,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type AxiosResponse } from 'axios';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { searchScenarios, updateScenariosWithInjectorContracts } from '../../../../actions/scenarios/scenario-actions';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../../components/i18n';
import ItemSeverity from '../../../../components/ItemSeverity';
import PlatformIconGroup from '../../../../components/PlatformIconGroup';
import {
  type InjectorContractSearchPaginationInput,
  type PageRawPaginationScenario,
  type RawPaginationScenario,
  type ScenarioIdsAndInjectorContractsInputs,
  type ScenarioSimple,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import { SCENARIO_TYPE_TIME_BASED } from '../../scenarios/scenario/ScenarioType';

interface Props {
  isExclusionMode: boolean;
  selectedElements: Record<string, ThreatArsenalAction>;
  deSelectedElements: Record<string, ThreatArsenalAction>;
  searchPaginationInput: InjectorContractSearchPaginationInput;
  handleClose: () => void;
}

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

const ThreatArsenalScenarioUpdateComponent = ({
  isExclusionMode,
  selectedElements,
  deSelectedElements,
  searchPaginationInput,
  handleClose,
}: Props) => {
  const { t, locale, nsdt } = useFormatter();
  const navigate = useNavigate();
  const theme = useTheme();

  const [textSearch, setTextSearch] = useState('');
  const [scenarios, setScenarios] = useState<RawPaginationScenario[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(0);
  const [isLoading, setLoading] = useState(true);
  const [isLoadingMore, setLoadingMore] = useState(false);
  const [selectedScenarios, setSelectedScenarios] = useState<RawPaginationScenario[]>([]);
  const [isSubmitting, setSubmitting] = useState(false);
  // Requests can resolve out of order (debounced searches, "show more" racing a
  // new search): only the latest request is allowed to touch the list state.
  const requestIdRef = useRef(0);

  const fetchPage = (pageToLoad: number, search: string, append: boolean) => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const input = buildSearchPagination({
      page: pageToLoad,
      size: PAGE_SIZE,
      textSearch: search,
      filterGroup: {
        mode: 'and',
        filters: [{
          id: generateFilterId(),
          key: 'scenario_type',
          operator: 'eq',
          mode: 'or',
          values: [SCENARIO_TYPE_TIME_BASED],
        }],
      },
      sorts: [{
        property: 'scenario_updated_at',
        direction: 'DESC',
      }],
    });
    return searchScenarios(input)
      .then((response: AxiosResponse<PageRawPaginationScenario>) => {
        if (requestId !== requestIdRef.current) return;
        const content = response.data.content ?? [];
        setScenarios(prev => (append ? [...prev, ...content] : content));
        setTotalElements(response.data.totalElements ?? 0);
        setHasMore(!(response.data.last ?? true));
      })
      .finally(() => {
        if (requestId !== requestIdRef.current) return;
        setLoading(false);
        setLoadingMore(false);
      });
  };

  useEffect(() => {
    setLoading(true);
    const handler = setTimeout(() => {
      setPage(0);
      fetchPage(0, textSearch, false);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handler);
  }, [textSearch]);

  const handleShowMore = () => {
    const nextPage = page + 1;
    setPage(nextPage);
    setLoadingMore(true);
    fetchPage(nextPage, textSearch, true);
  };

  const isSelected = (scenario: RawPaginationScenario) =>
    selectedScenarios.some(selected => selected.scenario_id === scenario.scenario_id);

  const toggleScenario = (scenario: RawPaginationScenario) => {
    setSelectedScenarios(prev => (
      prev.some(selected => selected.scenario_id === scenario.scenario_id)
        ? prev.filter(selected => selected.scenario_id !== scenario.scenario_id)
        : [...prev, scenario]
    ));
  };

  const handleSubmit = () => {
    setSubmitting(true);
    const inputs: ScenarioIdsAndInjectorContractsInputs = {
      locale: locale,
      scenario_ids: selectedScenarios
        .map(scenario => scenario.scenario_id)
        .filter((id): id is string => !!id),
      injector_contract_search_pagination_input: {
        ...searchPaginationInput,
        injector_contract_ids_to_process: isExclusionMode ? [] : Object.keys(selectedElements),
        injector_contract_ids_to_ignore: isExclusionMode ? Object.keys(deSelectedElements) : [],
      },
    };
    updateScenariosWithInjectorContracts(inputs).then((result: AxiosResponse<ScenarioSimple[]>) => {
      navigate(`/admin/scenarios/${result.data[0].scenario_id}/injects`);
    }).finally(() => setSubmitting(false));
  };

  return (
    <Slide in={true} direction="left" mountOnEnter unmountOnExit>
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
        overflowX: 'hidden',
      }}
      >
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('Select the scenarios that will receive the selected actions')}
        </Typography>
        <Typography variant="caption" sx={{ color: 'text.disabled' }}>
          {t('Chained scenarios are not listed: their injects are driven by their workflow')}
        </Typography>

        <TextField
          fullWidth
          size="small"
          variant="outlined"
          placeholder={t('Search scenarios...')}
          value={textSearch}
          onChange={event => setTextSearch(event.target.value)}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchOutlined fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
        />

        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 1,
          flexWrap: 'wrap',
          minHeight: 24,
        }}
        >
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {totalElements === 1
              ? t('1 scenario found')
              : t('{count} scenarios found', { count: totalElements })}
          </Typography>
          {selectedScenarios.length > 0 && (
            <Box sx={{
              display: 'flex',
              gap: 0.5,
              flexWrap: 'wrap',
              justifyContent: 'flex-end',
            }}
            >
              {selectedScenarios.map(scenario => (
                <Chip
                  key={scenario.scenario_id}
                  size="small"
                  color="primary"
                  variant="outlined"
                  label={scenario.scenario_name}
                  onDelete={() => toggleScenario(scenario)}
                />
              ))}
            </Box>
          )}
        </Box>

        {isLoading && (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 1,
          }}
          >
            {Array.from({ length: 4 }).map((_, index) => (
              // eslint-disable-next-line react/no-array-index-key
              <Skeleton key={index} variant="rounded" height={56} />
            ))}
          </Box>
        )}

        {!isLoading && scenarios.length === 0 && (
          <Box
            sx={{
              paddingBlock: 5,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 0.5,
              border: `1px dashed ${theme.palette.divider}`,
              borderRadius: 1,
            }}
          >
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {t('No scenarios found')}
            </Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {t('Try adjusting your search')}
            </Typography>
          </Box>
        )}

        {!isLoading && scenarios.length > 0 && (
          <Box sx={{
            border: `1px solid ${theme.palette.divider}`,
            borderRadius: 1,
            overflow: 'hidden',
          }}
          >
            {scenarios.map((scenario, index) => {
              const selected = isSelected(scenario);
              return (
                <ButtonBase
                  key={scenario.scenario_id}
                  onClick={() => toggleScenario(scenario)}
                  aria-pressed={selected}
                  data-testid="threat-arsenal-scenario-row"
                  sx={{
                    'width': '100%',
                    'display': 'flex',
                    'alignItems': 'center',
                    'gap': 1.5,
                    'paddingBlock': 1.25,
                    'paddingInline': 1.5,
                    'textAlign': 'left',
                    'justifyContent': 'flex-start',
                    'borderTop': index > 0 ? `1px solid ${theme.palette.divider}` : 'none',
                    'backgroundColor': selected
                      ? alpha(theme.palette.primary.main, 0.08)
                      : 'transparent',
                    'transition': theme.transitions.create('background-color', { duration: theme.transitions.duration.shorter }),
                    '&:hover': {
                      backgroundColor: selected
                        ? alpha(theme.palette.primary.main, 0.12)
                        : theme.palette.action.hover,
                    },
                  }}
                >
                  {selected
                    ? <CheckCircleOutlined fontSize="small" color="primary" />
                    : <RadioButtonUncheckedOutlined fontSize="small" sx={{ color: 'text.secondary' }} />}
                  <Box sx={{
                    flex: 1,
                    minWidth: 0,
                  }}
                  >
                    <Typography
                      noWrap
                      sx={{
                        fontSize: 13.5,
                        fontWeight: 600,
                        lineHeight: 1.35,
                      }}
                    >
                      {scenario.scenario_name}
                    </Typography>
                    <Typography variant="caption" sx={{ color: 'text.disabled' }}>
                      {t('Updated')}
                      {' '}
                      {nsdt(scenario.scenario_updated_at)}
                    </Typography>
                  </Box>
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    flexShrink: 0,
                  }}
                  >
                    <PlatformIconGroup platforms={scenario.scenario_platforms} width={16} />
                  </Box>
                  <ItemSeverity
                    variant="inList"
                    severity={scenario.scenario_severity}
                    label={t(scenario.scenario_severity ?? 'Unknown')}
                  />
                </ButtonBase>
              );
            })}
          </Box>
        )}

        {!isLoading && hasMore && (
          <Button
            size="small"
            onClick={handleShowMore}
            disabled={isLoadingMore}
            startIcon={isLoadingMore ? <CircularProgress size={14} /> : undefined}
            sx={{ alignSelf: 'center' }}
          >
            {t('Show more')}
          </Button>
        )}

        <Box sx={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 1,
          marginTop: 1,
        }}
        >
          <Button
            variant="outlined"
            color="primary"
            onClick={handleClose}
            disabled={isSubmitting}
          >
            {t('Back')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={handleSubmit}
            disabled={selectedScenarios.length === 0 || isSubmitting}
          >
            {selectedScenarios.length > 1
              ? t('Add to {count} scenarios', { count: selectedScenarios.length })
              : t('Add to scenario')}
          </Button>
        </Box>
      </Box>
    </Slide>
  );
};

export default ThreatArsenalScenarioUpdateComponent;
