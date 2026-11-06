import { useCallback, useState } from 'react';

/** Busy/error state for one async toolbar action. */
export default function useAsyncAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);

  const run = useCallback(async (action, { rethrow = false } = {}) => {
    setBusy(true);
    setError(false);
    try {
      return await action();
    } catch (err) {
      setError(true);
      if (rethrow) throw err;
      return undefined;
    } finally {
      setBusy(false);
    }
  }, []);

  return { busy, error, run };
}
