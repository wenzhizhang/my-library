import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';

import { LIBRARY_PATH } from '../config';

const KINDS = {
  books: 'book',
  authors: 'author',
  publishers: 'publisher',
  series: 'series',
  brands: 'brand',
};

const BASE = LIBRARY_PATH.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const PAGE_RE = new RegExp(`^${BASE}/(books|authors|publishers|series|brands)/(\\d+)(?:/|$)`);

/** Detail page currently on screen, e.g. { kind: 'author', id: 12 }. */
export default function usePageContext() {
  const { pathname } = useLocation();
  return useMemo(() => {
    const match = PAGE_RE.exec(pathname);
    if (!match) return null;
    return { kind: KINDS[match[1]], id: Number(match[2]) };
  }, [pathname]);
}
