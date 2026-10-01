package vn.bnn.rms.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 429, with how many seconds the client should wait; sent as the Retry-After header (BR-30, BR-31). */
@Getter
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
