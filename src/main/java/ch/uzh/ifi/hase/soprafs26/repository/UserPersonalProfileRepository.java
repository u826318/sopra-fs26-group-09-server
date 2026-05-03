package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.UserPersonalProfile;

@Repository("userPersonalProfileRepository")
public interface UserPersonalProfileRepository extends JpaRepository<UserPersonalProfile, Long> {
    
    Optional<UserPersonalProfile> findByUser_Id(Long userId);
}
