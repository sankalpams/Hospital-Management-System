import type { Prisma } from '@prisma/client';
import { prisma } from '../../db.js';
import { auditService } from '../audit/audit.service.js';
import { settingsRepository } from './settings.repository.js';

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

export const PUBLIC_SETTING_KEYS = [
  'hospitalName',
  'hospitalAddress',
  'hospitalPhone',
  'currencyCode',
  'currencySymbol',
  'sessionTimeoutMinutes',
] as const satisfies readonly (keyof SettingsValue)[];

type PublicSettings = Pick<SettingsValue, (typeof PUBLIC_SETTING_KEYS)[number]>;

async function loadAll(db?: Prisma.TransactionClient): Promise<SettingsValue> {
  const rows = await settingsRepository.findAll(db);
  const result: Record<string, unknown> = { ...SETTING_DEFAULTS };
  for (const row of rows) {
    if (row.key in SETTING_DEFAULTS && row.value !== null) result[row.key] = row.value;
  }
  return result as unknown as SettingsValue;
}

export const settingsService = {
  getAll: () => loadAll(),

  async getPublic(): Promise<PublicSettings> {
    const all = await loadAll();
    return Object.fromEntries(PUBLIC_SETTING_KEYS.map((k) => [k, all[k]])) as unknown as PublicSettings;
  },

  async update(actorId: string, input: SettingsValue, ip: string | null): Promise<SettingsValue> {
    return prisma.$transaction(async (tx) => {
      const before = await loadAll(tx);
      for (const key of Object.keys(SETTING_DEFAULTS) as (keyof SettingsValue)[]) {
        await settingsRepository.upsert(key, input[key], tx);
      }
      const after = await loadAll(tx);
      await auditService.record(tx, {
        userId: actorId,
        action: 'UPDATE',
        entityType: 'Setting',
        before: { ...before },
        after: { ...after },
        ip,
      });
      return after;
    });
  },
};
