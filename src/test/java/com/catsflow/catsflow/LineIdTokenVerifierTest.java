package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Service.LineIdTokenVerifier;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.mockito.Mockito;

class LineIdTokenVerifierTest {

    private static final String CHANNEL_ID = "test-channel-id";

    private final RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

    private LineIdTokenVerifier verifier() {
        LineIdTokenVerifier verifier = new LineIdTokenVerifier();
        ReflectionTestUtils.setField(verifier, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(verifier, "channelId", CHANNEL_ID);
        return verifier;
    }

    @Test
    void returnsEmptyWhenIdTokenIsNull() {
        assertThat(verifier().verify(null)).isEmpty();
    }

    @Test
    void returnsEmptyWhenIdTokenIsBlank() {
        assertThat(verifier().verify("   ")).isEmpty();
    }

    @Test
    void returnsEmptyWhenChannelIdIsBlank() {
        LineIdTokenVerifier verifier = new LineIdTokenVerifier();
        ReflectionTestUtils.setField(verifier, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(verifier, "channelId", "");

        assertThat(verifier.verify("some-token")).isEmpty();
    }

    @Test
    void returnsEmptyWhenLineApiThrows() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("connection failed"));

        assertThat(verifier().verify("some-token")).isEmpty();
    }

    @Test
    void returnsEmptyWhenResponseIsNull() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        assertThat(verifier().verify("some-token")).isEmpty();
    }

    @Test
    void returnsEmptyWhenAudienceDoesNotMatchChannelId() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("aud", "other-channel", "sub", "U1234"));

        assertThat(verifier().verify("some-token")).isEmpty();
    }

    @Test
    void returnsEmptyWhenSubjectIsMissing() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("aud", CHANNEL_ID));

        assertThat(verifier().verify("some-token")).isEmpty();
    }

    @Test
    void returnsEmptyWhenSubjectIsBlank() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("aud", CHANNEL_ID, "sub", " "));

        assertThat(verifier().verify("some-token")).isEmpty();
    }

    @Test
    void returnsSubjectWhenVerificationSucceeds() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("aud", CHANNEL_ID, "sub", "U1234567890"));

        Optional<String> result = verifier().verify("valid-token");

        assertThat(result).contains("U1234567890");
    }
}
