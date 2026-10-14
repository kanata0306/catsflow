package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Service.LineUserLinkService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LineUserLinkServiceTest {

    @Autowired
    private LineUserLinkService service;

    @Test
    void linkUserAndFindByLineUserId() {
        String lineUserId = "U_SERVICE_TEST_001";
        service.unlink(lineUserId);

        LineUserLink link = service.linkUser(1L, lineUserId);
        Optional<LineUserLink> found = service.findByLineUserId(lineUserId);

        assertThat(link.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1L);
        assertThat(found.get().getLineUserId()).isEqualTo(lineUserId);
        assertThat(found.get().getStatus()).isEqualTo("linked");
    }

    @Test
    void unlinkRemovesLink() {
        String lineUserId = "U_SERVICE_TEST_002";
        service.linkUser(2L, lineUserId);

        service.unlink(lineUserId);

        assertThat(service.findByLineUserId(lineUserId)).isEmpty();
    }

    @Test
    void doesNotReassignLineAccountToAnotherCatsflowUser() {
        String lineUserId = "U_SERVICE_TEST_CONFLICT";
        service.unlink(lineUserId);
        service.linkUser(1L, lineUserId);

        assertThatThrownBy(() -> service.linkUser(2L, lineUserId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.findByLineUserId(lineUserId))
                .hasValueSatisfying(link -> assertThat(link.getUserId()).isEqualTo(1L));
    }
}
