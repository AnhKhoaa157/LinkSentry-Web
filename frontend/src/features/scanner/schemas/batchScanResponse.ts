import { z } from 'zod';

import { apiErrorSchema } from '@/lib/api/errors';
import { scanDataSchema } from '@/features/scanner/schemas/scanResponse';

const indexedItem = z.object({ index: z.number().int().nonnegative() });

const batchScanItemSchema = z.discriminatedUnion('status', [
  indexedItem.extend({ status: z.literal('COMPLETE'), result: scanDataSchema }),
  indexedItem.extend({ status: z.literal('FAILED'), error: apiErrorSchema }),
  indexedItem.extend({ status: z.literal('SKIPPED') }),
]);

export const batchScanResponseSchema = z.object({
  data: z.object({ results: z.array(batchScanItemSchema) }),
  meta: z.object({ engineVersion: z.string() }),
});

export type BatchScanResponse = z.infer<typeof batchScanResponseSchema>;
export type BatchScanItem = z.infer<typeof batchScanItemSchema>;
