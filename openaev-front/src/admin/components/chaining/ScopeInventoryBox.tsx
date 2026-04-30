import { Add, Close, FileDownloadOutlined, InfoOutlined } from '@mui/icons-material';
import { Box, Button, Chip, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ChangeEvent, type KeyboardEvent, useMemo, useRef, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { MESSAGING$ } from '../../../utils/Environment';

interface InventoryChip {
  key: string;
  label: string;
  onDelete: () => void;
}

interface ScopeInventoryBoxProps {
  listLabel: string;
  totalSelected: number;
  chips: InventoryChip[];
  onDownloadTemplate: () => void;
  onUploadCsv: (formData: FormData, file: File) => Promise<void> | void;
  onAddManual: (values: string[]) => void;
  onClearAll: () => void;
}

const ScopeInventoryBox = ({
  listLabel,
  totalSelected,
  chips,
  onDownloadTemplate,
  onUploadCsv,
  onAddManual,
  onClearAll,
}: ScopeInventoryBoxProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const uploadRef = useRef<HTMLInputElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [inputValue, setInputValue] = useState('');

  const placeholderForInputValues = chips.length === 0 && inputValue.length === 0
    ? t('No asset selected. Add asset manually by typing IPs, CIDRs or hostnames or select some in the asset list.')
    : t('Type IPs, CIDRs or hostnames...');

  const parsedValues = useMemo(
    () => inputValue.split(',').map(v => v.trim()).filter(v => v.length > 0),
    [inputValue],
  );

  const handleOpenUpload = () => uploadRef.current?.click();

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    const file = target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    try {
      await Promise.resolve(onUploadCsv(formData, file));
    } catch {
      MESSAGING$.notifyError(t('Failed to import CSV file'));
    }
    event.target.value = '';
  };

  const commitValues = () => {
    if (parsedValues.length === 0) return;
    onAddManual(parsedValues);
    setInputValue('');
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      commitValues();
    }
  };

  return (
    <Box>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: theme.spacing(1),
      }}
      >
        <Typography variant="h4">
          {`${listLabel} ${t('inventory')} (${totalSelected})`}
        </Typography>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          <input
            ref={uploadRef}
            type="file"
            style={{ display: 'none' }}
            accept=".csv,text/csv"
            onChange={handleFileChange}
          />
          <Button size="small" onClick={onDownloadTemplate} startIcon={<FileDownloadOutlined />}>
            {t('CSV template')}
          </Button>
          <Button size="small" variant="text" onClick={handleOpenUpload} startIcon={<Add />}>
            {t('Add Bulk CSV')}
          </Button>
        </div>
      </div>

      <Box
        sx={{
          position: 'relative',
          minHeight: 100,
          border: `1px solid ${theme.palette.divider}`,
          borderRadius: 1,
          padding: 2,
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'flex-start',
          alignContent: 'flex-start',
          gap: 1,
          cursor: 'text',
        }}
        onClick={() => inputRef.current?.focus()}
      >
        {chips.length > 0 && (
          <Tooltip title={t('Clear all')}>
            <IconButton
              size="small"
              color="primary"
              onClick={(e) => {
                e.stopPropagation();
                onClearAll();
              }}
              sx={{
                position: 'absolute',
                top: 4,
                right: 4,
              }}
            >
              <Close fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        {chips.map(chip => (
          <Chip key={chip.key} label={chip.label} size="small" onDelete={chip.onDelete} />
        ))}
        <input
          ref={inputRef}
          value={inputValue}
          onChange={e => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholderForInputValues}
          style={{
            border: 'none',
            outline: 'none',
            background: 'transparent',
            color: 'inherit',
            font: 'inherit',
            minWidth: 180,
            flexGrow: 1,
          }}
        />
      </Box>

      {parsedValues.length > 0 && (
        <Paper
          variant="outlined"
          sx={{
            mt: 0.5,
            padding: 1.5,
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: 1,
            borderColor: theme.palette.primary.main,
          }}
        >
          <Typography
            variant="body2"
            sx={{
              color: 'text.secondary',
              whiteSpace: 'nowrap',
              cursor: 'pointer',
              textDecoration: 'underline',
            }}
            onClick={commitValues}
          >
            {t('Tap enter or click here to add:')}
          </Typography>
          {parsedValues.map((val, idx) => (
            <Chip
              key={`${val}-${idx}`}
              label={val}
              size="small"
              color="primary"
              variant="outlined"
              onDelete={(e) => {
                e.stopPropagation();
                const updated = parsedValues.filter((_, i) => i !== idx);
                setInputValue(updated.join(', '));
              }}
            />
          ))}
        </Paper>
      )}

      <Typography
        variant="body2"
        sx={{
          color: 'text.disabled',
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          mt: 1,
        }}
      >
        <InfoOutlined fontSize="small" color="primary" />
        {t('Add multiple items at once by separating them with commas.')}
      </Typography>
    </Box>
  );
};

export default ScopeInventoryBox;
