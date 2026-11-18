/**
 * Mirror of the server's PasswordPolicy, used ONLY for live feedback while typing.
 * The server re-validates and is the authority.
 */
export const PASSWORD_RULES = [
  { id: 'length', label: 'At least 10 characters', test: (p) => p.length >= 10 && p.length <= 72 },
  { id: 'letter', label: 'At least one letter', test: (p) => /\p{L}/u.test(p) },
  { id: 'digit', label: 'At least one number', test: (p) => /\d/.test(p) },
  {
    id: 'username',
    label: 'Does not contain your username',
    test: (p, username) => !username || !p.toLowerCase().includes(username.toLowerCase()),
  },
];

export function evaluatePassword(password, username) {
  return PASSWORD_RULES.map((r) => ({ ...r, passed: password.length > 0 && r.test(password, username) }));
}

export function isPasswordAcceptable(password, username) {
  return evaluatePassword(password, username).every((r) => r.passed);
}
