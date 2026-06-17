import { DnsOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { ApplicationCogOutline, Console, FileImportOutline, LanConnect } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import CustomTooltip from '../../../../components/CustomTooltip';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Collector, type Injector } from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';

interface Props {
  type: string | undefined;
  size?: string;
  variant?: string;
  done?: boolean;
  disabled?: boolean;
  isPayload?: boolean;
  onClick?: () => void;
  tooltip?: object;
}

const InjectIcon: FunctionComponent<Props> = ({
  type,
  size,
  variant,
  done,
  disabled,
  isPayload,
  onClick,
  tooltip,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const fontSize = size || 'medium';

  const { injectors } = useHelper((helper: InjectorHelper) => ({ injectors: helper.getInjectorsIncludingPending() }));

  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getCollectorsIncludingPending() }));

  const resolvedInjectorId = (injectors as Injector[])?.find(
    (inj: Injector) => inj.injector_type === type,
  )?.injector_id;

  const resolvedCollectorId = (collectors as Collector[])?.find(
    (c: Collector) => c.collector_type === type,
  )?.collector_id;

  const iconSelector = (type: string, isPayload: boolean, variant: string, fontSize: string, done: boolean, disabled: boolean) => {
    const style = {
      marginTop: variant === 'list' ? theme.spacing(0.5) : 0,
      padding: variant === 'timeline' ? 1 : 0,
      width: fontSize === 'small' || variant === 'inline' ? 20 : 24,
      height: fontSize === 'small' || variant === 'inline' ? 20 : 24,
      borderRadius: 4,
      cursor: onClick ? 'pointer' : 'default',
      filter: `${done ? 'filter:hue-rotate(100deg)' : `brightness(${disabled ? '30%' : '100%'})`}`,
    };
    if (!type || type.endsWith('_dummy')) {
      return (
        <HelpOutlineOutlined onClick={onClick} style={style} />
      );
    }
    if (isPayload) {
      if (type.startsWith('openaev_')) {
        return (
          <img
            onClick={onClick}
            src={resolvedCollectorId ? buildTenantApiPath(`/api/images/collectors/id/${resolvedCollectorId}`) : undefined}
            alt={type}
            style={style}
          />
        );
      }
      switch (type) {
        case 'Command':
          return <Console color="primary" onClick={onClick} style={style} />;
        case 'Executable':
          return <ApplicationCogOutline color="primary" onClick={onClick} style={style} />;
        case 'FileDrop':
          return <FileImportOutline color="primary" onClick={onClick} style={style} />;
        case 'DnsResolution':
          return <DnsOutlined color="primary" onClick={onClick} style={style} />;
        case 'NetworkTraffic':
          return <LanConnect color="primary" onClick={onClick} style={style} />;
        default:
          return <HelpOutlineOutlined color="primary" onClick={onClick} style={style} />;
      }
    }
    return (
      <img
        src={resolvedInjectorId ? buildTenantApiPath(`/api/images/injectors/id/${resolvedInjectorId}`) : undefined}
        onClick={onClick}
        alt={type}
        style={style}
      />
    );
  };

  return (
    <CustomTooltip title={tooltip ?? (type ? t(type) : t('Unknown'))}>
      {iconSelector(type!, isPayload!, variant!, fontSize, done!, disabled!)}
    </CustomTooltip>
  );
};

export default InjectIcon;
