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

export function parseScale(value: string | undefined): SeedScale {
  return value === 'small' ? 'small' : 'large';
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
