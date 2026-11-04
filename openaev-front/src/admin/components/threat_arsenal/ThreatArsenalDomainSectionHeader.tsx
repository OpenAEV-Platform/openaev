import { CategoryOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import MarketplaceSectionHeader from '../../../components/common/marketplace/MarketplaceSectionHeader';
import { useFormatter } from '../../../components/i18n';
import { DOMAIN_ICON_MAP } from '../common/domains/DomainsIcons';
import { type DomainSection } from './threatArsenalDomainSections';

interface Props { section: DomainSection }

const ThreatArsenalDomainSectionHeader: FunctionComponent<Props> = ({ section }) => {
  const { t } = useFormatter();
  const icon = section.domain && DOMAIN_ICON_MAP[section.domain.domain_name];
  return (
    <MarketplaceSectionHeader
      icon={icon ? icon() : <CategoryOutlined />}
      label={section.domain ? t(section.domain.domain_name) : t('Other')}
      count={section.actions.length}
    />
  );
};

export default ThreatArsenalDomainSectionHeader;
