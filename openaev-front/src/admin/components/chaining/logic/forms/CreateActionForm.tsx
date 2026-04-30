import { Add, ArrowBack, DeleteOutline, KeyboardArrowRight } from '@mui/icons-material';
import {
  Button,
  Chip,
  FormControl,
  IconButton,
  InputLabel,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemSecondaryAction,
  ListItemText,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useMemo, useState } from 'react';

import { searchEndpoints } from '../../../../../actions/assets/endpoint-actions';
import { searchInjectorContracts } from '../../../../../actions/InjectorContracts';
import { useFormatter } from '../../../../../components/i18n';
import PlatformIcon from '../../../../../components/PlatformIcon';
import type { EndpointOutput, InjectorContractFullOutput } from '../../../../../utils/api-types';
import type { ContractElement } from '../../../../../utils/api-types-custom';
import InjectIcon from '../../../common/injects/InjectIcon';
import type { MapperConditionRow } from './MapperConditionRow';
import { CONDITION_KEY_TYPES, MAPPING_TYPES } from './MapperConditionRow';

export interface CreateActionData {
  inject_title: string;
  inject_description: string;
  inject_injector_contract: string;
  inject_injector?: string;
  inject_assets: string[];
  step_conditions: MapperConditionRow[];
  contract_fields: ContractElement[];
}

interface Props {
  onSubmit: (data: CreateActionData) => void;
  handleClose: () => void;
}

const getContractLabel = (contract: InjectorContractFullOutput) => {
  if (contract.injector_contract_labels) {
    const labels = Object.values(contract.injector_contract_labels);
    if (labels.length > 0) return labels[0];
  }
  return contract.injector_contract_id;
};

const CreateActionForm: FunctionComponent<Props> = ({ onSubmit, handleClose }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [step, setStep] = useState<'contract' | 'details'>('contract');
  const [selectedContract, setSelectedContract] = useState<InjectorContractFullOutput | null>(null);

  // Contract search state
  const [contracts, setContracts] = useState<InjectorContractFullOutput[]>([]);
  const [searchText, setSearchText] = useState('');

  // Detail form state
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');

  // Asset state
  const [selectedAssets, setSelectedAssets] = useState<EndpointOutput[]>([]);
  const [assetResults, setAssetResults] = useState<EndpointOutput[]>([]);
  const [assetSearchText, setAssetSearchText] = useState('');

  // Mapper conditions state
  const [mapperConditions, setMapperConditions] = useState<MapperConditionRow[]>([]);

  const fetchContracts = useCallback((text: string) => {
    searchInjectorContracts({
      filterGroup: {
        mode: 'and',
        filters: [],
      },
      size: 20,
      page: 0,
      sorts: [],
      textSearch: text || undefined,
    }).then((res: { data: { content?: InjectorContractFullOutput[] } }) => {
      setContracts(res.data?.content ?? []);
    });
  }, []);

  useEffect(() => {
    fetchContracts(searchText);
  }, [searchText, fetchContracts]);

  const fetchAssets = useCallback((text: string) => {
    searchEndpoints({
      filterGroup: {
        mode: 'and',
        filters: [],
      },
      size: 20,
      page: 0,
      sorts: [],
      textSearch: text || undefined,
    }).then((res: { data: { content?: EndpointOutput[] } }) => {
      setAssetResults(res.data?.content ?? []);
    });
  }, []);

  useEffect(() => {
    fetchAssets(assetSearchText);
  }, [assetSearchText, fetchAssets]);

  // Parse contract fields from the selected contract's content
  const contractFields: ContractElement[] = useMemo(() => {
    if (!selectedContract?.injector_contract_content) return [];
    try {
      const parsed = JSON.parse(selectedContract.injector_contract_content);
      return (parsed.fields ?? []) as ContractElement[];
    } catch {
      return [];
    }
  }, [selectedContract]);

  const handleSelectContract = (contract: InjectorContractFullOutput) => {
    setSelectedContract(contract);
    setStep('details');
  };

  const handleSubmit = () => {
    if (!title.trim() || !selectedContract) return;
    onSubmit({
      inject_title: title.trim(),
      inject_description: description.trim(),
      inject_injector_contract: selectedContract.injector_contract_id,
      inject_injector: selectedContract.injector_contract_injectors?.[0],
      inject_assets: selectedAssets.map(a => a.asset_id),
      step_conditions: mapperConditions,
      contract_fields: contractFields,
    });
  };

  if (step === 'contract') {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
      >
        <TextField
          label={t('Search injector contracts')}
          fullWidth
          value={searchText}
          onChange={e => setSearchText(e.target.value)}
          autoFocus
          size="small"
        />
        <List
          disablePadding
          sx={{
            maxHeight: 'calc(100vh - 300px)',
            overflow: 'auto',
          }}
        >
          {contracts.map(contract => (
            <ListItemButton
              key={contract.injector_contract_id}
              onClick={() => handleSelectContract(contract)}
              divider
              sx={{ borderRadius: 1 }}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>
                <InjectIcon
                  type={contract.injector_contract_injector_type}
                  size="small"
                />
              </ListItemIcon>
              <ListItemText
                primary={getContractLabel(contract)}
                secondary={(
                  <span style={{
                    display: 'flex',
                    gap: 4,
                    alignItems: 'center',
                    marginTop: 4,
                  }}
                  >
                    {contract.injector_contract_platforms?.map(platform => (
                      <PlatformIcon
                        key={platform}
                        platform={platform}
                        width={16}
                        tooltip
                      />
                    ))}
                  </span>
                )}
              />
              <KeyboardArrowRight color="action" />
            </ListItemButton>
          ))}
          {contracts.length === 0 && (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                p: 2,
                textAlign: 'center',
              }}
            >
              {t('No injector contracts found')}
            </Typography>
          )}
        </List>
        <Button variant="outlined" color="primary" onClick={handleClose}>
          {t('Cancel')}
        </Button>
      </div>
    );
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
      }}
      >
        <IconButton size="small" onClick={() => setStep('contract')}>
          <ArrowBack fontSize="small" />
        </IconButton>
        <InjectIcon
          type={selectedContract?.injector_contract_injector_type}
          size="small"
        />
        <Chip
          label={selectedContract ? getContractLabel(selectedContract) : ''}
          size="small"
          variant="outlined"
          sx={{ borderColor: theme.palette.primary.main }}
        />
      </div>
      <TextField
        label={t('Title (inject_title)')}
        fullWidth
        required
        value={title}
        onChange={e => setTitle(e.target.value)}
        autoFocus
      />
      <TextField
        label={t('Description (inject_description)')}
        fullWidth
        multiline
        minRows={2}
        value={description}
        onChange={e => setDescription(e.target.value)}
      />
      <Typography variant="subtitle2" sx={{ mt: 1 }}>
        {t('Assets')}
      </Typography>
      {selectedAssets.length > 0 && (
        <List dense disablePadding>
          {selectedAssets.map(asset => (
            <ListItemButton key={asset.asset_id} disableRipple sx={{ borderRadius: 1 }}>
              <ListItemText
                primary={asset.asset_name}
                secondary={`${asset.endpoint_platform} · ${asset.endpoint_arch}`}
              />
              <ListItemSecondaryAction>
                <IconButton
                  edge="end"
                  size="small"
                  onClick={() => setSelectedAssets(prev => prev.filter(a => a.asset_id !== asset.asset_id))}
                >
                  <DeleteOutline fontSize="small" />
                </IconButton>
              </ListItemSecondaryAction>
            </ListItemButton>
          ))}
        </List>
      )}
      <TextField
        label={t('Search assets')}
        fullWidth
        size="small"
        value={assetSearchText}
        onChange={e => setAssetSearchText(e.target.value)}
      />
      {assetResults.length > 0 && (
        <List
          dense
          disablePadding
          sx={{
            maxHeight: 200,
            overflow: 'auto',
          }}
        >
          {assetResults
            .filter(a => !selectedAssets.some(s => s.asset_id === a.asset_id))
            .map(asset => (
              <ListItemButton
                key={asset.asset_id}
                onClick={() => setSelectedAssets(prev => [...prev, asset])}
                divider
                sx={{ borderRadius: 1 }}
              >
                <ListItemText
                  primary={asset.asset_name}
                  secondary={`${asset.endpoint_platform} · ${asset.endpoint_arch}`}
                />
              </ListItemButton>
            ))}
        </List>
      )}
      <Typography variant="subtitle2" sx={{ mt: 2 }}>
        {t('Mapper conditions (step_conditions)')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        {t('Map inputs from the contract with output types from other steps')}
      </Typography>
      {contractFields.length > 0 && (
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 6,
            marginBottom: 8,
          }}
        >
          {contractFields.map(field => (
            <Chip
              key={field.key}
              label={`${t(field.label)} (${field.key})`}
              size="small"
              variant="outlined"
              sx={{ fontSize: '0.75rem' }}
            />
          ))}
        </div>
      )}
      {mapperConditions.map((mc, idx) => (
        <div
          key={idx}
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
          }}
        >
          <FormControl size="small" sx={{ minWidth: 130 }}>
            <InputLabel>{t('Key type')}</InputLabel>
            <Select
              label={t('Key type')}
              value={mc.condition_key_type}
              onChange={(e) => {
                setMapperConditions(prev => prev.map((c, i) => (i === idx
                  ? {
                      ...c,
                      condition_key_type: e.target.value,
                    }
                  : c)));
              }}
            >
              {CONDITION_KEY_TYPES.map(kt => (
                <MenuItem key={kt} value={kt}>{kt}</MenuItem>
              ))}
            </Select>
          </FormControl>
          {contractFields.length > 0
            ? (
                <FormControl size="small" sx={{ flex: 1 }}>
                  <InputLabel>{t('Contract field')}</InputLabel>
                  <Select
                    label={t('Contract field')}
                    value={mc.condition_key}
                    onChange={(e) => {
                      setMapperConditions(prev => prev.map((c, i) => (i === idx
                        ? {
                            ...c,
                            condition_key: e.target.value,
                          }
                        : c)));
                    }}
                  >
                    {contractFields.map(field => (
                      <MenuItem key={field.key} value={field.key}>
                        {`${t(field.label)} (${field.key})`}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              )
            : (
                <TextField
                  label={t('Key')}
                  size="small"
                  value={mc.condition_key}
                  onChange={(e) => {
                    setMapperConditions(prev => prev.map((c, i) => (i === idx
                      ? {
                          ...c,
                          condition_key: e.target.value,
                        }
                      : c)));
                  }}
                  sx={{ flex: 1 }}
                />
              )}
          <FormControl size="small" sx={{ minWidth: 110 }}>
            <InputLabel>{t('Mapping')}</InputLabel>
            <Select
              label={t('Mapping')}
              value={mc.condition_mapping_type}
              onChange={(e) => {
                setMapperConditions(prev => prev.map((c, i) => (i === idx
                  ? {
                      ...c,
                      condition_mapping_type: e.target.value,
                    }
                  : c)));
              }}
            >
              {MAPPING_TYPES.map(mt => (
                <MenuItem key={mt} value={mt}>{mt}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <IconButton
            size="small"
            onClick={() => setMapperConditions(prev => prev.filter((_, i) => i !== idx))}
          >
            <DeleteOutline fontSize="small" />
          </IconButton>
        </div>
      ))}
      <Button
        size="small"
        startIcon={<Add />}
        onClick={() => setMapperConditions(prev => [...prev, {
          condition_key_type: 'text',
          condition_key: '',
          condition_mapping_type: 'GLOBAL',
        }])}
      >
        {t('Add mapper condition')}
      </Button>
      <Button
        variant="contained"
        color="secondary"
        onClick={handleSubmit}
        disabled={!title.trim()}
      >
        {t('Create')}
      </Button>
      <Button variant="outlined" color="primary" onClick={handleClose}>
        {t('Cancel')}
      </Button>
    </div>
  );
};

export default CreateActionForm;
