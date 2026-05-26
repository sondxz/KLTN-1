package com.web.repository;

import com.web.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    @Query(value = "select u from User u where u.username = ?1")
    Optional<User> findByUsername(String username);

    @Query(value = "select u from User u where u.username = ?1 and u.id <> ?2")
    Optional<User> findByUsernameAndId(String username, Long id);

    @Query(value = "select u from User u where u.email = ?1")
    Optional<User> findByEmail(String email);
    
    @Query(value = "select u from User u where LOWER(u.email) = LOWER(?1)")
    Optional<User> findByEmailIgnoreCase(String email);

    @Query(value = "select u from User u where u.email = ?1 and u.id <> ?2")
    Optional<User> findByEmailAndId(String email, Long id);

    @Query(value = "select u.* from users u where u.id = ?1", nativeQuery = true)
    Optional<User> findById(Long id);

    @Query(value = "select u from User u where u.activation_key = ?1 and u.email = ?2")
    Optional<User> getUserByActivationKeyAndEmail(String key, String email);

    @Query("select u from User u where u.authorities.name = ?1")
    List<User> getUserByRole(String role);

    @Query("select count(u.id) from User u where u.authorities.name = ?1")
    public Double countAdmin(String role);

    @Query("select u from User u where u.authorities.name = ?2 and (u.email like ?1 or u.fullname like ?1 or u.username like ?1 or u.phone like ?1 )")
    Page<User> getUserByRole(String seearch,String role, Pageable pageable);

    @Query("select u from User u where u.email like ?1 or u.fullname like ?1 or u.phone like ?1 or u.username like ?1")
    Page<User> findAll(String search,Pageable pageable);

    @Query("select u.username from User u where u.id = ?1")
    String findUsernameById(Long id);

    @Query("select count(u) from User u where u.actived = ?1")
    Long countByActived(Boolean actived);

    @Query("select count(u) from User u where u.createdDate >= ?1")
    Long countByCreatedAtAfter(java.time.LocalDateTime date);

    // For CSV export
    @Query("select u from User u where u.email like ?1 or u.fullname like ?1 or u.phone like ?1 or u.username like ?1")
    List<User> findAllForExport(String search);

    @Query("select u from User u where u.authorities.name = ?2 and (u.email like ?1 or u.fullname like ?1 or u.username like ?1 or u.phone like ?1)")
    List<User> getUserByRoleAll(String search, String role);
}
