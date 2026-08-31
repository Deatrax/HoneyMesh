package com.honeymesh.threatengine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlocklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private BlocklistService blocklistService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        blocklistService = new BlocklistService(redisTemplate);
    }

    @Test
    @DisplayName("Case 4: CRITICAL assessment creates Redis block with honeymesh:block:<ip>, value='true', TTL=300s")
    void testBlockCreatesKeyWithValueTrueAndTtl() {
        String sourceIp = "203.0.113.7";

        blocklistService.block(sourceIp);

        String expectedKey = "honeymesh:block:203.0.113.7";
        verify(valueOperations).set(eq(expectedKey), eq("true"), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("Custom TTL block: supports short test TTL for key creation")
    void testBlockWithCustomDuration() {
        String sourceIp = "192.168.1.50";
        Duration customTtl = Duration.ofSeconds(2);

        blocklistService.block(sourceIp, customTtl);

        String expectedKey = "honeymesh:block:192.168.1.50";
        verify(valueOperations).set(eq(expectedKey), eq("true"), eq(customTtl));
    }

    @Test
    @DisplayName("Case 7: IP Isolation - checking IP A does not affect IP B")
    void testIpIsolation() {
        String ipA = "10.0.0.1";
        String ipB = "10.0.0.2";

        when(redisTemplate.hasKey("honeymesh:block:10.0.0.1")).thenReturn(true);
        when(redisTemplate.hasKey("honeymesh:block:10.0.0.2")).thenReturn(false);

        assertThat(blocklistService.isBlocked(ipA)).isTrue();
        assertThat(blocklistService.isBlocked(ipB)).isFalse();
    }

    @Test
    @DisplayName("Case 5: TTL inspection returns remaining seconds")
    void testGetRemainingTtlSeconds() {
        String sourceIp = "172.16.0.5";
        String expectedKey = "honeymesh:block:172.16.0.5";

        when(redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS)).thenReturn(295L);

        long remainingTtl = blocklistService.getRemainingTtlSeconds(sourceIp);

        assertThat(remainingTtl).isEqualTo(295L);
    }

    @Test
    @DisplayName("Case 8: Repeated CRITICAL activity refreshes the 300-second block TTL")
    void testRepeatedCriticalActivityRefreshesTtl() {
        String sourceIp = "198.51.100.42";
        String expectedKey = "honeymesh:block:198.51.100.42";

        // First CRITICAL hit
        blocklistService.block(sourceIp);

        // Second CRITICAL hit
        blocklistService.block(sourceIp);

        // Verify set was called twice with 300 seconds TTL
        verify(valueOperations, times(2)).set(eq(expectedKey), eq("true"), eq(Duration.ofSeconds(300)));
    }
}
