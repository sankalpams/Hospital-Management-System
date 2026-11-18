import type { RequestHandler } from 'express';
import { z } from 'zod';
import { roleSchema, type CreateUserInput, type UpdateUserInput } from '@hms/shared';
import { unauthenticated } from '../../http/errors.js';
import { parsePaging } from '../../http/paging.js';
import { usersService } from './users.service.js';

const boolQuery = z.enum(['true', 'false']).transform((v) => v === 'true');

export const listUsersQuerySchema = z.object({
  search: z.string().trim().min(1).optional(),
  role: roleSchema.optional(),
  isActive: boolQuery.optional(),
});

export const listUsers: RequestHandler = async (req, res, next) => {
  try {
    const filter = listUsersQuerySchema.parse(req.query);
    res.json(await usersService.list(filter, parsePaging(req.query)));
  } catch (err) {
    next(err);
  }
};

export const createUser: RequestHandler = async (req, res, next) => {
  try {
    if (!req.user) throw unauthenticated();
    const result = await usersService.create(req.user.id, req.body as CreateUserInput, req.ip ?? null);
    res.status(201).json(result);
  } catch (err) {
    next(err);
  }
};

export const updateUser: RequestHandler = async (req, res, next) => {
  try {
    if (!req.user) throw unauthenticated();
    const id = String(req.params.id);
    res.json(await usersService.update(req.user.id, id, req.body as UpdateUserInput, req.ip ?? null));
  } catch (err) {
    next(err);
  }
};

export const resetUserPassword: RequestHandler = async (req, res, next) => {
  try {
    if (!req.user) throw unauthenticated();
    const id = String(req.params.id);
    res.json(await usersService.resetPassword(req.user.id, id, req.ip ?? null));
  } catch (err) {
    next(err);
  }
};
