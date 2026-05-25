import { Close, InfoOutlined, KeyboardArrowDown, RestartAlt } from '@mui/icons-material';
import {
  Box,
  Button,
  Chip,
  Menu,
  MenuItem,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { findEndpoints } from '../../../../actions/assets/endpoint-actions';
import { directFetchInjectorContract } from '../../../../actions/InjectorContracts';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { EndpointOutput, ThreatArsenalAction } from '../../../../utils/api-types';
import type { ContractElement } from '../../../../utils/api-types-custom';
import DrawerBreadcrumb from './DrawerBreadcrumb';
import { CONDITION_KEY_TYPES } from './forms/MapperConditionRow';

interface AddActionDetailProps {
  open: boolean;
  action: ThreatArsenalAction | null;
  allowListedAssetIds: string[];
  onClose: () => void;
  onBack: () => void;
  onBackToRoot: () => void;
  onSave: (data: ActionDetailData) => void;
}

export interface ActionDetailData {
  inject_title: string;
  inject_injector_contract: string;
  inject_injector?: string;
  inject_assets: string[];
  inject_content: Record<string, unknown>;
  inject_field_links: Record<string, string>;
  contract_fields: ContractElement[];
}

const AddActionDetail: FunctionComponent<AddActionDetailProps> = ({
  open,
  action,
  allowListedAssetIds,
  onClose,
  onBack,
  onBackToRoot,
  onSave,
}) => {
  const { t, tPick } = useFormatter();

  // Title
  const [title, setTitle] = useState('');

  // Assets from allow list
  const [allowListedEndpoints, setAllowListedEndpoints] = useState<EndpointOutput[]>([]);
  const [selectedAssetIds, setSelectedAssetIds] = useState<Set<string>>(new Set());

  // Contract fields
  const [contractFields, setContractFields] = useState<ContractElement[]>([]);
  const [loadingContract, setLoadingContract] = useState(false);

  // Dynamic field values (inject_content)
  const [fieldValues, setFieldValues] = useState<Record<string, unknown>>({});

  // Output links per field (field key → output type)
  const [fieldOutputLinks, setFieldOutputLinks] = useState<Record<string, string>>({});
  const [linkMenuAnchor, setLinkMenuAnchor] = useState<null | HTMLElement>(null);
  const [linkMenuFieldKey, setLinkMenuFieldKey] = useState<string | null>(null);

  // Fetch allowlisted endpoints
  useEffect(() => {
    if (allowListedAssetIds.length > 0) {
      findEndpoints(allowListedAssetIds).then((result: { data: EndpointOutput[] }) => {
        setAllowListedEndpoints(result.data);
      });
    } else {
      setAllowListedEndpoints([]);
    }
  }, [allowListedAssetIds]);

  // Reset state when action changes
  useEffect(() => {
    if (action) {
      const label = action.action_labels ? tPick(action.action_labels) : '';
      setTitle(label);
      setSelectedAssetIds(new Set(allowListedAssetIds));
      setFieldValues({});
      setFieldOutputLinks({});
      setContractFields([]);

      // Fetch injector contract content
      setLoadingContract(true);
      directFetchInjectorContract(action.injector_contract_id)
        .then((res: { data: { injector_contract_content?: string } }) => {
          if (res.data?.injector_contract_content) {
            try {
              const parsed = JSON.parse(res.data.injector_contract_content);
              const fields = (parsed.fields ?? []) as ContractElement[];
              setContractFields(fields);
              // Set default values
              const defaults: Record<string, unknown> = {};
              for (const field of fields) {
                if (field.defaultValue !== undefined && field.defaultValue !== null) {
                  defaults[field.key] = field.defaultValue;
                }
              }
              setFieldValues(defaults);
            } catch {
              setContractFields([]);
            }
          }
        })
        .catch(() => setContractFields([]))
        .finally(() => setLoadingContract(false));
    }
  }, [action, allowListedAssetIds]);

  const handleFieldChange = (key: string, value: unknown) => {
    setFieldValues(prev => ({
      ...prev,
      [key]: value,
    }));
  };

  const handleResetDefaults = () => {
    const defaults: Record<string, unknown> = {};
    for (const field of contractFields) {
      if (field.defaultValue !== undefined && field.defaultValue !== null) {
        defaults[field.key] = field.defaultValue;
      }
    }
    setFieldValues(defaults);
  };

  const handleSave = () => {
    if (!action) return;
    onSave({
      inject_title: title.trim(),
      inject_injector_contract: action.injector_contract_id,
      inject_injector: action.action_injector_type,
      inject_assets: Array.from(selectedAssetIds),
      inject_content: fieldValues,
      inject_field_links: fieldOutputLinks,
      contract_fields: contractFields,
    });
  };

  const actionLabel = useMemo(() => {
    if (!action?.action_labels) return '';
    return tPick(action.action_labels);
  }, [action, tPick]);

  const handleOpenLinkMenu = (event: React.MouseEvent<HTMLElement>, fieldKey: string) => {
    setLinkMenuAnchor(event.currentTarget);
    setLinkMenuFieldKey(fieldKey);
  };

  const handleCloseLinkMenu = () => {
    setLinkMenuAnchor(null);
    setLinkMenuFieldKey(null);
  };

  const handleSelectOutput = (outputType: string) => {
    if (linkMenuFieldKey) {
      setFieldOutputLinks(prev => ({
        ...prev,
        [linkMenuFieldKey]: outputType,
      }));
    }
    handleCloseLinkMenu();
  };

  const handleRemoveLink = (fieldKey: string) => {
    setFieldOutputLinks((prev) => {
      const next = { ...prev };
      delete next[fieldKey];
      return next;
    });
  };

  const renderField = (field: ContractElement) => {
    const value = fieldValues[field.key] ?? '';
    const fieldLabel = t(field.label) || field.key;

    switch (field.type) {
      case 'textarea':
        return (
          <TextField
            key={field.key}
            fullWidth
            label={fieldLabel}
            value={String(value)}
            onChange={e => handleFieldChange(field.key, e.target.value)}
            multiline
            minRows={3}
            size="small"
            required={field.mandatory}
          />
        );
      case 'number':
        return (
          <TextField
            key={field.key}
            fullWidth
            label={fieldLabel}
            value={String(value)}
            onChange={e => handleFieldChange(field.key, e.target.value)}
            type="number"
            size="small"
            required={field.mandatory}
          />
        );
      case 'checkbox':
        return (
          <TextField
            key={field.key}
            fullWidth
            label={fieldLabel}
            value={String(value || 'false')}
            onChange={e => handleFieldChange(field.key, e.target.value)}
            select
            SelectProps={{ native: true }}
            size="small"
          >
            <option value="true">{t('Yes')}</option>
            <option value="false">{t('No')}</option>
          </TextField>
        );
      case 'select':
        if (field.choices) {
          const choices = Array.isArray(field.choices)
            ? field.choices
            : Object.entries(field.choices).map(([k, v]) => ({
                key: k,
                label: String(v),
              }));
          return (
            <TextField
              key={field.key}
              fullWidth
              label={fieldLabel}
              value={String(value)}
              onChange={e => handleFieldChange(field.key, e.target.value)}
              select
              SelectProps={{ native: true }}
              size="small"
              required={field.mandatory}
            >
              <option value="">{t('Select...')}</option>
              {choices.map(choice => (
                <option
                  key={choice.key || String(choice.label)}
                  value={choice.key || String(choice.label)}
                >
                  {String(choice.label)}
                </option>
              ))}
            </TextField>
          );
        }
        return (
          <TextField
            key={field.key}
            fullWidth
            label={fieldLabel}
            value={String(value)}
            onChange={e => handleFieldChange(field.key, e.target.value)}
            size="small"
            required={field.mandatory}
          />
        );
      default:
        // text, tuple, attachment, article, etc. → render as text input
        return (
          <TextField
            key={field.key}
            fullWidth
            label={fieldLabel}
            value={String(value)}
            onChange={e => handleFieldChange(field.key, e.target.value)}
            size="small"
            required={field.mandatory}
          />
        );
    }
  };

  // Only render visible, non-readOnly fields
  const visibleFields = useMemo(() => {
    return contractFields.filter(f => !f.readOnly);
  }, [contractFields]);

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={actionLabel}
    >
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        <DrawerBreadcrumb
          grandParentLabel={t('Add Component')}
          onBackToGrandParent={onBackToRoot}
          parentLabel={t('Add Actions')}
          currentLabel={actionLabel}
          onBack={onBack}
        />

        <TextField
          label={`${t('Title')}`}
          fullWidth
          value={title}
          onChange={e => setTitle(e.target.value)}
          required
        />

        {/* Initial Target Assets */}
        <Box>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            mb: 1,
          }}
          >
            <Typography variant="subtitle2" fontWeight={600}>
              {t('Initial Target Assets')}
            </Typography>
            <Tooltip
              title={t('Additional endpoints may be included during simulation based on real decision logic.')}
            >
              <InfoOutlined fontSize="small" color="info" />
            </Tooltip>
          </Box>
          {allowListedEndpoints.length > 0 ? (
            <Box sx={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 0.5,
            }}
            >
              {allowListedEndpoints.map(asset => (
                <Chip
                  key={asset.asset_id}
                  label={asset.asset_name}
                  size="small"
                  onDelete={() => setSelectedAssetIds((prev) => {
                    const next = new Set(prev);
                    next.delete(asset.asset_id);
                    return next;
                  })}
                  deleteIcon={<Close sx={{ fontSize: 14 }} />}
                  variant={selectedAssetIds.has(asset.asset_id) ? 'filled' : 'outlined'}
                  onClick={() => {
                    setSelectedAssetIds((prev) => {
                      const next = new Set(prev);
                      if (next.has(asset.asset_id)) {
                        next.delete(asset.asset_id);
                      } else {
                        next.add(asset.asset_id);
                      }
                      return next;
                    });
                  }}
                />
              ))}
            </Box>
          ) : (
            <Typography variant="body2" color="text.secondary">
              {t('No assets in the allow list.')}
            </Typography>
          )}
        </Box>

        {/* Inject Data (dynamic contract fields) */}
        <Box>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            mb: 1,
          }}
          >
            <Typography variant="subtitle2" fontWeight={600}>
              {t('Inject Data')}
            </Typography>
            <Button
              size="small"
              startIcon={<RestartAlt />}
              onClick={handleResetDefaults}
            >
              {t('Reset default value')}
            </Button>
          </Box>
          {loadingContract && (
            <Typography variant="body2" color="text.secondary">
              {t('Loading contract fields...')}
            </Typography>
          )}
          {!loadingContract && visibleFields.length === 0 && (
            <Typography variant="body2" color="text.secondary">
              {t('No configuration fields for this action.')}
            </Typography>
          )}
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
          >
            {visibleFields.map(field => (
              <Box
                key={field.key}
                sx={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 1,
                }}
              >
                <Box sx={{ flex: 1 }}>
                  {renderField(field)}
                </Box>
                <Box sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'flex-end',
                  gap: 0.5,
                  pt: 0.5,
                }}
                >
                  {fieldOutputLinks[field.key]
                    ? (
                        <Chip
                          label={fieldOutputLinks[field.key]}
                          size="small"
                          color="primary"
                          onDelete={() => handleRemoveLink(field.key)}
                          sx={{ fontSize: '0.75rem' }}
                        />
                      )
                    : (
                        <Button
                          size="small"
                          variant="contained"
                          color="primary"
                          endIcon={<KeyboardArrowDown />}
                          onClick={e => handleOpenLinkMenu(e, field.key)}
                          sx={{
                            whiteSpace: 'nowrap',
                            textTransform: 'none',
                            fontSize: '0.75rem',
                          }}
                        >
                          {t('Link an Output')}
                        </Button>
                      )}
                </Box>
              </Box>
            ))}
            <Menu
              anchorEl={linkMenuAnchor}
              open={Boolean(linkMenuAnchor)}
              onClose={handleCloseLinkMenu}
            >
              {CONDITION_KEY_TYPES.map(outputType => (
                <MenuItem
                  key={outputType}
                  onClick={() => handleSelectOutput(outputType)}
                  dense
                >
                  {outputType}
                </MenuItem>
              ))}
            </Menu>
          </Box>
        </Box>

        {/* Actions */}
        <Box sx={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 1,
          mt: 1,
        }}
        >
          <Button variant="outlined" color="primary" onClick={onClose}>
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="secondary"
            onClick={handleSave}
          >
            {t('Save')}
          </Button>
        </Box>
      </Box>
    </Drawer>
  );
};

export default AddActionDetail;
