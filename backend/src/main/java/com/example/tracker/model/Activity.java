package com.example.tracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "activities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long boardId;

    private Long taskId;

    @Column(nullable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private long seq;

    @Column(nullable = false)
    private Instant createdAt;
}
