import { Badge } from '@/components/ui/Badge';
import type { RiskLevel } from '@/features/scanner/schemas/scanResponse';

const LABELS: Record<RiskLevel, string> = {
  LOW: 'Low risk',
  MODERATE: 'Moderate risk',
  HIGH: 'High risk',
  CRITICAL: 'Critical risk',
};

const TONES: Record<RiskLevel, 'low' | 'moderate' | 'high' | 'critical'> = {
  LOW: 'low',
  MODERATE: 'moderate',
  HIGH: 'high',
  CRITICAL: 'critical',
};

/** Text-and-colour risk indicator. Never colour alone. */
export function RiskBadge({ riskLevel }: { readonly riskLevel: RiskLevel }) {
  return <Badge tone={TONES[riskLevel]}>{LABELS[riskLevel]}</Badge>;
}
