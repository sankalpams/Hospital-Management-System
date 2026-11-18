import { Router } from 'express';
import { createUserSchema, updateUserSchema } from '@hms/shared';
import { requireAuth } from '../../http/requireAuth.js';
import { requirePermission } from '../../http/requirePermission.js';
import { validate } from '../../http/validate.js';
import { createUser, listUsers, resetUserPassword, updateUser } from './users.controller.js';

export function usersRouter(): Router {
  const router = Router();
  router.use(requireAuth(), requirePermission('user:manage'));
  router.get('/', listUsers);
  router.post('/', validate(createUserSchema, 'body'), createUser);
  router.patch('/:id', validate(updateUserSchema, 'body'), updateUser);
  router.post('/:id/reset-password', resetUserPassword);
  return router;
}
