package org.openelisglobal.security.service;

import org.springframework.http.HttpStatus;

public class ModuleAccessResult {

    private final HttpStatus status;
    private final boolean allowed;
    private final String message;

    private ModuleAccessResult(HttpStatus status, boolean allowed, String message) {
        this.status = status;
        this.allowed = allowed;
        this.message = message;
    }

    public static ModuleAccessResult allowed() {
        return new ModuleAccessResult(HttpStatus.OK, true, null);
    }

    public static ModuleAccessResult denied() {
        return new ModuleAccessResult(HttpStatus.OK, false, null);
    }

    public static ModuleAccessResult unauthorized(String message) {
        return new ModuleAccessResult(HttpStatus.UNAUTHORIZED, false, message);
    }

    public static ModuleAccessResult badRequest(String message) {
        return new ModuleAccessResult(HttpStatus.BAD_REQUEST, false, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getMessage() {
        return message;
    }
}
