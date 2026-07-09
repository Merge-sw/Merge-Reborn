package com.merge.merge.shared.security;

import com.merge.merge.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenServiceTest {

    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void rotationIssuesNewTokenAndInvalidatesOld() {
        UUID studentId = UUID.randomUUID();
        String original = refreshTokenService.issue(studentId);

        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(original);

        assertThat(rotated.studentId()).isEqualTo(studentId);
        assertThat(rotated.newTokenId()).isNotEqualTo(original);

        // The old token has been tombstoned. Presenting it again triggers
        // reuse detection (TokenReuseDetectedException, which extends RuntimeException).
        assertThatThrownBy(() -> refreshTokenService.rotate(original))
                .isInstanceOf(TokenReuseDetectedException.class)
                .hasMessageContaining("reuse detected");
    }

    @Test
    void reuseOfRotatedTokenRevokesEntireTokenSetForThatStudent() {
        UUID studentId = UUID.randomUUID();
        String first = refreshTokenService.issue(studentId);
        String second = refreshTokenService.issue(studentId);

        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(first);

        // Presenting the already-rotated "first" token triggers reuse detection:
        // all sessions for this student are revoked before the exception is thrown.
        assertThatThrownBy(() -> refreshTokenService.rotate(first))
                .isInstanceOf(TokenReuseDetectedException.class)
                .hasMessageContaining("reuse detected");

        // After revocation, the freshly-rotated token and the unrelated second
        // token must both be dead.
        assertThatThrownBy(() -> refreshTokenService.rotate(rotated.newTokenId()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(second))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeInvalidatesSpecificToken() {
        UUID studentId = UUID.randomUUID();
        String tokenId = refreshTokenService.issue(studentId);

        refreshTokenService.revoke(tokenId);

        assertThatThrownBy(() -> refreshTokenService.rotate(tokenId))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
