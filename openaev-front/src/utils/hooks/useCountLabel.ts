import { useFormatter } from '../../components/i18n';

/** A translated count: the "1 …" key for one, the "{count} …" key otherwise, with any other value of the text. */
const useCountLabel = () => {
  const { t } = useFormatter();
  return (count: number, one: string, many: string, values: Record<string, string> = {}) => (count === 1
    ? t(one, values)
    : t(many, {
        ...values,
        count: String(count),
      }));
};

export default useCountLabel;
