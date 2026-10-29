import { AppsOutlined, CampaignOutlined, FilterAltOutlined, FlagOutlined, GpsFixedOutlined, PublicOutlined, SwapHorizOutlined } from '@mui/icons-material';
import { CrosshairsQuestion, DatabaseExportOutline, ShieldBugOutline } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

interface ItemCategoryProps {
  category: string;
  label?: string;
}

// One icon per scenario category, chosen to read at a glance:
// - global-crisis: a globe (world-wide crisis)
// - attack-scenario: a crosshair on target (a targeted attack)
// - media-pressure: a megaphone (public / press pressure)
// - data-exfiltration: data leaving a database (export)
// - capture-the-flag: a flag (CTF)
// - vulnerability-exploitation: a shield with a bug (exploited weakness)
// - lateral-movement: horizontal swap arrows (host-to-host movement)
// - url-filtering: a filter funnel (web content filtering)
// One size and one ink for every category glyph: it labels, it does not shout.
const ICON_STYLE = {
  fontSize: 16,
  color: 'var(--text-default-secondary)',
};

const renderIcon = (category: string) => {
  switch (category) {
    case 'global-crisis':
      return <PublicOutlined style={ICON_STYLE} />;
    case 'attack-scenario':
      return <GpsFixedOutlined style={ICON_STYLE} />;
    case 'media-pressure':
      return <CampaignOutlined style={ICON_STYLE} />;
    case 'data-exfiltration':
      return <DatabaseExportOutline style={ICON_STYLE} />;
    case 'capture-the-flag':
      return <FlagOutlined style={ICON_STYLE} />;
    case 'vulnerability-exploitation':
      return <ShieldBugOutline style={ICON_STYLE} />;
    case 'lateral-movement':
      return <SwapHorizOutlined style={ICON_STYLE} />;
    case 'url-filtering':
      return <FilterAltOutlined style={ICON_STYLE} />;
    case 'all':
      return <AppsOutlined style={ICON_STYLE} />;
    default:
      return <CrosshairsQuestion style={ICON_STYLE} />;
  }
};

const ItemCategory: FunctionComponent<ItemCategoryProps> = ({
  label,
  category,
}) => {
  return (
    // The gap belongs to the row, not to each of the ten icons.
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 4,
    }}
    >
      {renderIcon(category)}
      {label && (
        <span style={{
          fontSize: 14,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
        >
          {label}
        </span>
      )}
    </div>
  );
};

export default ItemCategory;
