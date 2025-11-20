import { Chip, Tooltip } from '@mui/material';
import PropTypes from 'prop-types';
import { makeStyles } from 'tss-react/mui';

import { type Domain } from '../utils/api-types';
import { truncate } from '../utils/String';
import { useFormatter } from './i18n';

const useStyles = makeStyles()(() => ({
  inline: {
    display: 'inline',
    alignItems: 'center',
    flexWrap: 'nowrap',
    overflow: 'hidden',
  },
  domainChip: {
    height: 25,
    fontSize: 12,
    margin: '0 7px 0 0',
    borderRadius: 4,
  },
  domainChipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
  },
}));

interface ItemsDomainsProps {
  domains: Domain[];
  variant: string;
}

const ItemDomains = ({ domains, variant }: ItemsDomainsProps) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  let truncateLimit = 20;
  let style = classes.domainChip;

  if (variant === 'list') {
    style = `${classes.domainChip} ${classes.domainChipInList}`;
  }
  if (variant === 'reduced-view') {
    style = `${classes.domainChip} ${classes.domainChipInList}`;
    truncateLimit = 12;
  }

  const renderList = () =>
    domains
      .filter(d => d.domain_name !== 'Unclassified')
      .map(domain => (
        <Tooltip key={domain.domain_id} title={domain.domain_name}>
          <Chip
            variant="outlined"
            classes={{ root: style }}
            label={truncate(domain.domain_name, truncateLimit)}
            style={{
              color: domain.domain_color,
              borderColor: domain.domain_color,
              backgroundColor: 'transparent',
            }}
          />
        </Tooltip>
      ));

  const renderSingle = () => {
    const primaryDomain = domains[0];
    if (!primaryDomain || primaryDomain.domain_name === 'Unclassified') return null;

    return (
      <>
        <Tooltip title={primaryDomain.domain_name}>
          <Chip
            variant="outlined"
            classes={{ root: style }}
            label={truncate(primaryDomain.domain_name, truncateLimit)}
            style={{
              color: primaryDomain.domain_color,
              borderColor: primaryDomain.domain_color,
              backgroundColor: 'transparent',
            }}
          />
        </Tooltip>
        {domains.length > 1 && (
          <Tooltip title={t('Additional domains')}>
            <Chip
              variant="outlined"
              classes={{ root: style }}
              label={`+${domains.length - 1}`}
            />
          </Tooltip>
        )}
      </>
    );
  };

  return (
    <div className={classes.inline}>
      {variant === 'list' ? renderList() : renderSingle()}
    </div>
  );
};

ItemDomains.propTypes = {
  domains: PropTypes.arrayOf(PropTypes.string),
  variant: PropTypes.string,
};

export default ItemDomains;
