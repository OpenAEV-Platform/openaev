import { Add, FileDownloadOutlined, InfoOutlined } from '@mui/icons-material';
import { Box, Button, Chip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ChangeEvent, useRef } from 'react';

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
}

const ScopeInventoryBox = ({
  listLabel,
  totalSelected,
  chips,
  onDownloadTemplate,
  onUploadCsv,
}: ScopeInventoryBoxProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const uploadRef = useRef<HTMLInputElement | null>(null);

  const handleOpenUpload = () => uploadRef.current?.click();

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    const file = target.files?.[0];
    if (!file) {
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    try {
      await Promise.resolve(onUploadCsv(formData, file));
    } catch {
      MESSAGING$.notifyError(t('Failed to import CSV file'));
    }
    event.target.value = '';
  };

  return (
    <Box>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
      >
        <Typography variant="h4">
          {`${listLabel} ${t('inventory')} (${totalSelected})`}
        </Typography>
        <div style={{
          display: 'flex',
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
          <Button
            size="small"
            onClick={onDownloadTemplate}
            startIcon={<FileDownloadOutlined />}
          >
            {t('CSV template')}
          </Button>
          <Button
            size="small"
            variant="text"
            onClick={handleOpenUpload}
            startIcon={<Add />}
          >
            {t('Add Bulk CSV')}
          </Button>
        </div>
      </div>

      <Box
        sx={{
          minHeight: 100,
          border: `1px solid ${theme.palette.divider}`,
          borderRadius: 1,
          padding: theme.spacing(2),
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'flex-start',
          alignContent: 'flex-start',
          gap: theme.spacing(1),
        }}
      >
        {chips.length === 0 && (
          <Typography
            variant="body2"
            sx={{ color: 'text.disabled' }}
          >
            {t('No asset selected. Add asset manually or select some in the asset list.')}
          </Typography>
        )}
        {chips.map(chip => (
          <Chip
            key={chip.key}
            label={chip.label}
            size="small"
            onDelete={chip.onDelete}
          />
        ))}
      </Box>

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
