import { createApp } from './app.js';
import { config } from './config.js';
import { logger } from './logger.js';

createApp().listen(config.port, () => {
  logger.info(`HMS server listening on port ${config.port}`);
});
