import { Router } from 'express';
import { settingsSchema } from '@hms/shared';
import { requireAuth } from '../../http/requireAuth.js';
import { requirePermission } from '../../http/requirePermission.js';
import { validate } from '../../http/validate.js';
import { getPublicSettings, getSettings, putSettings } from './settings.controller.js';

export function settingsRouter(): Router {
  const router = Router();
  // Public subset: any signed-in user, including one who must still change their password.
  router.get('/public', requireAuth({ allowPasswordChange: true }), getPublicSettings);
  router.get('/', requireAuth(), requirePermission('settings:manage'), getSettings);
  router.put(
    '/',
    requireAuth(),
    requirePermission('settings:manage'),
    validate(settingsSchema, 'body'),
    putSettings,
  );
  return router;
}
