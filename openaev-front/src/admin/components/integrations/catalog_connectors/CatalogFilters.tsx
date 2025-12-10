import { FilterListOffOutlined } from '@mui/icons-material';
import { Autocomplete, IconButton, TextField, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import SearchFilter from '../../../../components/SearchFilter';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import useSearchAnFilter from '../../../../utils/SortingFiltering';

const useStyles = makeStyles()(theme => ({
  filters: {
    display: 'flex',
    gap: theme.spacing(1),
  },
}));

type CatalogFiltersProps = {
  connectors: CatalogConnectorOutput[];
  onFiltered: (filtered: CatalogConnectorOutput[]) => void;
};

const connectorTypes = [
  {
    label: 'Collector',
    value: 'COLLECTOR',
  },
  {
    label: 'Injector',
    value: 'INJECTOR',
  },
  {
    label: 'Executor',
    value: 'EXECUTOR',
  },
];

const CatalogFilters = ({ connectors, onFiltered }: CatalogFiltersProps) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { classes } = useStyles();

  const searchColumns = ['title', 'short_description'];

  const filtering = useSearchAnFilter(
    'catalog_connector',
    'title',
    searchColumns,
  );
  const [filters, setFilters] = useState({ type: '' });

  const hasActiveFilters = Boolean(filters.type);

  useEffect(() => {
    let result = filtering.filterAndSort(connectors);
    if (filters.type) {
      result = result.filter((c: CatalogConnectorOutput) => c.catalog_connector_type === filters.type);
    }
    onFiltered(result);
  }, [connectors, filters, filtering.keyword]);

  const handleFilterChange = (key: string, value: string) => {
    setFilters(prev => ({
      ...prev,
      [key]: value,
    }));
  };

  const handleClearFilters = () => {
    setFilters({ type: '' });
    filtering.handleSearch('');
  };

  return (
    <div className={classes.filters}>
      <SearchFilter
        variant="small"
        onChange={filtering.handleSearch}
        keyword={filtering.keyword}
      />

      <Autocomplete
        size="small"
        sx={{
          width: 200,
          backgroundColor: theme.palette.background.paper,
        }}
        options={connectorTypes}
        value={connectorTypes.find(o => o.value === filters.type) || null}
        onChange={(e, opt) => handleFilterChange('type', opt?.value || '')}
        getOptionLabel={opt => opt.label}
        isOptionEqualToValue={(opt, val) => opt.value === val.value}
        renderInput={params => (
          <TextField {...params} label="Type" placeholder="Type" variant="outlined" />
        )}
        clearOnEscape
      />

      <Tooltip title={t('Clear filters')}>
        <IconButton
          style={{ maxHeight: 40 }}
          color={hasActiveFilters ? 'primary' : 'default'}
          onClick={handleClearFilters}
          size="small"
          disabled={!hasActiveFilters}
        >
          <FilterListOffOutlined fontSize="small" />
        </IconButton>
      </Tooltip>

    </div>
  );
};

export default CatalogFilters;
