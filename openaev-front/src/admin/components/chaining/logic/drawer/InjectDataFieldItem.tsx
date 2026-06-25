import { KeyboardArrowDown, LinkOff, LinkOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  IconButton,
  Menu,
  MenuItem,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { type FunctionComponent, useMemo, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import useArgumentTypes from '../../../threat_arsenal/form/useArgumentTypes';
import { CONDITION_KEY_TYPES } from '../forms/MapperConditionRow';

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
  onValueChange,
  onLink,
  onUnlink,
  onToggleLocalScope,
}) => {
  const { t } = useFormatter();
  const { subtypesByType } = useArgumentTypes();

  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);

  // Build flattened menu items: types with subtypes become "type.subtype", others stay as-is
  const menuItems = useMemo(() => {
    const items: string[] = [];
    for (const keyType of CONDITION_KEY_TYPES) {
      const subtypes = subtypesByType[keyType];
      if (subtypes && subtypes.length > 0) {
        for (const sub of subtypes) {
          items.push(`${keyType}.${sub}`);
        }
      } else {
        items.push(keyType);
      }
    }
    return items;
  }, [subtypesByType]);

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
              {link.outputType}
            </Typography>
          </Box>

          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
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
          <TextField
            fullWidth
            size="small"
            variant="standard"
            label={fieldLabel}
            placeholder={fieldLabel}
            value={value}
            onChange={e => onValueChange(fieldKey, e.target.value)}
          />
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
            {item}
          </MenuItem>
        ))}
      </Menu>
    </Box>
  );
};

export default InjectDataFieldItem;
