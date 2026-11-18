import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PrismaClient } from '@prisma/client';
import { createContext, parseScale, type SeedModule, type SeedScale } from './context.js';
import { coreModule } from './modules/core.js';

/** Ordered list; later slices append their modules here. */
export const modules: SeedModule[] = [coreModule];

export async function runSeed(opts: {
  prisma: PrismaClient;
  scale?: SeedScale;
  log?: (msg: string) => void;
}): Promise<void> {
  const ctx = createContext(opts);
  ctx.log(`Seeding (scale=${ctx.scale})`);
  for (const mod of modules) {
    const start = performance.now();
    await mod.run(ctx);
    ctx.log(`[seed] ${mod.name} done in ${Math.round(performance.now() - start)}ms`);
  }
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const prisma = new PrismaClient();
  runSeed({ prisma, scale: parseScale(process.env.SEED_SCALE) })
    .catch((err) => {
      console.error(err);
      process.exitCode = 1;
    })
    .finally(() => prisma.$disconnect());
}
