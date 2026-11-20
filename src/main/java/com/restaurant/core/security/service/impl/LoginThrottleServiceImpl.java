package com.restaurant.core.security.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.security.entity.LoginThrottleEntry;
import com.restaurant.core.security.repository.LoginThrottleEntryRepository;
import com.restaurant.core.security.service.LoginThrottleService;

@Service
public class LoginThrottleServiceImpl implements LoginThrottleService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);

    private final LoginThrottleEntryRepository repository;
    private final Clock clock;

    @Autowired
    public LoginThrottleServiceImpl(LoginThrottleEntryRepository repository) {
        this(repository, Clock.systemUTC());
    }

    LoginThrottleServiceImpl(LoginThrottleEntryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public long lockedSeconds(String username) {
        Instant now = clock.instant();
        return repository.findById(throttleKeyOf(username)).map(e -> e.lockedSecondsAt(now)).orElse(0L);
    }

    /** Transaction riêng: lần sai được ghi lại kể cả khi request đăng nhập kết thúc bằng lỗi 401. */
    @Override
    @Transactional
    public void recordFailure(String username) {
        Instant now = clock.instant();
        String key = throttleKeyOf(username);
        repository.deleteExpired(now, now.minus(WINDOW));
        repository.insertIfAbsent(key, now);
        LoginThrottleEntry entry = repository.findByUsernameKeyForUpdate(key).orElseThrow();
        entry.recordFailure(now, MAX_FAILURES, WINDOW, LOCK);
    }

    @Override
    @Transactional
    public void recordSuccess(String username) {
        repository.deleteById(throttleKeyOf(username));
    }

    private static String throttleKeyOf(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
