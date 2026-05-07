package com.ceres.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class BotLogger {

    public static final int LEVEL_ERROR = 1;
    public static final int LEVEL_WARN  = 2;
    public static final int LEVEL_INFO  = 3;
    public static final int LEVEL_DEBUG = 4;

    private static final int MAX_BUFFER = 100;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss");

    private static final BotLogger INSTANCE = new BotLogger();

    private final ArrayDeque<String> buffer = new ArrayDeque<>();
    private BufferedWriter fileWriter;
    private int logLevel = LEVEL_WARN;

    private BotLogger() {
        try {
            Path logFile = FabricLoader.getInstance().getConfigDir().resolve("ceres/ceres.log");
            Files.createDirectories(logFile.getParent());
            fileWriter = Files.newBufferedWriter(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[Ceres] Failed to open log file: " + e.getMessage());
        }
    }

    public static BotLogger getInstance() {
        return INSTANCE;
    }

    public void setLogLevel(int level) {
        this.logLevel = level;
    }

    public int getLogLevel() {
        return logLevel;
    }

    public void logError(String msg) { log(LEVEL_ERROR, "ERROR", msg); }
    public void logWarn(String msg)  { log(LEVEL_WARN,  "WARN",  msg); }
    public void logInfo(String msg)  { log(LEVEL_INFO,  "INFO",  msg); }
    public void logDebug(String msg) { log(LEVEL_DEBUG, "DEBUG", msg); }

    private void log(int level, String levelName, String msg) {
        if (level > logLevel) return;
        String line = "[" + LocalDateTime.now().format(FMT) + "] [" + levelName + "] " + msg;
        addToBuffer(line);
        writeToFile(line);
        if (level <= LEVEL_WARN) {
            System.out.println("[Ceres] " + line);
        }
    }

    private void addToBuffer(String line) {
        buffer.addLast(line);
        while (buffer.size() > MAX_BUFFER) {
            buffer.removeFirst();
        }
    }

    private void writeToFile(String line) {
        if (fileWriter == null) return;
        try {
            fileWriter.write(line);
            fileWriter.newLine();
            fileWriter.flush();
        } catch (IOException e) {
            System.err.println("[Ceres] Log write failed: " + e.getMessage());
        }
    }

    public List<String> getRecentLines() {
        return new ArrayList<>(buffer);
    }

    public void close() {
        if (fileWriter != null) {
            try { fileWriter.close(); } catch (IOException ignored) {}
        }
    }
}
