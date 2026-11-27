import { ButtonGroup, ButtonGroupItem, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { GridViewOutlined, ViewListOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import MarketplaceHero from '../../../../components/common/marketplace/page/MarketplaceHero';
import { type ViewMode } from '../../../../components/common/marketplace/toolbar/useMarketplaceView';
import { useFormatter } from '../../../../components/i18n';

interface Props {
  activeFindings: number;
  viewMode: ViewMode;
  onViewModeChange: (mode: ViewMode) => void;
}

const FindingHero: FunctionComponent<Props> = ({ activeFindings, viewMode, onViewModeChange }) => {
  const { t } = useFormatter();
  const viewModeChange = (mode: string) => {
    onViewModeChange(mode === 'list' ? 'list' : 'grid');
  };

  return (
    <MarketplaceHero
      title={t('Findings')}
      stats={[{
        id: 'active',
        label: t('Findings'),
        value: activeFindings,
        icon: 'radar',
        to: '/admin/findings',
      }]}
      action={(
        <ButtonGroup
          value={viewMode}
          size="md"
          usecase="isolated"
          onValueChange={viewModeChange}
          aria-label={t('View mode')}
        >
          <Tooltip>
            <TooltipTrigger asChild>
              <ButtonGroupItem
                value="grid"
                aria-label={t('Grid view')}
                icon={<GridViewOutlined fontSize="small" />}
              />
            </TooltipTrigger>
            <TooltipContent>{t('Grid view')}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <ButtonGroupItem
                value="list"
                aria-label={t('List view')}
                icon={<ViewListOutlined fontSize="small" />}
              />
            </TooltipTrigger>
            <TooltipContent>{t('List view')}</TooltipContent>
          </Tooltip>
        </ButtonGroup>
      )}
    />
  );
};

export default FindingHero;
