import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import EntityCard from '../../../../components/common/card/EntityCard';
import ItemTags from '../../../../components/ItemTags';
import { securityPlatformTypeLabel } from '../../../../components/securityPlatformType';
import { SECURITY_PLATFORM_BASE_URL } from '../../../../constants/BaseUrls';
import { type SecurityPlatform } from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import SecurityPlatformPopover from './SecurityPlatformPopover';
import isConnectorManaged from './securityPlatformUtils';

interface Props {
  securityPlatform: SecurityPlatform;
  onUpdate: (result: SecurityPlatform) => void;
  onDelete: (result: string) => void;
  openEditOnInit?: boolean;
}

const SecurityPlatformCard: FunctionComponent<Props> = ({
  securityPlatform,
  onUpdate,
  onDelete,
  openEditOnInit,
}) => {
  const theme = useTheme();

  return (
    <EntityCard
      testId="security-platform-card"
      to={`${SECURITY_PLATFORM_BASE_URL}/${securityPlatform.asset_id}`}
      icon={(
        <img
          src={buildTenantApiPath(`/api/images/security_platforms/id/${securityPlatform.asset_id}/${theme.palette.mode}?${Date.now()}`)}
          alt={securityPlatform.asset_name}
          style={{
            width: 32,
            height: 32,
            borderRadius: 4,
          }}
        />
      )}
      overline={securityPlatformTypeLabel(securityPlatform.security_platform_type)}
      title={securityPlatform.asset_name}
      description={securityPlatform.asset_description}
      menu={(
        <SecurityPlatformPopover
          securityPlatform={{
            ...securityPlatform,
            type: 'static',
          }}
          onUpdate={onUpdate}
          onDelete={onDelete}
          openEditOnInit={openEditOnInit}
          disabled={isConnectorManaged(securityPlatform)}
        />
      )}
      footerStart={<ItemTags variant="reduced-view" tags={securityPlatform.asset_tags} />}
    />
  );
};

export default SecurityPlatformCard;
