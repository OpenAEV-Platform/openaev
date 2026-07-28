import { KeyboardArrowDown, LinkOff, LinkOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  ClickAwayListener,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import AutocompleteField from '../../../../../components/fields/AutocompleteField';
import { useFormatter } from '../../../../../components/i18n';
import { formatPrimitiveTypeLabel } from '../../../../../utils/String';
import useArgumentTypes from '../../../threat_arsenal/form/useArgumentTypes';

export interface FieldLink {
  outputTypes: string[];
  localScope: boolean;
}

interface Props {
  panelOpen?: boolean;
  fieldKey: string;
  fieldLabel: string;
  value: string;
  defaultValue?: string;
  link: FieldLink | null;
  readOnly?: boolean;
  noLink?: boolean;
  choices?: Record<string, string>;
  onValueChange: (fieldKey: string, value: string) => void;
  onLink: (fieldKey: string, link: FieldLink) => void;
  onUnlink: (fieldKey: string) => void;
  onToggleLocalScope: (fieldKey: string, localScope: boolean) => void;
}

const InjectDataFieldItem: FunctionComponent<Props> = ({
  panelOpen = true,
  fieldKey,
  fieldLabel,
  value,
  defaultValue,
  link,
  readOnly = false,
  noLink = false,
  choices,
  onValueChange,
  onLink,
  onUnlink,
  onToggleLocalScope,
}) => {
  const { t } = useFormatter();
  const { argumentTypes } = useArgumentTypes();

  const [isTypeSelectorOpen, setIsTypeSelectorOpen] = useState(false);

  const menuItems = useMemo(
    () => (argumentTypes.length > 0 ? argumentTypes : ['text']),
    [argumentTypes],
  );

  const normalizedLinkOutputTypes = useMemo(() => {
    if (!link) return [];
    return link.outputTypes ?? [];
  }, [link]);

  const openTypeSelector = () => setIsTypeSelectorOpen(prev => !prev);
  const closeTypeSelector = () => setIsTypeSelectorOpen(false);

  const handleOutputTypesChange = (nextOutputTypes: string[]) => {
    if (nextOutputTypes.length === 0) {
      closeTypeSelector();
      onUnlink(fieldKey);
      return;
    }

    onLink(fieldKey, {
      outputTypes: nextOutputTypes,
      localScope: link?.localScope ?? false,
    });
  };

  useEffect(() => {
    if (!panelOpen) {
      closeTypeSelector();
    }
  }, [panelOpen]);

  return (
    <Box
      sx={{
        backgroundColor: 'background.paper',
        borderRadius: 1,
        px: 2,
        py: 2,
      }}
    >
      {/* Field name + link controls: only when linked */}
      {link && (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
            <Typography variant="body2" fontWeight={600}>
              {fieldLabel}
            </Typography>
            {defaultValue && (
              <Typography variant="body2" color="text.secondary">
                {`(default: ${defaultValue})`}
              </Typography>
            )}
            <Typography variant="body2" color="text.secondary">-</Typography>
            <LinkOutlined fontSize="small" color="primary" />
            <Typography variant="body2" color="primary">
              {normalizedLinkOutputTypes.map(type => t(formatPrimitiveTypeLabel(type))).join(', ')}
            </Typography>
          </Box>

          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
            {!readOnly && (
              <>
                <Switch
                  size="small"
                  checked={link.localScope}
                  onChange={(_, checked) => onToggleLocalScope(fieldKey, checked)}
                  color="primary"
                />
                <Typography variant="caption" color="text.secondary">
                  {t('Limit to Local Scope')}
                </Typography>
                <Button
                  size="small"
                  variant="text"
                  color="primary"
                  endIcon={<KeyboardArrowDown />}
                  onClick={openTypeSelector}
                >
                  {t('Edit links')}
                </Button>
                <Tooltip title={t('Unlink')}>
                  <IconButton
                    size="small"
                    onClick={() => onUnlink(fieldKey)}
                  >
                    <LinkOff fontSize="small" />
                  </IconButton>
                </Tooltip>
              </>
            )}
          </Box>
        </Box>
      )}

      {/* Default value input + Link button: only when no link */}
      {!link && (
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          marginBottom: 1,
        }}
        >
          {choices
            ? (
                <FormControl fullWidth size="small">
                  <InputLabel id={`select-label-${fieldKey}`}>{fieldLabel}</InputLabel>
                  <Select
                    labelId={`select-label-${fieldKey}`}
                    label={fieldLabel}
                    value={value || ''}
                    onChange={e => onValueChange(fieldKey, e.target.value as string)}
                    renderValue={selected =>
                      selected ? (choices[selected as string] ?? selected) : ''}
                  >
                    {Object.entries(choices).map(([k, label]) => (
                      <MenuItem key={k} value={k} dense>
                        {label}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              )
            : (
                <TextField
                  fullWidth
                  size="small"
                  variant="standard"
                  label={fieldLabel}
                  placeholder={fieldLabel}
                  value={value}
                  onChange={e => onValueChange(fieldKey, e.target.value)}
                />
              )}
          {!noLink && (
            <Button
              size="small"
              variant="text"
              color="primary"
              endIcon={<KeyboardArrowDown />}
              onClick={openTypeSelector}
              sx={{
                whiteSpace: 'nowrap',
                textTransform: 'none',
                paddingInline: 2,
              }}
            >
              {t('Link an Output')}
            </Button>
          )}
        </Box>
      )}

      {isTypeSelectorOpen && (
        <ClickAwayListener onClickAway={closeTypeSelector}>
          <Box
            sx={{
              width: 320,
              px: 1.5,
              py: 1,
              ml: 'auto',
            }}
          >
            <AutocompleteField
              label={t('Primitive types')}
              variant="standard"
              multiple
              options={menuItems.map(type => ({
                id: type,
                label: t(formatPrimitiveTypeLabel(type)),
              }))}
              value={normalizedLinkOutputTypes.filter(type => menuItems.includes(type))}
              onInputChange={() => {}}
              onChange={(nextOutputTypes) => {
                if (nextOutputTypes.length === 0) {
                  closeTypeSelector();
                  onUnlink(fieldKey);
                  return;
                }
                handleOutputTypesChange(nextOutputTypes);
                closeTypeSelector();
              }}
            />
          </Box>
        </ClickAwayListener>
      )}
    </Box>
  );
};

export default InjectDataFieldItem;
