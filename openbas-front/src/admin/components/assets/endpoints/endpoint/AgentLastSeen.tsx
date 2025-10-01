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

  const days = Math.floor(seconds / (60 * 60 * 24));
  const hours = Math.floor((seconds % (60 * 60 * 24)) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;

  let display;
  if (days > 0) {
    display = `${days}d ${hours}h ${minutes}m ${remainingSeconds}s ago`;
  } else if (hours > 0) {
    display = `${hours}h ${minutes}m ${remainingSeconds}s ago`;
  } else if (minutes > 0) {
    display = `${minutes}m ${remainingSeconds}s ago`;
  } else {
    display = `${remainingSeconds}s ago`;
  }

  return <span>{display}</span>;
};

export default AgentLastSeen;
