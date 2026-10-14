package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.catsflow.catsflow.Entity.LineUserLink;
import com.catsflow.catsflow.Repository.LineUserLinkRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LineUserLinkRepositoryTest {

    @Autowired
    private LineUserLinkRepository repository;

    @Test
    void saveAndFindByLineUserId() {
        repository.deleteByLineUserId("U_TEST_001");

        LineUserLink link = new LineUserLink();
        link.setUserId(1L);
        link.setLineUserId("U_TEST_001");
        link.setStatus("linked");

        LineUserLink saved = repository.save(link);

        Optional<LineUserLink> found = repository.findByLineUserId("U_TEST_001");

        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1L);
        assertThat(found.get().getLineUserId()).isEqualTo("U_TEST_001");
        assertThat(found.get().getStatus()).isEqualTo("linked");
    }
}
