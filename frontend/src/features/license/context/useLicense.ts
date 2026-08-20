import { useContext } from 'react';

import { LicenseContext } from '@/features/license/context/LicenseContext';

export function useLicense() {
  const value = useContext(LicenseContext);
  if (value === null) {
    throw new Error('useLicense must be used inside LicenseProvider');
  }
  return value;
}
