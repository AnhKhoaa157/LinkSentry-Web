import { AppProviders } from '@/app/providers';
import { AppRoutes } from '@/app/router';

/** Application root: providers wrapped around the route table. */
export function App() {
  return (
    <AppProviders>
      <AppRoutes />
    </AppProviders>
  );
}
