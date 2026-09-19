import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Category } from '../api/types';

// Categories change rarely and are needed by the header, nav bar and filters on every page: fetch them once.
let cached: Promise<Category[]> | null = null;

export function useCategories(): Category[] {
  const [categories, setCategories] = useState<Category[]>([]);
  useEffect(() => {
    let live = true;
    cached ??= api<Category[]>('/categories').catch(() => {
      cached = null; // try again next time
      return [] as Category[];
    });
    void cached.then((c) => live && setCategories(c));
    return () => {
      live = false;
    };
  }, []);
  return categories;
}
