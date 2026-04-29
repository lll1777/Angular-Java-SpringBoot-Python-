package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.User;
import com.gov.serviceplatform.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    boolean existsByUsername(String username);
    
    List<User> findByDepartmentId(Long departmentId);
    
    List<User> findByRole(UserRole role);
    
    List<User> findByDepartmentIdAndRole(Long departmentId, UserRole role);
}
