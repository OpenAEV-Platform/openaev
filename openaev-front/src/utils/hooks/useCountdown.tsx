import { useEffect, useState } from 'react';

const useCountdown = (timePeriod: number, interval: number, minusTime: number) => {
  const [remainingTimePeriod, setRemainingTimePeriod] = useState(timePeriod);

  useEffect(() => {
    const intervalId = setInterval(() => {
      setRemainingTimePeriod(current => Math.max(current - minusTime, 0));
    }, interval);
    return () => {
      clearInterval(intervalId);
    };
  }, [timePeriod]);
  return remainingTimePeriod;
};

export default useCountdown;
