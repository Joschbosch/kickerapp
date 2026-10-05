package zur.koeln.kickertool.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "player")
class PlayerEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true)
    String subject;

    @Column(name = "display_name", nullable = false)
    String displayName;

    protected PlayerEntity() {
    }

    PlayerEntity(UUID id, String subject, String displayName) {
        this.id = id;
        this.subject = subject;
        this.displayName = displayName;
    }
}
