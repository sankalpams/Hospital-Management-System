import { Router } from 'express';
import { prisma } from '../../db.js';

export const healthRouter = Router();

healthRouter.get('/', async (_req, res, next) => {
  try {
    await prisma.$queryRaw`SELECT 1`;
    res.json({ status: 'ok', db: 'up' });
  } catch (err) {
    next(err);
  }
});
