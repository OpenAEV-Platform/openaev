import { KeyboardArrowDown, LinkOff, LinkOutlined } from '@mui/icons-material';
import {
  Box,
  Button,
  ClickAwayListener,
  IconButton,
  Paper,
  Popper,
  Switch,
  Tooltip,
  Typography,
} from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useRef, useState } from 'react';

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
  link: FieldLink | null;
  readOnly?: boolean;
  onLink: (fieldKey: string, link: FieldLink) => void;
  onUnlink: (fieldKey: string) => void;
  onToggleLocalScope: (fieldKey: string, localScope: boolean) => void;
}

/**
 * Chaining-only control that links an inject field to a workflow scope output (primitive types).
 * Rendered as an adornment next to the real inject-form field widget: when a field is linked its
 * value is fed by the upstream step output instead of a static value.
 */
const FieldOutputLink: FunctionComponent<Props> = ({
  panelOpen = true,
  fieldKey,
  fieldLabel,
  link,
  readOnly = false,
  onLink,
  onUnlink,
  onToggleLocalScope,
}) => {
  const { t } = useFormatter();
  const { argumentTypes } = useArgumentTypes();

  // Stable anchor that persists across the "Link an Output" <-> "Edit links" transition.
  const containerRef = useRef<HTMLDivElement>(null);
  const [typeSelectorAnchorEl, setTypeSelectorAnchorEl] = useState<HTMLElement | null>(null);
  const isTypeSelectorOpen = Boolean(typeSelectorAnchorEl);
  const [selectorReady, setSelectorReady] = useState(false);

  const menuItems = useMemo(
    () => (argumentTypes.length > 0 ? argumentTypes : ['text']),
    [argumentTypes],
  );

  const normalizedLinkOutputTypes = useMemo(() => link?.outputTypes ?? [], [link]);

  const openTypeSelector = () => setTypeSelectorAnchorEl(containerRef.current);
  const closeTypeSelector = () => setTypeSelectorAnchorEl(null);

  const handleOutputTypesChange = (nextOutputTypes: string[]) => {
    if (nextOutputTypes.length === 0) {
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

  useEffect(() => {
    if (!isTypeSelectorOpen) {
      setSelectorReady(false);
      return undefined;
    }
    let raf2 = 0;
    const raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => setSelectorReady(true));
    });
    return () => {
      cancelAnimationFrame(raf1);
      cancelAnimationFrame(raf2);
    };
  }, [isTypeSelectorOpen]);

  return (
    <Box ref={containerRef}>
      {link
        ? (
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 1,
            }}
            >
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
              >
                <Typography variant="body2" fontWeight={600}>{fieldLabel}</Typography>
                <Typography variant="body2" color="text.secondary">-</Typography>
                <LinkOutlined fontSize="small" color="primary" />
                <Typography variant="body2" color="primary">
                  {normalizedLinkOutputTypes.map(type => t(formatPrimitiveTypeLabel(type))).join(', ')}
                </Typography>
              </Box>
              {!readOnly && (
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
                    <IconButton size="small" onClick={() => onUnlink(fieldKey)}>
                      <LinkOff fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
              )}
            </Box>
          )
        : (
            !readOnly && (
              <Box sx={{
                display: 'flex',
                justifyContent: 'flex-end',
              }}
              >
                <Button
                  size="small"
                  variant="text"
                  color="primary"
                  endIcon={<KeyboardArrowDown />}
                  onClick={openTypeSelector}
                  sx={{
                    whiteSpace: 'nowrap',
                    textTransform: 'none',
                  }}
                >
                  {t('Link an Output')}
                </Button>
              </Box>
            )
          )}

      <Popper
        open={isTypeSelectorOpen}
        anchorEl={typeSelectorAnchorEl}
        placement="bottom-end"
        sx={{
          zIndex: 1300,
          width: 320,
        }}
      >
        <ClickAwayListener onClickAway={closeTypeSelector}>
          <Paper
            elevation={4}
            sx={{
              p: 1.5,
              minHeight: 56,
            }}
          >
            {selectorReady && (
              <AutocompleteField
                autoFocus
                label={t('Primitive types')}
                multiple
                disableCloseOnSelect
                disableOptionTooltip
                options={menuItems.map(type => ({
                  id: type,
                  label: t(formatPrimitiveTypeLabel(type)),
                }))}
                value={normalizedLinkOutputTypes.filter(type => menuItems.includes(type))}
                onInputChange={() => {}}
                onChange={handleOutputTypesChange}
              />
            )}
          </Paper>
        </ClickAwayListener>
      </Popper>
    </Box>
  );
};

export default FieldOutputLink;
