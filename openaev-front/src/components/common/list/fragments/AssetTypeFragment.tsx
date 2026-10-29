import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

import { type AssetCategory, humanizeEnum } from '../../../../admin/components/assets/asset-categories';
import AssetCategoryIcon from '../../../../admin/components/assets/AssetCategoryIcon';
import { useFormatter } from '../../../i18n';

type Props = {
  type?: string;
  category?: AssetCategory | null;
};

const AssetTypeFragment = (props: Props) => {
  const { t } = useFormatter();
  // The asset category is the meaningful business descriptor (Host, Web application, AI target,
  // ...). The raw discriminator type ("Endpoint") is only a storage detail - agentless web
  // applications are persisted as endpoints - so it is used as a last-resort fallback only.
  const label = props.category ? t(humanizeEnum(props.category)) : props.type;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Chip startIcon={<AssetCategoryIcon category={props.category} sx={{ fontSize: 14 }} />} label={label ?? ''} />
      </TooltipTrigger>
      {label && <TooltipContent>{label}</TooltipContent>}
    </Tooltip>
  );
};

export default AssetTypeFragment;
