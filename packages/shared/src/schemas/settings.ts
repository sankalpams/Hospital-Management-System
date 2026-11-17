import { z } from 'zod';

export const settingsSchema = z.object({
  hospitalName: z.string().trim().min(1).max(120),
  hospitalAddress: z.string().trim().max(300),
  hospitalPhone: z.string().trim().max(30),
  currencyCode: z.string().trim().length(3).toUpperCase(),
  currencySymbol: z.string().trim().min(1).max(8),
  taxRatePercent: z.number().min(0).max(100),
  sessionTimeoutMinutes: z.number().int().min(1).max(1440),
  expiryAlertDays: z.number().int().min(1).max(365),
});

export type Settings = z.infer<typeof settingsSchema>;
