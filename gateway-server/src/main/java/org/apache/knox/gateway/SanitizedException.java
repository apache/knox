package org.apache.knox.gateway;

import javax.servlet.ServletException;

public class SanitizedException extends ServletException {
    public SanitizedException(String message) {
        super(message);
    }
}
