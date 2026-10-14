package com.catsflow.catsflow.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class LineIdTokenVerifier {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${line.channel.id:}")
    private String channelId;

    public Optional<String> verify(String idToken) {
        if (idToken == null || idToken.isBlank() || channelId == null || channelId.isBlank()) {
            return Optional.empty();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id_token", idToken);
        form.add("client_id", channelId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    "https://api.line.me/oauth2/v2.1/verify",
                    new HttpEntity<>(form, headers), Map.class);
            if (response == null || !channelId.equals(String.valueOf(response.get("aud")))) {
                return Optional.empty();
            }
            Object subject = response.get("sub");
            return subject == null || String.valueOf(subject).isBlank()
                    ? Optional.empty()
                    : Optional.of(String.valueOf(subject));
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }
}
