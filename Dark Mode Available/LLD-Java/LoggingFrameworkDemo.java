/*
 * Logging Framework - Low Level Design
 * ------------------------------------
 * A pluggable logger with severity levels, multiple appenders (console,
 * in-memory/file-like) and a Chain-of-Responsibility over log levels.
 *
 * Key classes:
 *   - LogLevel
 *   - LogMessage
 *   - Appender (+ ConsoleAppender, MemoryAppender)
 *   - Logger (level filter + fan-out to appenders)
 *
 * Patterns used: Chain of Responsibility (levels), Strategy (appenders),
 *                Singleton-ish LogManager.
 */

import java.util.ArrayList;
import java.util.List;

public class LoggingFrameworkDemo {
    public static void main(String[] args) {
        Logger logger = LogManager.getLogger();
        logger.setLevel(LogLevel.INFO);
        logger.addAppender(new ConsoleAppender());
        MemoryAppender memory = new MemoryAppender();
        logger.addAppender(memory);

        System.out.println("=== Logging Framework Demo ===");
        logger.debug("this is debug - filtered out");
        logger.info("application started");
        logger.warn("low disk space");
        logger.error("unhandled exception");

        System.out.println("--- Memory appender captured " + memory.getLogs().size() + " entries ---");
        for (String l : memory.getLogs()) System.out.println("  stored: " + l);
    }
}

enum LogLevel {
    DEBUG(1), INFO(2), WARN(3), ERROR(4);
    final int severity;
    LogLevel(int severity) { this.severity = severity; }
}

class LogMessage {
    final LogLevel level; final String text; final long timestamp;
    LogMessage(LogLevel level, String text) { this.level = level; this.text = text; this.timestamp = System.currentTimeMillis(); }
    String format() { return "[" + level + "] " + text; }
}

interface Appender {
    void append(LogMessage message);
}

class ConsoleAppender implements Appender {
    public void append(LogMessage message) { System.out.println(message.format()); }
}

class MemoryAppender implements Appender {
    private final List<String> logs = new ArrayList<String>();
    public void append(LogMessage message) { logs.add(message.format()); }
    List<String> getLogs() { return logs; }
}

class Logger {
    private LogLevel level = LogLevel.INFO;
    private final List<Appender> appenders = new ArrayList<Appender>();

    void setLevel(LogLevel level) { this.level = level; }
    void addAppender(Appender a) { appenders.add(a); }

    void log(LogLevel level, String text) {
        if (level.severity < this.level.severity) return; // filter
        LogMessage msg = new LogMessage(level, text);
        for (Appender a : appenders) a.append(msg);
    }
    void debug(String t) { log(LogLevel.DEBUG, t); }
    void info(String t) { log(LogLevel.INFO, t); }
    void warn(String t) { log(LogLevel.WARN, t); }
    void error(String t) { log(LogLevel.ERROR, t); }
}

class LogManager {
    private static Logger instance;
    static synchronized Logger getLogger() {
        if (instance == null) instance = new Logger();
        return instance;
    }
}
