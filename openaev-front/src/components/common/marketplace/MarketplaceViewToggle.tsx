import { ButtonGroup, ButtonGroupItem, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { GridViewOutlined, ViewListOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../i18n';

interface Props {
  value: 'grid' | 'list';
  onChange: (value: string) => void;
}

const MarketplaceViewToggle: FunctionComponent<Props> = ({ value, onChange }) => {
  const { t } = useFormatter();
  return (
    <ButtonGroup
      value={value}
      size="md"
      usecase="isolated"
      onValueChange={onChange}
      aria-label={t('View mode')}
    >
      <Tooltip>
        <TooltipTrigger asChild>
          <ButtonGroupItem value="grid" aria-label={t('Grid view')} icon={<GridViewOutlined fontSize="small" />} />
        </TooltipTrigger>
        <TooltipContent>{t('Grid view')}</TooltipContent>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <ButtonGroupItem value="list" aria-label={t('List view')} icon={<ViewListOutlined fontSize="small" />} />
        </TooltipTrigger>
        <TooltipContent>{t('List view')}</TooltipContent>
      </Tooltip>
    </ButtonGroup>
  );
};

export default MarketplaceViewToggle;
