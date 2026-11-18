import { faker, type Faker } from '@faker-js/faker';
import type { PrismaClient } from '@prisma/client';

export type SeedScale = 'small' | 'large';

export const SEED_RANDOM_SEED = 20261117;

export interface SeedContext {
  prisma: PrismaClient;
  faker: Faker;
  scale: SeedScale;
  now: Date;
  log(msg: string): void;
}

export interface SeedModule {
  name: string;
  run(ctx: SeedContext): Promise<void>;
}

/** SEED_SCALE: small | large (case-insensitive); unset means large; anything else is an error. */
export function parseScale(value: string | undefined): SeedScale {
  if (value === undefined) return 'large';
  const v = value.toLowerCase();
  if (v === 'small' || v === 'large') return v;
  throw new Error(`Invalid SEED_SCALE "${value}": expected "small" or "large"`);
}

/** The seed overwrites demo users and settings, so it must never run in production by accident. */
export function assertSeedAllowed(
  env: Record<string, string | undefined>,
  warn: (msg: string) => void = (m) => console.warn(m),
): void {
  if (env.NODE_ENV !== 'production') return;
  if (env.SEED_ALLOW_PRODUCTION !== '1') {
    throw new Error(
      'Refusing to seed: NODE_ENV=production. Set SEED_ALLOW_PRODUCTION=1 to override (this overwrites hospital settings and demo users).',
    );
  }
  warn(
    'WARNING: seeding a PRODUCTION database (SEED_ALLOW_PRODUCTION=1). Hospital settings and demo users will be overwritten.',
  );
}

export function createContext(opts: {
  prisma: PrismaClient;
  scale?: SeedScale;
  now?: Date;
  log?: (msg: string) => void;
}): SeedContext {
  faker.seed(SEED_RANDOM_SEED);
  return {
    prisma: opts.prisma,
    faker,
    scale: opts.scale ?? 'large',
    now: opts.now ?? new Date(),
    log: opts.log ?? ((msg) => console.log(msg)),
  };
}
