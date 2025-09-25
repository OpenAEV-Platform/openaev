import { useEffect, useState } from 'react';

const AgentLastSeen = ({ timestamp }: { timestamp: string }) => {
  const [seconds, setSeconds] = useState(
    Math.floor((Date.now() - new Date(timestamp).getTime()) / 1000),
  );

  useEffect(() => {
    const interval = setInterval(() => {
      setSeconds(Math.floor((Date.now() - new Date(timestamp).getTime()) / 1000));
    }, 1000);
    return () => clearInterval(interval);
  }, [timestamp]);

  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;

  return (
    <span>
      {minutes > 0
        ? `${minutes}m ${remainingSeconds}s ago`
        : `${remainingSeconds}s ago`}
    </span>
  );
};

export default AgentLastSeen;
