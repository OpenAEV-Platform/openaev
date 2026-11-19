import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { FileDownloadOutlined } from '@mui/icons-material';
import { type FunctionComponent, useState } from 'react';

import { exportMarkingDefinitions } from '../../../../actions/marking_definitions/marking-definition-actions';
import { useFormatter } from '../../../../components/i18n';
import { type SearchPaginationInput } from '../../../../utils/api-types';
import { download } from '../../../../utils/utils';

interface Props {
  // The search input of the list: the export is what the list shows, without its paging.
  searchPaginationInput: SearchPaginationInput;
  disabled?: boolean;
}

const MarkingDefinitionExportButton: FunctionComponent<Props> = ({ searchPaginationInput, disabled = false }) => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState(false);

  const handleExport = () => {
    setLoading(true);
    exportMarkingDefinitions(searchPaginationInput)
      .then(result => download(result.data, result.filename, 'text/csv'))
      // The API layer already notified the user of a failed call.
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span style={{ display: 'inline-flex' }}>
          <IconButton
            priority="secondary"
            size="md"
            aria-label={t('Export this list')}
            icon={<FileDownloadOutlined fontSize="small" />}
            disabled={disabled || loading}
            onClick={handleExport}
          />
        </span>
      </TooltipTrigger>
      <TooltipContent>{t('Export this list')}</TooltipContent>
    </Tooltip>
  );
};

export default MarkingDefinitionExportButton;
