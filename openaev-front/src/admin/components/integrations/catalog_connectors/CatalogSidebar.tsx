import { Button } from '@filigran/design-system';
import {
  CloudOutlined,
  GroupsOutlined,
  Inventory2Outlined,
  Key,
  OnlinePredictionOutlined,
  SmartButtonOutlined,
  type SvgIconComponent,
  TerminalOutlined,
  VerifiedOutlined,
} from '@mui/icons-material';
import { useMemo } from 'react';

import { type FacetSection, FacetSidebar } from '../../../../components/common/facets/FacetFilters';
import { useFormatter } from '../../../../components/i18n';
import { isFeatureEnabled } from '../../../../utils/utils';
import {
  type CatalogFacetFilters,
  type ConnectorItem,
  countByPredicate,
  DEPLOYMENT_BUILT_IN,
  DEPLOYMENT_EXTERNAL,
  type FacetGroupId,
  filterConnectors,
  hasActiveFacetFilters,
  prettifyUseCase,
  STATUS_COMMUNITY,
  STATUS_FILIGRAN,
} from './catalog-facets';
import useCaseIcon from './use-case-icons';

interface FacetGroup {
  id: FacetGroupId;
  label: string;
  rows: {
    value: string;
    label: string;
    count: number;
    icon?: SvgIconComponent;
  }[];
}

interface Props {
  connectors: ConnectorItem[];
  filters: CatalogFacetFilters;
  keyword: string;
  onToggleFacet: (groupId: FacetGroupId, value: string) => void;
  onClearAll: () => void;
}

const CatalogSidebar = ({ connectors, filters, keyword, onToggleFacet, onClearAll }: Props) => {
  const { t } = useFormatter();
  const isCredentialAssetEnabled = isFeatureEnabled('CREDENTIAL_ASSET');

  const groups: FacetGroup[] = useMemo(() => {
    // Faceted-search count semantics: each group is counted against items
    // filtered by every group EXCEPT itself (plus the search keyword).
    const baseFor = (groupId: FacetGroupId) => filterConnectors(connectors, filters, keyword, groupId);

    const typesBase = baseFor('types');
    const useCasesBase = baseFor('useCases');
    const statusBase = baseFor('status');
    const deploymentBase = baseFor('deployment');

    const allUseCases = Array.from(new Set(connectors.flatMap(c => c.useCases))).sort((a, b) => a.localeCompare(b));

    return [
      {
        id: 'types' as const,
        label: t('Type'),
        rows: [
          {
            value: 'COLLECTOR',
            label: t('Collector'),
            icon: OnlinePredictionOutlined,
            count: countByPredicate(typesBase, c => c.type === 'COLLECTOR'),
          },
          {
            value: 'INJECTOR',
            label: t('Injector'),
            icon: SmartButtonOutlined,
            count: countByPredicate(typesBase, c => c.type === 'INJECTOR'),
          },
          {
            value: 'EXECUTOR',
            label: t('Executor'),
            icon: TerminalOutlined,
            count: countByPredicate(typesBase, c => c.type === 'EXECUTOR'),
          },
          ...isCredentialAssetEnabled
            ? [{
                value: 'SECRETS_PROVIDER',
                label: t('Secrets Provider'),
                icon: Key,
                count: countByPredicate(typesBase, c => c.type === 'SECRETS_PROVIDER'),
              }]
            : [],
        ],
      },
      {
        id: 'useCases' as const,
        label: t('Use cases'),
        rows: allUseCases.map(useCase => ({
          value: useCase,
          label: prettifyUseCase(useCase),
          icon: useCaseIcon(useCase),
          count: countByPredicate(useCasesBase, c => c.useCases.includes(useCase)),
        })),
      },
      // No "Deployed" status facet: it would be redundant with the Deployed tab.
      {
        id: 'status' as const,
        label: t('Status'),
        rows: [
          {
            value: STATUS_FILIGRAN,
            label: t('Supported by Filigran'),
            icon: VerifiedOutlined,
            count: countByPredicate(statusBase, c => c.verified),
          },
          {
            value: STATUS_COMMUNITY,
            label: t('Supported by Community'),
            icon: GroupsOutlined,
            count: countByPredicate(statusBase, c => !c.verified),
          },
        ],
      },
      {
        id: 'deployment' as const,
        label: t('Deployment'),
        rows: [
          {
            value: DEPLOYMENT_EXTERNAL,
            label: t('External'),
            icon: CloudOutlined,
            count: countByPredicate(deploymentBase, c => c.external),
          },
          {
            value: DEPLOYMENT_BUILT_IN,
            label: t('Built-in'),
            icon: Inventory2Outlined,
            count: countByPredicate(deploymentBase, c => !c.external),
          },
        ],
      },
    ].filter(group => group.rows.length > 0);
  }, [connectors, filters, keyword, t]);

  const sections: FacetSection[] = groups.map(group => ({
    id: group.id,
    label: group.label,
    rows: group.rows.map(({ icon: RowIcon, ...row }) => ({
      ...row,
      icon: RowIcon && (() => <RowIcon fontSize="small" />),
      checked: filters[group.id].includes(row.value),
      onToggle: () => onToggleFacet(group.id, row.value),
    })),
  }));

  return (
    <FacetSidebar
      action={hasActiveFacetFilters(filters) && (
        <Button type="button" priority="tertiary" size="sm" onClick={onClearAll}>{t('Clear all')}</Button>
      )}
      sections={sections}
    />
  );
};

export default CatalogSidebar;
