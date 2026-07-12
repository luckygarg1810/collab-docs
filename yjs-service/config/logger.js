const winston = require('winston');

// Configure logging levels and format
const logger = winston.createLogger({
    // winston's level names are lowercase-only; an unrecognized level (e.g.
    // an uppercase "INFO" from the environment) breaks its severity
    // comparison and silently drops every log call, including errors.
    level: (process.env.LOG_LEVEL || 'info').toLowerCase(),
    format: winston.format.combine(
        winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
        winston.format.errors({ stack: true }),
        winston.format.splat(),
        winston.format.json()
    ),
    defaultMeta: { service: 'yjs-service' },
    transports: [
        // Write all logs to console
        new winston.transports.Console({
            format: winston.format.combine(
                winston.format.colorize(),
                winston.format.printf(
                    ({ level, message, timestamp, ...metadata }) => {
                        let msg = `${timestamp} [${level}] : ${message} `;
                        if (Object.keys(metadata).length > 0) {
                            msg += JSON.stringify(metadata);
                        }
                        return msg;
                    }
                )
            ),
        }),
    ],
});

// If we're in production, also log to file
if (process.env.NODE_ENV === 'production') {
    logger.add(new winston.transports.File({
        filename: 'logs/error.log',
        level: 'error'
    }));
    logger.add(new winston.transports.File({
        filename: 'logs/combined.log'
    }));
}

module.exports = logger;
