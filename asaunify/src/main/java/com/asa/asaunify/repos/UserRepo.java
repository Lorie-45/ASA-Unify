package com.asa.asaunify.repos;

import com.asa.asaunify.entity.Department;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepo extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByDepartment(Department department);
    List<User> findByRole (Role role);
    List<User> findByDepartmentAndIsActiveTrue(Department department);
    List<User> findByRoleAndIsActiveTrue(Role role);

    @Query("SELECT u FROM User u WHERE u.department.id = :departmentId AND u.isActive = true")
    List<User> findActiveUsersByDepartmentId(UUID departmentId);

}
