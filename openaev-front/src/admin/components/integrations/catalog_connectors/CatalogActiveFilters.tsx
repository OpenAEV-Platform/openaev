import { Button, Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import {
  type CatalogFacetFilters,
  DEPLOYMENT_EXTERNAL,
  type FacetGroupId,
  prettifyUseCase,
  STATUS_FILIGRAN,
} from './catalog-facets';

interface ActiveFilterChip {
  groupId: FacetGroupId;
  value: string;
  label: string;
  capitalize?: boolean;
}

interface Props {
  filters: CatalogFacetFilters;
  onToggleFacet: (groupId: FacetGroupId, value: string) => void;
  onClearAll: () => void;
}

const CatalogActiveFilters = ({ filters, onToggleFacet, onClearAll }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const typeLabels: Record<string, string> = {
    COLLECTOR: t('Collector'),
    INJECTOR: t('Injector'),
    EXECUTOR: t('Executor'),
  };

  const chips: ActiveFilterChip[] = [
    ...filters.types.map(value => ({
      groupId: 'types' as const,
      value,
      label: typeLabels[value] ?? value,
    })),
    ...filters.useCases.map(value => ({
      groupId: 'useCases' as const,
      value,
      label: prettifyUseCase(value),
      capitalize: true,
    })),
    ...filters.status.map(value => ({
      groupId: 'status' as const,
      value,
      label: value === STATUS_FILIGRAN ? t('Supported by Filigran') : t('Supported by Community'),
    })),
    ...filters.deployment.map(value => ({
      groupId: 'deployment' as const,
      value,
      label: value === DEPLOYMENT_EXTERNAL ? t('External') : t('Built-in'),
    })),
  ];

  if (chips.length === 0) {
    return null;
  }

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      flexWrap: 'wrap',
      gap: theme.spacing(1),
    }}
    >
      {chips.map(chip => (
        <Chip
          key={`${chip.groupId}-${chip.value}`}
          size="small"
          variant="outlined"
          color="primary"
          sx={{
            borderRadius: 1,
            textTransform: chip.capitalize ? 'capitalize' : 'none',
          }}
          label={chip.label}
          onDelete={() => onToggleFacet(chip.groupId, chip.value)}
        />
      ))}
      <Button size="small" onClick={onClearAll}>
        {t('Clear all')}
      </Button>
    </div>
  );
};

export default CatalogActiveFilters;
