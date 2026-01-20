package com.example.LlmSpring.Repository;

import com.example.LlmSpring.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmail(String email);
    boolean existsByUserId(String userId);
}
