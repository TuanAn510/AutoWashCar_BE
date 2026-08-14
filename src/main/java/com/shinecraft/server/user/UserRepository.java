package com.shinecraft.server.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    List<User> findByRoleOrderByIdAsc(UserRole role);

    List<User> findByRoleAndIsActiveTrue(UserRole role);
}
