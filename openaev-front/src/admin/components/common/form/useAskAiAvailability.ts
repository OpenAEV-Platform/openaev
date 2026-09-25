import useAI from '../../../../utils/hooks/useAI';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';

/** Whether the Ask AI action is usable, and whether it must be hidden altogether. */
const useAskAiAvailability = () => {
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const { enabled, configured, xtmOneConfigured } = useAI();
  return {
    hidden: enabled === false,
    isAvailable: isEnterpriseEdition && enabled === true && (configured || xtmOneConfigured) === true,
  };
};

export default useAskAiAvailability;
