type Props = { arch?: string };

// Architecture is only meaningful for OS-bound assets; absent / unknown values render a neutral dash
// rather than a misleading "Unknown".
const EndpointArchFragment = (props: Props) => {
  if (!props.arch || props.arch === 'Unknown') {
    return '-';
  }
  return props.arch;
};

export default EndpointArchFragment;
