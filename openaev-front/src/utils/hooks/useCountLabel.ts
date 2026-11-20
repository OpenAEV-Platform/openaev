import { useFormatter } from '../../components/i18n';

/** A translated count: the "1 …" key for one, the "{count} …" key otherwise. */
const useCountLabel = () => {
  const { t } = useFormatter();
  return (count: number, one: string, many: string) => (count === 1 ? t(one) : t(many, { count: String(count) }));
};

export default useCountLabel;
