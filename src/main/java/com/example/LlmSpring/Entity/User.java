package com.example.LlmSpring.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name="User")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @Column(length=20, nullable=false)
    private String userId;
    @Column(nullable=false, length=100)
    private String passwordHash;
    @Column(length=30, nullable=false)
    private String name;
    @Column(length=255, nullable=false, unique=true)
    private String email;
    @CreationTimestamp
    @Column(nullable=false, updatable=false)
    private LocalDateTime regDate;
    @Column(columnDefinition = "TEXT")
    private String filePath;
    @Column(nullable = false)
    @Builder.Default
    private boolean githubOauth = false;
    @Column(nullable=true)
    private LocalDateTime deletedAt;
}
