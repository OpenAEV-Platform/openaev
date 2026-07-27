import { KeyboardArrowDown, LinkOff, LinkOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  FormControl,
  IconButton,
  InputLabel,
  Menu,
  MenuItem,
  Select,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { type FunctionComponent, useMemo, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { formatPrimitiveTypeLabel } from '../../../../../utils/String';
import useArgumentTypes from '../../../threat_arsenal/form/useArgumentTypes';

export interface FieldLink {
  outputType: string;
  localScope: boolean;
}

interface Props {
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

  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);

  const menuItems = useMemo(
    () => (argumentTypes.length > 0 ? argumentTypes : ['text']),
    [argumentTypes],
  );

  const handleCloseMenu = () => {
    setMenuAnchor(null);
  };

  const handleSelectType = (outputType: string) => {
    onLink(fieldKey, {
      outputType,
      localScope: false,
    });
    handleCloseMenu();
  };

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
              {t(formatPrimitiveTypeLabel(link.outputType))}
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
              onClick={event => setMenuAnchor(event.currentTarget)}
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

      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleCloseMenu}
      >
        {menuItems.map(item => (
          <MenuItem
            key={item}
            onClick={() => handleSelectType(item)}
            dense
            sx={{ gap: 1 }}
          >
            <LinkOutlined fontSize="small" color="primary" />
            {t(formatPrimitiveTypeLabel(item))}
          </MenuItem>
        ))}
      </Menu>
    </Box>
  );
};

export default InjectDataFieldItem;
