import { HelpOutlined, NewspaperOutlined, OndemandVideoOutlined } from '@mui/icons-material';
import { PostOutline } from 'mdi-material-ui';
import * as PropTypes from 'prop-types';
import { Component } from 'react';

import CustomTooltip from '../../../../components/CustomTooltip';

const iconSelector = (type, variant, fontSize) => {
  let style;
  switch (variant) {
    case 'inline':
      style = {
        width: 20,
        height: 20,
        margin: '0 7px 0 0',
        float: 'left',
      };
      break;
    case 'chip':
      style = { margin: '0 0 0 5px' };
      break;
    default:
      style = { margin: 0 };
  }

  switch (type) {
    case 'newspaper':
      return (
        <NewspaperOutlined
          style={style}
          fontSize={fontSize}
          sx={{ color: '#3f51b5' }}
        />
      );
    case 'microblogging':
      return (
        <PostOutline
          style={style}
          fontSize={fontSize}
          sx={{ color: '#00bcd4' }}
        />
      );
    case 'tv':
      return (
        <OndemandVideoOutlined
          style={style}
          fontSize={fontSize}
          sx={{ color: '#ff9800' }}
        />
      );
    default:
      return <HelpOutlined style={style} fontSize={fontSize} />;
  }
};

class ChannelIcon extends Component {
  render() {
    const { type, size, variant, tooltip } = this.props;
    const fontSize = size || 'medium';
    if (tooltip) {
      return (
        <CustomTooltip title={tooltip}>
          {iconSelector(type, variant, fontSize)}
        </CustomTooltip>
      );
    }
    return iconSelector(type, variant, fontSize);
  }
}

ChannelIcon.propTypes = {
  type: PropTypes.string,
  size: PropTypes.string,
  tooltip: PropTypes.string,
  variant: PropTypes.string,
  done: PropTypes.bool,
};

export default ChannelIcon;
