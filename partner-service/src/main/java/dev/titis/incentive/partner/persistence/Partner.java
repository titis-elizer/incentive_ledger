package dev.titis.incentive.partner.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partners")
public class Partner {
    @Id private UUID id;
    @Column(nullable = false, length = 30, unique = true) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected Partner() { }
    public Partner(String code, String name) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }
    public void changeStatus(Status status) { this.status = status; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public enum Status { ACTIVE, INACTIVE }
}