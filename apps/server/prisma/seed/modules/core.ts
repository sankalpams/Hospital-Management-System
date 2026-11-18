import type { Prisma, PrismaClient, RoleName } from '@prisma/client';
import { hashPassword } from '../../../src/modules/auth/password.js';
import type { SeedModule } from '../context.js';

type Db = PrismaClient | Prisma.TransactionClient;

export const ROLE_DESCRIPTIONS: Record<RoleName, string> = {
  ADMIN: 'Administrator',
  DOCTOR: 'Doctor',
  NURSE: 'Nurse',
  RECEPTIONIST: 'Receptionist',
  LAB_STAFF: 'Laboratory staff',
  PHARMACIST: 'Pharmacist',
  ACCOUNTANT: 'Accountant',
};

export const DEMO_PASSWORD = 'Password1!';

export const DEMO_USERS: { username: string; fullName: string; role: RoleName }[] = [
  { username: 'admin', fullName: 'Nimal Perera', role: 'ADMIN' },
  { username: 'doctor', fullName: 'Dr. Kumari Fernando', role: 'DOCTOR' },
  { username: 'nurse', fullName: 'Tharushi Jayasinghe', role: 'NURSE' },
  { username: 'reception', fullName: 'Mohamed Rizwan', role: 'RECEPTIONIST' },
  { username: 'lab', fullName: 'Suresh Kumar', role: 'LAB_STAFF' },
  { username: 'pharmacist', fullName: 'Dilani Wickramasinghe', role: 'PHARMACIST' },
  { username: 'accountant', fullName: 'Ruwan Bandara', role: 'ACCOUNTANT' },
];

export const DEMO_SETTINGS: Record<string, Prisma.InputJsonValue> = {
  hospitalName: 'Lanka General Hospital (Demo)',
  hospitalAddress: 'No. 10, Kynsey Road, Colombo 08',
  hospitalPhone: '+94112345678',
  currencyCode: 'LKR',
  currencySymbol: 'Rs.',
  taxRatePercent: 0,
  sessionTimeoutMinutes: 15,
  expiryAlertDays: 90,
};

/** Idempotently ensures the 7 role rows exist; also used by the test harness. */
export async function seedRoles(db: Db): Promise<void> {
  for (const [name, description] of Object.entries(ROLE_DESCRIPTIONS) as [RoleName, string][]) {
    await db.role.upsert({ where: { name }, update: { description }, create: { name, description } });
  }
}

export const coreModule: SeedModule = {
  name: 'core',
  async run(ctx) {
    const { prisma } = ctx;
    await seedRoles(prisma);
    const roles = await prisma.role.findMany();
    const roleId = new Map(roles.map((r) => [r.name, r.id]));

    for (const [key, value] of Object.entries(DEMO_SETTINGS)) {
      await prisma.setting.upsert({ where: { key }, update: { value }, create: { key, value } });
    }

    const passwordHash = await hashPassword(DEMO_PASSWORD); // hashed once, reused
    for (const u of DEMO_USERS) {
      const data = { fullName: u.fullName, roleId: roleId.get(u.role) as string };
      await prisma.user.upsert({
        where: { username: u.username },
        update: data,
        create: { username: u.username, passwordHash, mustChangePassword: false, ...data },
      });
    }
    ctx.log(
      `core: ${roles.length} roles, ${DEMO_USERS.length} users, ${Object.keys(DEMO_SETTINGS).length} settings`,
    );
  },
};
