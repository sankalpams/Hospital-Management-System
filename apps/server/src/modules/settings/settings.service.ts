import { prisma } from '../../db.js';

export interface SettingsValue {
  hospitalName: string;
  hospitalAddress: string;
  hospitalPhone: string;
  sessionTimeoutMinutes: number;
  expiryAlertDays: number;
  taxRatePercent: number;
  currencyCode: string;
  currencySymbol: string;
}

export const SETTING_DEFAULTS: SettingsValue = {
  hospitalName: 'Hospital',
  hospitalAddress: '',
  hospitalPhone: '',
  sessionTimeoutMinutes: 15,
  expiryAlertDays: 90,
  taxRatePercent: 0,
  currencyCode: 'LKR',
  currencySymbol: 'Rs.',
};

export async function getSetting<K extends keyof SettingsValue>(
  key: K,
): Promise<SettingsValue[K]> {
  const row = await prisma.setting.findUnique({ where: { key } });
  if (!row || row.value === null) return SETTING_DEFAULTS[key];
  return row.value as SettingsValue[K];
}
