import type { RequestHandler } from 'express';
import type { Settings } from '@hms/shared';
import { unauthenticated } from '../../http/errors.js';
import { settingsService } from './settings.service.js';

export const getPublicSettings: RequestHandler = async (_req, res, next) => {
  try {
    res.json(await settingsService.getPublic());
  } catch (err) {
    next(err);
  }
};

export const getSettings: RequestHandler = async (_req, res, next) => {
  try {
    res.json(await settingsService.getAll());
  } catch (err) {
    next(err);
  }
};

export const putSettings: RequestHandler = async (req, res, next) => {
  try {
    if (!req.user) throw unauthenticated();
    res.json(await settingsService.update(req.user.id, req.body as Settings, req.ip ?? null));
  } catch (err) {
    next(err);
  }
};
