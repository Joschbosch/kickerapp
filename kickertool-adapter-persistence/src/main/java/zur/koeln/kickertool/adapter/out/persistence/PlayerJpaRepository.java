package zur.koeln.kickertool.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlayerJpaRepository extends JpaRepository<PlayerEntity, UUID> {

    Optional<PlayerEntity> findBySubject(String subject);
}
