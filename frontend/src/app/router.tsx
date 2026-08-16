import { Route, Routes } from 'react-router';

import { AppLayout } from '@/components/layout/AppLayout';
import { HomePage } from '@/pages/HomePage';
import { MethodologyPage } from '@/pages/MethodologyPage';
import { NotFoundPage } from '@/pages/NotFoundPage';

/** Route table. Every route renders inside the shared shell. */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        <Route path="methodology" element={<MethodologyPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
