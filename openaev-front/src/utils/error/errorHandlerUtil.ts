export type Error = {
  status: number;
  message: string;
  errors?: { children?: { message?: { errors: string[] } } };
};

let notifyError: ((error: Error) => void) | null = null;

export const setNotifyErrorHandler = (fn: (error: Error) => void) => {
  notifyError = fn;
};

export const notifyErrorHandler = (error: Error) => {
  if (notifyError) {
    notifyError(error);
  }
};
