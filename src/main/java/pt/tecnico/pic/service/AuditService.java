package pt.tecnico.pic.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import pt.tecnico.pic.util.PathSanitizer;

public class AuditService {

    private static final Pattern SENSITIVE_VALUE_PATTERN =
            Pattern.compile("(?i)\\b(password|pin|senha)\\b\\s*[:=]\\s*\\S+");

    private static final Pattern PATH_TOKEN_PATTERN =
            Pattern.compile("\\S*[\\\\/]\\S+");

    private final List<Log> logs = new ArrayList<>();
    private int nextLogId = 1;

    public synchronized void log(Integer accountId,
                                 String username,
                                 Role actorRole,
                                 ActionType action,
                                 String filePath,
                                 OperationResult result,
                                 String message) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(result, "result must not be null");

        String fileName = PathSanitizer.toFileName(filePath);
        String safeMessage = sanitizeMessage(message);

        Log log = new Log(
                nextLogId++,
                accountId,
                LocalDateTime.now(),
                username,
                actorRole,
                action,
                fileName,
                result,
                safeMessage
        );

        logs.add(log);
    }

    public synchronized List<LogDTO> getLogs() {
        return logs.stream()
                .map(LogDTO::fromLog)
                .toList();
    }

    public synchronized List<LogDTO> getLogs(LogFilter filter) {
        if (filter == null) {
            return getLogs();
        }

        return logs.stream()
                .filter(log -> matchesFilter(log, filter))
                .map(LogDTO::fromLog)
                .toList();
    }

    private static boolean matchesFilter(Log log, LogFilter filter) {
        if (filter.getUsername() != null && !filter.getUsername().equals(log.getUsername())) {
            return false;
        }

        if (filter.getActorRole() != null && filter.getActorRole() != log.getActorRole()) {
            return false;
        }

        if (filter.getActionType() != null && filter.getActionType() != log.getAction()) {
            return false;
        }

        if (filter.getResult() != null && filter.getResult() != log.getResult()) {
            return false;
        }

        if (filter.getStartDate() != null && log.getTimestamp().isBefore(filter.getStartDate())) {
            return false;
        }

        return filter.getEndDate() == null || !log.getTimestamp().isAfter(filter.getEndDate());
    }

    private static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String withoutSensitiveValues = SENSITIVE_VALUE_PATTERN
                .matcher(message)
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
