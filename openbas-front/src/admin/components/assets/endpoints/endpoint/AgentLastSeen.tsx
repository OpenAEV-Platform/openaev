import { useEffect, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';

const AgentLastSeen = ({ timestamp }: { timestamp: string }) => {
  const { du } = useFormatter();
  const [elapsed, setElapsed] = useState(
    Date.now() - new Date(timestamp).getTime(),
  );

  useEffect(() => {
    const interval = setInterval(() => {
      setElapsed(Date.now() - new Date(timestamp).getTime());
    }, 1000);
    return () => clearInterval(interval);
  }, [timestamp]);

  return (
    <span>
      {du(elapsed)}
    </span>
  );
};

export default AgentLastSeen;
