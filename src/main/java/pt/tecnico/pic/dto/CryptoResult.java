package pt.tecnico.pic.dto;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;

public class CryptoResult {
    private final OperationResult result;
    private final String message;
    private final String outputFilePath;
    private final String inputFilePath;
    private final ActionType actionType;

    public CryptoResult(OperationResult result, String message, String inputFilePath, String outputFilePath, ActionType actionType) {
        this.result = result;
        this.message = message;
        this.outputFilePath = outputFilePath;
        this.inputFilePath = inputFilePath;
        this.actionType = actionType;
    }

    public OperationResult getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public String getInputFilePath() {
        return inputFilePath;
    }

    public ActionType getActionType() {
        return actionType;
    }
}
