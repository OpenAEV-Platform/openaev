import { GpsFixedOutlined, LanguageOutlined, LinkOutlined, PlayArrowOutlined } from '@mui/icons-material';
import { Button, Chip, Divider, IconButton, Menu, MenuItem, Switch, TextField, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import { directFetchInjectorContract } from '../../../../actions/InjectorContracts';
import FindingIcon from '../../../../components/FindingIcon';
import { useFormatter } from '../../../../components/i18n';
import type { InjectorContract, InjectorContractFullOutput } from '../../../../utils/api-types';
import type { ContractElement, OutputTypeDescriptor } from '../../../../utils/api-types-custom';
import { simpleCall } from '../../../../utils/Action';
import { isNotEmptyField } from '../../../../utils/utils';
import InjectIcon from './InjectIcon';

const HIDDEN_FIELD_KEYS = new Set([
  'target_selector', 'targets', 'assets', 'asset_groups', 'assetgroups', 'asset-groups',
  'target_property_selector', 'expectations',
]);

interface Props {
  contract: InjectorContractFullOutput;
  onSubmit: (title: string, contractId: string, injectContent: Record<string, unknown>) => void;
  onCancel: () => void;
}

const LogicActionPreviewPanel: FunctionComponent<Props> = ({ contract, onSubmit, onCancel }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const injectorType = contract.injector_contract_payload_type ?? contract.injector_contract_injector_type;
  const contractId = contract.injector_contract_id;

  const [title, setTitle] = useState('');
  const [contractFields, setContractFields] = useState<ContractElement[]>([]);
  const [injectContent, setInjectContent] = useState<Record<string, unknown>>({});
  const [outputTypes, setOutputTypes] = useState<string[]>([]);
  const [outputTypeCatalog, setOutputTypeCatalog] = useState<OutputTypeDescriptor[]>([]);
  const [fieldScopes, setFieldScopes] = useState<Record<string, string>>({});

  // Link menu state
  const [linkMenuAnchor, setLinkMenuAnchor] = useState<null | HTMLElement>(null);
  const [linkMenuFieldKey, setLinkMenuFieldKey] = useState<string | null>(null);

  // Fetch full contract details and output types catalog
  useEffect(() => {
    if (!contractId) return;

    // Set default title from contract labels
    const label = typeof contract.injector_contract_labels === 'object'
      ? Object.values(contract.injector_contract_labels ?? {})[0] as string ?? contractId
      : contractId;
    setTitle(label);
    setInjectContent({});
    setFieldScopes({});

    directFetchInjectorContract(contractId).then((result) => {
      const fullContract = result.data as InjectorContract;
      const fields: ContractElement[] = [];
      if (fullContract.injector_contract_content) {
        try {
          const content = JSON.parse(fullContract.injector_contract_content);
          if (Array.isArray(content.fields)) fields.push(...content.fields);
          // Extract output types
          if (Array.isArray(content.outputTypes)) setOutputTypes(content.outputTypes);
          else setOutputTypes([]);
        } catch { setOutputTypes([]); }
      }
      const payloadArgs = fullContract.injector_contract_payload?.payload_arguments ?? [];
      for (const arg of payloadArgs) {
        if (fields.some(f => f.key === arg.key)) continue;
        fields.push({
          key: arg.key, label: arg.key, type: 'text' as ContractElement['type'],
          defaultValue: arg.default_value ?? '', mandatory: false, readOnly: false, cardinality: '1',
        } as ContractElement);
      }
      setContractFields(fields);
    }).catch(() => {});

    simpleCall('/api/output_types')
      .then((res: { data: OutputTypeDescriptor[] }) => setOutputTypeCatalog(res.data))
      .catch(() => {});
  }, [contractId]);

  const visibleFields = contractFields.filter(f => !HIDDEN_FIELD_KEYS.has(f.key));

  const toggleFieldScope = (key: string) => {
    setFieldScopes(prev => ({ ...prev, [key]: (prev[key] ?? 'LOCAL') === 'LOCAL' ? 'GLOBAL' : 'LOCAL' }));
  };

  const handleOpenLinkMenu = (e: React.MouseEvent<HTMLElement>, key: string) => {
    setLinkMenuAnchor(e.currentTarget);
    setLinkMenuFieldKey(key);
  };
  const handleCloseLinkMenu = () => { setLinkMenuAnchor(null); setLinkMenuFieldKey(null); };

  const handleLinkField = (inputType: string) => {
    if (!linkMenuFieldKey) return;
    // Store the link info in injectContent as a special marker
    setInjectContent(prev => ({ ...prev, [`__link_${linkMenuFieldKey}`]: inputType }));
    handleCloseLinkMenu();
  };

  const leafTypes = outputTypeCatalog
    .filter(ot => ot.fields.length === 0 || ['username', 'password', 'hash', 'token', 'ticket', 'share', 'cve'].includes(ot.outputType));

  const handleSubmit = () => {
    onSubmit(title, contractId, injectContent);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: theme.spacing(2), padding: theme.spacing(2), height: '100%', overflowY: 'auto' }}>
      {/* Action info header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: theme.spacing(1.5) }}>
        {isNotEmptyField(injectorType)
          ? <InjectIcon type={injectorType} size="medium" tooltip={{}} />
          : <PlayArrowOutlined color="primary" sx={{ fontSize: 28 }} />
        }
        <Typography variant="subtitle2" color="text.secondary">
          {contract.injector_contract_injector_name ?? injectorType ?? t('Unknown injector')}
        </Typography>
      </div>

      <TextField
        label={t('Action title')}
        fullWidth
        value={title}
        onChange={e => setTitle(e.target.value)}
        variant="standard"
        required
      />

      <Divider />

      {/* Targets */}
      <Typography variant="subtitle2">{t('Targets')}</Typography>
      <Typography variant="body2" color="text.secondary">
        {t('From workflow scope (defined in scenario Definition tab)')}
      </Typography>

      <Divider />

      {/* Arguments */}
      {visibleFields.length > 0 && (
        <>
          <Typography variant="subtitle2">{t('Arguments')}</Typography>
          {visibleFields.map(field => {
            const linkedType = injectContent[`__link_${field.key}`] as string | undefined;
            const isLinked = !!linkedType;
            const currentScope = fieldScopes[field.key] ?? 'LOCAL';
            const isLocal = currentScope === 'LOCAL';

            if (isLinked) {
              return (
                <div
                  key={field.key}
                  style={{
                    display: 'flex', alignItems: 'center', gap: theme.spacing(1.5),
                    padding: theme.spacing(1, 1.5), borderRadius: 4,
                    border: `1px solid ${theme.palette.success.main}40`,
                    background: `${theme.palette.success.main}08`,
                  }}
                >
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <Typography variant="body2" fontWeight={600}>{field.label || field.key}</Typography>
                    <Chip
                      size="small"
                      icon={<FindingIcon findingType={linkedType} />}
                      label={linkedType}
                      variant="outlined"
                      color="success"
                      sx={{ height: 20, fontSize: 10 }}
                    />
                  </div>
                  <Tooltip title={isLocal ? t('Local: only the triggering instance') : t('Global: all results of this type')}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                      {isLocal
                        ? <GpsFixedOutlined sx={{ fontSize: 14, color: theme.palette.primary.main }} />
                        : <LanguageOutlined sx={{ fontSize: 14, color: theme.palette.text.secondary }} />
                      }
                      <Switch checked={isLocal} onChange={() => toggleFieldScope(field.key)} size="small" />
                      <Typography variant="caption" sx={{ fontSize: 10 }}>
                        {isLocal ? t('Local') : t('Global')}
                      </Typography>
                    </div>
                  </Tooltip>
                  <Tooltip title={t('Unlink')}>
                    <IconButton size="small" onClick={() => setInjectContent(prev => { const n = { ...prev }; delete n[`__link_${field.key}`]; return n; })}>
                      <LinkOutlined sx={{ fontSize: 16, color: theme.palette.text.secondary }} />
                    </IconButton>
                  </Tooltip>
                </div>
              );
            }

            return (
              <div
                key={field.key}
                style={{
                  display: 'flex', alignItems: 'center', gap: theme.spacing(1.5),
                  padding: theme.spacing(1, 1.5), borderRadius: 4,
                  border: `1px solid ${theme.palette.divider}`,
                }}
              >
                <div style={{ flex: 1 }}>
                  <TextField
                    label={field.label || field.key}
                    value={(injectContent[field.key] as string) ?? field.defaultValue ?? ''}
                    onChange={e => setInjectContent(prev => ({ ...prev, [field.key]: e.target.value }))}
                    size="small" fullWidth variant="standard"
                  />
                </div>
                <Tooltip title={t('Link to output type')}>
                  <IconButton size="small" onClick={e => handleOpenLinkMenu(e, field.key)} sx={{ color: theme.palette.primary.main }}>
                    <LinkOutlined sx={{ fontSize: 18 }} />
                  </IconButton>
                </Tooltip>
                <Typography variant="caption" color="text.disabled" sx={{ fontSize: 10, whiteSpace: 'nowrap' }}>
                  {t('Manual')}
                </Typography>
              </div>
            );
          })}
        </>
      )}

      {outputTypes.length > 0 && (
        <>
          <Divider />
          <Typography variant="subtitle2">{t('Outputs')}</Typography>
          <Typography variant="caption" color="text.secondary">
            {t('Data this action provides to downstream events and actions.')}
          </Typography>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {outputTypes.map(type => (
              <Chip
                key={type} size="small"
                icon={<FindingIcon findingType={type} />}
                label={type} variant="outlined"
                sx={{ height: 24, fontSize: 11 }}
              />
            ))}
          </div>
        </>
      )}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: theme.spacing(1), marginTop: 'auto', paddingTop: theme.spacing(2) }}>
        <Button variant="contained" onClick={onCancel}>{t('Cancel')}</Button>
        <Button variant="contained" color="secondary" onClick={handleSubmit} disabled={!title}>
          {t('Add to chain')}
        </Button>
      </div>

      {/* Link menu */}
      <Menu anchorEl={linkMenuAnchor} open={Boolean(linkMenuAnchor)} onClose={handleCloseLinkMenu} slotProps={{ paper: { style: { maxHeight: 300 } } }}>
        {leafTypes.map(ot => (
          <MenuItem key={ot.outputType} onClick={() => handleLinkField(ot.outputType)} sx={{ fontSize: 13 }}>
            <FindingIcon findingType={ot.outputType} />
            <Typography sx={{ ml: 1, fontSize: 13 }}>{ot.outputType}</Typography>
          </MenuItem>
        ))}
      </Menu>
    </div>
  );
};

export default LogicActionPreviewPanel;
