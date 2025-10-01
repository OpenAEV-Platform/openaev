import { useCallback, useState } from 'react';

const useDialog = <T = void>() => {
  const [open, setOpen] = useState(false);
  const [data, setData] = useState<T | undefined>(undefined);

  const handleOpen = useCallback((payload?: T) => {
    setData(payload);
    setOpen(true);
  }, []);

  const handleClose = useCallback(() => {
    setOpen(false);
    setData(undefined);
  }, []);

  return {
    open,
    data,
    handleOpen,
    handleClose,
    dialogProps: {
      open,
      handleClose: handleClose,
    },
  };
};

export default useDialog;
