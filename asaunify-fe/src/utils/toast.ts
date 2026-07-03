import { toast } from 'sonner';
import axios from 'axios';

export const toastSuccess = (message: string) => {
  toast.success(message);
};

export const toastError = (message: string) => {
  toast.error(message);
};

export const toastApiError = (
  error: unknown,
  fallback = 'Something went wrong. Please try again.'
) => {
  if (axios.isAxiosError(error)) {
    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.response?.data ||
      fallback;
    toast.error(typeof message === 'string' ? message : fallback);
  } else {
    toast.error(fallback);
  }
};

export const toastLoading = (message: string) => {
  return toast.loading(message);
};

export const toastDismiss = (id: string | number) => {
  toast.dismiss(id);
};