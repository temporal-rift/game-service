package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.JoinCodePort;

/**
 * Generates join codes from an unambiguous alphabet (no I/L/O/U/0/1) at 8 characters — a ~6.6e11
 * space, sized against online guessing rather than a 6-hex-char space an attacker could enumerate.
 *
 * <p>No existence pre-check: check-then-insert is racy, and at this entropy a collision is rarer
 * than a transaction failing for any other reason. The unique index on {@code lobby.join_code} is
 * the hard guarantee; the astronomically unlikely loser surfaces as a retryable failure.
 */
@Component
class JoinCodeAdapter implements JoinCodePort {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        var code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
