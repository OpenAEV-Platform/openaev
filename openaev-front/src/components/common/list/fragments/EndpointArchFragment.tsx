import { useFormatter } from '../../../i18n';

type Props = { arch?: string };

const EndpointArchFragment = (props: Props) => {
  const { t } = useFormatter();
  return props.arch ?? t('Unknown');
};

export default EndpointArchFragment;
