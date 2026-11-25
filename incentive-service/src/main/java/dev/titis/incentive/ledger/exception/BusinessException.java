package dev.titis.incentive.ledger.exception;

public class BusinessException extends RuntimeException {
    private final int status;
    private final String code;
    public BusinessException(int status, String code, String detail) {
        super(detail); this.status = status; this.code = code;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
}