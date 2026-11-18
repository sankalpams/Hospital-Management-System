import { createApp } from './app.js';
import { config } from './config.js';
import { logger } from './logger.js';

createApp({ loginRateLimit: config.loginRateLimit }).listen(config.port, () => {
  logger.info(`HMS server listening on port ${config.port}`);
});
