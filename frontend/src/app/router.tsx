import { Route, Routes } from 'react-router';

import { AppLayout } from '@/components/layout/AppLayout';
import { AdminApp } from '@/features/admin/AdminApp';
import { HomePage } from '@/pages/HomePage';
import { LicensePage } from '@/pages/LicensePage';
import { MethodologyPage } from '@/pages/MethodologyPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { ScanPage } from '@/pages/ScanPage';

/**
 * Route table. `admin/*` is a sibling of the shared public shell, not a child of it: the admin
 * console has its own auth provider and its own login page, so it must never render inside
 * `AppLayout`'s public header/footer or under the public shell's device-license `LicenseProvider`.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="admin/*" element={<AdminApp />} />
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        <Route path="methodology" element={<MethodologyPage />} />
        <Route path="license" element={<LicensePage />} />
        <Route path="scans/:scanId" element={<ScanPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
