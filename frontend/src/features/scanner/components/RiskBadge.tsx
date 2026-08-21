import { Badge } from '@/components/ui/Badge';
import type { RiskLevel } from '@/features/scanner/schemas/scanResponse';
import type { TranslationKey } from '@/lib/i18n/translations';
import { useLocale } from '@/lib/i18n/useLocale';

const LABEL_KEYS: Record<RiskLevel, TranslationKey> = {
  LOW: 'riskBadge.low',
  MODERATE: 'riskBadge.moderate',
  HIGH: 'riskBadge.high',
  CRITICAL: 'riskBadge.critical',
};

const TONES: Record<RiskLevel, 'low' | 'moderate' | 'high' | 'critical'> = {
  LOW: 'low',
  MODERATE: 'moderate',
  HIGH: 'high',
  CRITICAL: 'critical',
};

/** Text-and-colour risk indicator. Never colour alone. */
export function RiskBadge({ riskLevel }: { readonly riskLevel: RiskLevel }) {
  const { t } = useLocale();
  return <Badge tone={TONES[riskLevel]}>{t(LABEL_KEYS[riskLevel])}</Badge>;
}
