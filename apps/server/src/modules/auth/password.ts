import bcrypt from 'bcryptjs';

function cost(): number {
  const c = Number(process.env.BCRYPT_COST);
  return Number.isInteger(c) && c >= 4 && c <= 31 ? c : 12;
}

export function hashPassword(pw: string): Promise<string> {
  return bcrypt.hash(pw, cost());
}

export function verifyPassword(pw: string, hash: string): Promise<boolean> {
  return bcrypt.compare(pw, hash);
}
