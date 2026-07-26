import { DnsOutlined, Groups3Outlined, PersonOutlined, SmartToyOutlined } from '@mui/icons-material';
import { Chip, Tooltip } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AssetCategory } from '../admin/components/assets/asset-categories';
import AssetCategoryIcon from '../admin/components/assets/AssetCategoryIcon';
import { type TargetSimple } from '../utils/api-types';
import { getRemainingItemsCount, getVisibleItems, truncate } from '../utils/String';
import { useFormatter } from './i18n';
import PlatformIcon from './PlatformIcon';

const useStyles = makeStyles()(theme => ({
  inline: { display: 'flex' },
  target: {
    fontSize: 12,
    height: 20,
    float: 'left',
    marginRight: 4,
    borderRadius: 4,
  },
  clickable: {
    'cursor': 'pointer',
    '&:hover': {
      borderColor: theme.palette.primary.main,
      color: theme.palette.primary.main,
    },
  },
  tooltipTable: {
    'borderCollapse': 'collapse',
    '& th': {
      textAlign: 'left',
      textTransform: 'uppercase',
      fontSize: 10,
      fontWeight: 600,
      opacity: 0.7,
      padding: '2px 12px 4px 0',
    },
    '& td': {
      fontSize: 12,
      padding: '2px 12px 2px 0',
      verticalAlign: 'middle',
    },
  },
  tooltipName: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
  },
  tooltipMore: {
    fontSize: 11,
    opacity: 0.7,
    marginTop: 4,
  },
}));

// Cap the rich tooltip so an inject targeting hundreds of assets stays readable.
const MAX_TOOLTIP_ROWS = 20;

const typeLabelKey = (type: string | undefined): string => {
  switch (type) {
    case 'AGENT':
    case 'AGENTS':
      return 'Agent';
    case 'ASSETS':
    case 'ENDPOINTS':
      return 'Asset';
    case 'ASSETS_GROUPS':
      return 'Asset group';
    case 'AI_TARGETS':
      return 'AI target';
    case 'PLAYERS':
      return 'Player';
    case 'TEAMS':
      return 'Team';
    case 'MANUAL':
      return 'Manual';
    default:
      return type ?? '-';
  }
};

interface Props {
  targets: TargetSimple[] | undefined;
  variant?: string;
  // When provided, each target chip becomes a clickable link to the resolved
  // URL (used e.g. to pivot from a finding to its impacted endpoint). Returning
  // undefined keeps that individual chip non-clickable.
  getTargetLink?: (target: TargetSimple) => string | undefined;
}

const ItemTargets: FunctionComponent<Props> = ({
  targets,
  variant,
  getTargetLink,
}) => {
  // Standard hooks
  const { classes, cx } = useStyles();
  const { t } = useFormatter();
  let truncateLimit = 15;
  if (variant === 'reduced-view') {
    truncateLimit = 6;
  }

  // Extract the first two targets as visible chips
  const visibleTargets = getVisibleItems(targets, 1);
  const remainingTargets = targets?.slice(visibleTargets?.length ?? 0) ?? [];
  const remainingTargetsCount = getRemainingItemsCount(targets, visibleTargets);

  if (!targets || targets.length === 0) {
    return '-';
  }

  // Mirrors the detail-page TargetIcon glyph selection so a target reads identically in list
  // chips and on its result page: host-like assets keep their OS platform brand icon, every other
  // asset category is represented by its taxonomy glyph (web application, cloud, AI target, ...).
  const getIcon = (target: TargetSimple) => {
    switch (target.target_type) {
      case 'ASSETS':
      case 'ENDPOINTS': {
        const category = target.target_category as AssetCategory | undefined;
        const platform = target.target_subtype;
        // No category (legacy data) or host-like category: the OS platform is the meaningful
        // glyph. PlatformIcon renders nothing for Unknown, which would leave an empty chip, so
        // Unknown falls through to the category glyph instead.
        if (platform && platform !== 'Unknown' && (!category || category === 'HOST' || category === 'MOBILE_DEVICE')) {
          return <PlatformIcon platform={platform} width={14} />;
        }
        return <AssetCategoryIcon category={category ?? null} style={{ fontSize: '1rem' }} />;
      }
      case 'ASSETS_GROUPS':
        return <SelectGroup style={{ fontSize: '1rem' }} />;
      case 'AI_TARGETS':
        return <SmartToyOutlined style={{ fontSize: '1rem' }} />;
      case 'MANUAL':
        return <DnsOutlined style={{ fontSize: '1rem' }} />;
      case 'PLAYERS':
        return <PersonOutlined style={{ fontSize: '1rem' }} />;
      default:
        return <Groups3Outlined style={{ fontSize: '1rem' }} />; // Teams
    }
  };

  return (
    <div className={classes.inline}>
      {visibleTargets && visibleTargets.map((target: TargetSimple, index: number) => {
        const link = getTargetLink?.(target);
        return (
          <span key={index}>
            <Tooltip title={target.target_name}>
              <Chip
                variant="outlined"
                key={target.target_id}
                classes={{ root: link ? cx(classes.target, classes.clickable) : classes.target }}
                icon={getIcon(target)}
                label={truncate(target.target_name!, truncateLimit)}
                {...(link
                  ? {
                      component: Link,
                      to: link,
                      clickable: true,
                    }
                  : {})}
              />
            </Tooltip>
          </span>
        );
      })}
      {remainingTargetsCount && remainingTargetsCount > 0 && (
        <Tooltip
          slotProps={{ tooltip: { sx: { maxWidth: 480 } } }}
          title={(
            <>
              <table className={classes.tooltipTable}>
                <thead>
                  <tr>
                    <th>{t('Name')}</th>
                    <th>{t('Type')}</th>
                  </tr>
                </thead>
                <tbody>
                  {remainingTargets.slice(0, MAX_TOOLTIP_ROWS).map(target => (
                    <tr key={target.target_id}>
                      <td>
                        <span className={classes.tooltipName}>
                          {getIcon(target)}
                          {truncate(target.target_name ?? '-', 40)}
                        </span>
                      </td>
                      <td>{t(typeLabelKey(target.target_type))}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {remainingTargets.length > MAX_TOOLTIP_ROWS && (
                <div className={classes.tooltipMore}>
                  {`+${remainingTargets.length - MAX_TOOLTIP_ROWS} ${t('more')}`}
                </div>
              )}
            </>
          )}
        >
          <Chip
            variant="outlined"
            classes={{ root: classes.target }}
            label={`+${remainingTargetsCount}`}
          />
        </Tooltip>
      )}
    </div>
  );
};

export default ItemTargets;
