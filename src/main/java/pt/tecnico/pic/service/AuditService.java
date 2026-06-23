package pt.tecnico.pic.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.Log;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.LogFilter;
import pt.tecnico.pic.store.LogStore;
import pt.tecnico.pic.util.PathSanitizer;

public class AuditService {

    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b(password|pin|senha|key|chave|secret|token)\\b\\s*[:=]\\s*"
                    + "(?:\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|\\S+)"
    );

    private static final Pattern PATH_TOKEN_PATTERN =
            Pattern.compile("\\S*[\\\\/]\\S+");

    private final LogStore logStore;

    public AuditService() {
        this(new LogStore());
    }

    public AuditService(LogStore logStore) {
        this.logStore = Objects.requireNonNull(logStore, "logStore must not be null");
    }

    public synchronized void log(Integer accountId,
                                 String username,
                                 Role actorRole,
                                 ActionType action,
                                 String filePath,
                                 OperationResult result,
                                 String message) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(result, "result must not be null");

        Log log = new Log(
                logStore.nextLogId(),
                accountId,
                LocalDateTime.now(),
                username,
                actorRole,
                action,
                PathSanitizer.toFileName(filePath),
                result,
                sanitizeMessage(message)
        );

        logStore.save(log);
    }

    public synchronized List<LogDTO> getLogs() {
        return logStore.findAll().stream()
                .map(LogDTO::fromLog)
                .toList();
    }

    public synchronized List<LogDTO> getLogs(LogFilter filter) {
        return logStore.findByFilter(filter).stream()
                .map(LogDTO::fromLog)
                .toList();
    }

    private static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String firstLine = message.lines().findFirst().orElse("");
        String withoutSensitiveValues = SENSITIVE_VALUE_PATTERN
                .matcher(firstLine)
                .replaceAll("$1=[REDACTED]");

        Matcher matcher = PATH_TOKEN_PATTERN.matcher(withoutSensitiveValues);
        StringBuffer safeMessage = new StringBuffer();

        while (matcher.find()) {
            String replacement = PathSanitizer.toFileName(matcher.group());
            matcher.appendReplacement(
                    safeMessage,
                    Matcher.quoteReplacement(replacement == null ? "" : replacement)
            );
        }

        matcher.appendTail(safeMessage);
        return safeMessage.toString();
    }
}
