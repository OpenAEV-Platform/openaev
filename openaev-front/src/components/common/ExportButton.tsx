import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { FileDownloadOutlined } from '@mui/icons-material';
import { CSVLink } from 'react-csv';

import { exportCsvMapper } from '../../actions/mapper/mapper-actions';
import { type TagHelper } from '../../actions/tags/tag-helper';
import { useHelper } from '../../store';
import { type SearchPaginationInput } from '../../utils/api-types';
import { exportData } from '../../utils/Environment';
import { download } from '../../utils/utils';
import { useFormatter } from '../i18n';

export interface ExportProps<T> {
  exportType: string;
  exportKeys: string[];
  exportData: T[];
  exportFileName?: string;
  searchPaginationInput?: SearchPaginationInput;
}

interface Props<T> {
  totalElements: number;
  exportProps: ExportProps<T>;
  exportCsvMapperFunction?: (searchPaginationInput: SearchPaginationInput | undefined) => Promise<{
    data: string;
    filename: string;
  }>;
}

const ExportButton = <T extends object>({ totalElements, exportProps, exportCsvMapperFunction }: Props<T>) => {
  // Standard hooks
  const { t } = useFormatter();
  // Fetching data
  const { tagsMap } = useHelper((helper: TagHelper) => ({ tagsMap: helper.getTagsMap() }));

  const exportCsvMapperAction = () => {
    const exportPromise = exportCsvMapperFunction
      ? exportCsvMapperFunction(exportProps.searchPaginationInput)
      : exportCsvMapper(exportProps.exportType, exportProps.searchPaginationInput);
    exportPromise.then(
      (result: {
        data: string;
        filename: string;
      }) => {
        download(result.data, result.filename, 'text/csv');
      },
    );
  };

  const exportBtn = (enableOnClick: boolean) => (
    <Tooltip>
      <TooltipTrigger asChild>
        <span style={{ display: 'inline-flex' }}>
          <IconButton
            priority="secondary"
            size="md"
            aria-label={t('Export this list')}
            icon={<FileDownloadOutlined fontSize="small" />}
            disabled={totalElements === 0}
            onClick={enableOnClick ? exportCsvMapperAction : undefined}
          />
        </span>
      </TooltipTrigger>
      <TooltipContent>{t('Export this list')}</TooltipContent>
    </Tooltip>
  );

  if (
    exportProps.exportType === 'ENDPOINTS'
    || exportProps.exportType === 'INJECTOR_CONTRACTS'
  ) {
    return exportBtn(true);
  }

  return (
    <CSVLink
      data={exportData(
        exportProps.exportType,
        exportProps.exportKeys,
        exportProps.exportData,
        tagsMap,
      )}
      filename={exportProps.exportFileName}
    >
      {exportBtn(false)}
    </CSVLink>
  );
};

export default ExportButton;
