import { AddOutlined, DevicesOtherOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';

import { findEndpoints, searchEndpoints } from '../../../../../actions/assets/endpoint-actions';
import { fetchExecutors } from '../../../../../actions/executors/executor-action';
import type { ExecutorHelper } from '../../../../../actions/executors/executor-helper';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectList, { type SelectListElements } from '../../../../../components/common/SelectList';
import Transition from '../../../../../components/common/Transition';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import PlatformIcon from '../../../../../components/PlatformIcon';
import { useHelper } from '../../../../../store';
import type { Endpoint, EndpointOutput } from '../../../../../utils/api-types';
import type { ScopeList, WorkflowScope } from '../../../../../utils/api-types-custom';
import { getActiveMsgTooltip, getExecutorsCount } from '../../../../../utils/endpoints/utils';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import AssetStatus from '../../../assets/AssetStatus';

interface Props {
  open: boolean;
  onClose: () => void;
  onSubmit: (scope: WorkflowScope, timeoutSeconds: number) => void;
  initialScope: WorkflowScope;
  initialTimeoutSeconds: number;
}

const ScopeDefinitionDialog: FunctionComponent<Props> = ({
  open,
  onClose,
  onSubmit,
  initialScope,
  initialTimeoutSeconds,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const [activeTab, setActiveTab] = useState(0);
  const [timeoutMinutes, setTimeoutMinutes] = useState(Math.round(initialTimeoutSeconds / 60));

  // Whitelist state
  const [wlEndpointValues, setWlEndpointValues] = useState<(Endpoint | EndpointOutput)[]>([]);
  const [wlManualEntries, setWlManualEntries] = useState<string[]>([]);
  const [wlManualInput, setWlManualInput] = useState('');

  // Blacklist state
  const [blEndpointValues, setBlEndpointValues] = useState<(Endpoint | EndpointOutput)[]>([]);
  const [blManualEntries, setBlManualEntries] = useState<string[]>([]);
  const [blManualInput, setBlManualInput] = useState('');

  const [isLoading, setIsLoading] = useState(false);

  const { executorsMap } = useHelper((helper: ExecutorHelper) => ({ executorsMap: helper.getExecutorsMap() }));

  useDataLoader(() => {
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
      dispatch(fetchExecutors());
    }
  });

  useEffect(() => {
    if (open) {
      setTimeoutMinutes(Math.round(initialTimeoutSeconds / 60));
      setWlManualEntries([...initialScope.whitelist.manual_entries]);
      setWlManualInput('');
      setBlManualEntries([...initialScope.blacklist.manual_entries]);
      setBlManualInput('');
      setActiveTab(0);

      if (initialScope.whitelist.endpoint_ids.length > 0) {
        findEndpoints(initialScope.whitelist.endpoint_ids).then((result) => setWlEndpointValues(result.data));
      } else {
        setWlEndpointValues([]);
      }
      if (initialScope.blacklist.endpoint_ids.length > 0) {
        findEndpoints(initialScope.blacklist.endpoint_ids).then((result) => setBlEndpointValues(result.data));
      } else {
        setBlEndpointValues([]);
      }
    }
  }, [open, initialScope, initialTimeoutSeconds]);

  const handleSubmit = () => {
    onSubmit(
      {
        whitelist: {
          endpoint_ids: wlEndpointValues.map((v) => v.asset_id),
          manual_entries: wlManualEntries,
        },
        blacklist: {
          endpoint_ids: blEndpointValues.map((v) => v.asset_id),
          manual_entries: blManualEntries,
        },
      },
      timeoutMinutes * 60,
    );
  };

  const elements: SelectListElements<EndpointOutput> = useMemo(() => ({
    icon: { value: () => <DevicesOtherOutlined color="primary" /> },
    headers: [
      {
        field: 'asset_name',
        value: (ep: EndpointOutput) => ep.asset_name,
        width: 35,
      },
      {
        field: 'endpoint_active',
        value: (ep: EndpointOutput) => {
          const status = getActiveMsgTooltip(ep.asset_agents.map((a) => a.agent_active ?? false), t('Active'), t('Inactive'), t('Agentless'));
          return (
            <Tooltip title={status.activeMsgTooltip}>
              <span>
                <AssetStatus variant="list" status={status.status} />
              </span>
            </Tooltip>
          );
        },
        width: 20,
      },
      {
        field: 'endpoint_platform',
        value: (ep: EndpointOutput) => (
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <PlatformIcon platform={ep.endpoint_platform} width={20} marginRight={theme.spacing(2)} />
          </div>
        ),
        width: 10,
      },
      {
        field: 'endpoint_arch',
        value: (ep: EndpointOutput) => ep.endpoint_arch,
        width: 15,
      },
      {
        field: 'endpoint_agents_executor',
        value: (ep: EndpointOutput) => {
          if (ep.asset_agents.length > 0) {
            const groupedExecutors = getExecutorsCount(ep, executorsMap);
            return (
              <>
                {Object.keys(groupedExecutors).map((executorType) => {
                  const executorsOfType = groupedExecutors[executorType];
                  const count = executorsOfType.length;
                  const base = executorsOfType[0];
                  if (count > 0) {
                    return (
                      <Tooltip key={executorType} title={`${base.executor_name} : ${count}`} arrow>
                        <div style={{ display: 'inline-flex', alignItems: 'center' }}>
                          <img
                            src={`/api/images/executors/icons/${executorType}`}
                            alt={executorType}
                            style={{ width: 20, height: 20, borderRadius: 4, marginRight: 10 }}
                          />
                        </div>
                      </Tooltip>
                    );
                  }
                  return t('Unknown');
                })}
              </>
            );
          }
          return <span>-</span>;
        },
        width: 10,
      },
      {
        field: 'asset_tags',
        value: (ep: EndpointOutput) => <ItemTags variant="reduced-view" tags={ep.asset_tags} />,
        width: 10,
      },
    ],
  }), [executorsMap]);

  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
  const availableFilterNames = ['asset_tags', 'endpoint_platform', 'endpoint_arch'];
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));

  const paginationComponent = (
    <PaginationComponentV2
      fetch={searchEndpoints}
      searchPaginationInput={searchPaginationInput}
      setContent={setEndpoints}
      setLoading={setIsLoading}
      entityPrefix="endpoint"
      availableFilterNames={availableFilterNames}
      queryableHelpers={queryableHelpers}
    />
  );

  const currentEndpointValues = activeTab === 0 ? wlEndpointValues : blEndpointValues;
  const setCurrentEndpointValues = activeTab === 0 ? setWlEndpointValues : setBlEndpointValues;
  const currentManualEntries = activeTab === 0 ? wlManualEntries : blManualEntries;
  const setCurrentManualEntries = activeTab === 0 ? setWlManualEntries : setBlManualEntries;
  const currentManualInput = activeTab === 0 ? wlManualInput : blManualInput;
  const setCurrentManualInput = activeTab === 0 ? setWlManualInput : setBlManualInput;

  const addEndpoint = (_endpointId: string, endpoint: EndpointOutput) => {
    setCurrentEndpointValues([...currentEndpointValues, endpoint]);
  };
  const removeEndpoint = (endpointId: string) => {
    setCurrentEndpointValues(currentEndpointValues.filter((v) => v.asset_id !== endpointId));
  };

  const handleAddManualEntry = () => {
    const trimmed = currentManualInput.trim();
    if (trimmed && !currentManualEntries.includes(trimmed)) {
      setCurrentManualEntries([...currentManualEntries, trimmed]);
      setCurrentManualInput('');
    }
  };
  const handleRemoveManualEntry = (entry: string) => {
    setCurrentManualEntries(currentManualEntries.filter((e) => e !== entry));
  };

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={onClose}
      fullWidth
      maxWidth="lg"
      slotProps={{
        paper: {
          elevation: 1,
          sx: { minHeight: 700, maxHeight: 700 },
        },
      }}
    >
      <DialogTitle>{t('Define Scope')}</DialogTitle>
      <DialogContent>
        <Box sx={{ marginBottom: 2, marginTop: 1, display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography variant="h4">{t('Simulation timeout')}</Typography>
          <TextField
            size="small"
            type="number"
            value={timeoutMinutes}
            onChange={(e) => setTimeoutMinutes(Math.max(1, parseInt(e.target.value, 10) || 1))}
            slotProps={{ htmlInput: { min: 1 } }}
            sx={{ width: 120 }}
          />
          <Typography variant="body2" color="text.secondary">{t('minutes')}</Typography>
        </Box>

        <Tabs
          value={activeTab}
          onChange={(_, newVal) => setActiveTab(newVal)}
          sx={{ marginBottom: 2 }}
        >
          <Tab
            label={t('Whitelist')}
            sx={{ '&.Mui-selected': { color: theme.palette.success.main } }}
          />
          <Tab
            label={t('Blacklist')}
            sx={{ '&.Mui-selected': { color: theme.palette.error.main } }}
          />
        </Tabs>

        <Box>
          <SelectList<EndpointOutput, Endpoint>
            values={endpoints}
            selectedValues={currentEndpointValues}
            isLoadingValues={isLoading}
            elements={elements}
            onSelect={addEndpoint}
            onDelete={removeEndpoint}
            paginationComponent={paginationComponent}
            getId={(element) => element.asset_id}
            getName={(element) => element.asset_name}
          />
        </Box>

        <Box sx={{ marginTop: 3 }}>
          <Typography variant="h4" sx={{ marginBottom: 1 }}>{t('Manual entries')}</Typography>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', marginBottom: 1 }}>
            <TextField
              size="small"
              value={currentManualInput}
              onChange={(e) => setCurrentManualInput(e.target.value)}
              placeholder={t('IP, hostname, or subnet (e.g. 192.168.1.0/24)')}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleAddManualEntry(); } }}
              sx={{ flex: 1 }}
            />
            <Button
              variant="outlined"
              size="small"
              startIcon={<AddOutlined />}
              onClick={handleAddManualEntry}
              disabled={!currentManualInput.trim()}
            >
              {t('Add')}
            </Button>
          </Box>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
            {currentManualEntries.map((entry) => (
              <Chip
                key={entry}
                label={entry}
                onDelete={() => handleRemoveManualEntry(entry)}
                size="small"
              />
            ))}
          </Box>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('Cancel')}</Button>
        <Button color="secondary" onClick={handleSubmit}>
          {t('Update')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ScopeDefinitionDialog;
