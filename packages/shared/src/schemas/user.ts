import { z } from 'zod';
import { roleSchema, usernameSchema } from './auth.js';

export const createUserSchema = z.object({
  username: usernameSchema,
  fullName: z.string().trim().min(1).max(120),
  email: z.string().trim().email().optional(),
  role: roleSchema,
});

export const updateUserSchema = z
  .object({
    fullName: z.string().trim().min(1).max(120),
    email: z.string().trim().email(),
    role: roleSchema,
    isActive: z.boolean(),
  })
  .partial();

export type CreateUserInput = z.infer<typeof createUserSchema>;
export type UpdateUserInput = z.infer<typeof updateUserSchema>;
