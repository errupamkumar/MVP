package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A wrong boarding OTP. The attempt counter is incremented before this is
 * thrown, and {@code board()} is marked noRollbackFor this type, so the
 * increment commits and brute-forcing the 4 digits is capped.
 */
public class OtpMismatchException extends ApiException {

    public OtpMismatchException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "OTP_INVALID", message);
    }
}
