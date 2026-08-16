/**
 * Types inferred from the Zod schemas in `../schemas`, following the pattern in
 * `features/health/types/health.ts`. Re-exported here rather than duplicated so a
 * schema change can never silently drift from its type.
 */
export type {
  ScanResponse,
  ScanData,
  Finding,
  NormalizedUrlResponse,
  RiskLevel,
  Severity,
} from '@/features/scanner/schemas/scanResponse';
export type { ScanRequest } from '@/features/scanner/schemas/scanRequest';
