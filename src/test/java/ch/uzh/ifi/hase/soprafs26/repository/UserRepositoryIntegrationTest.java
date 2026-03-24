package ch.uzh.ifi.hase.soprafs26.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
public class UserRepositoryIntegrationTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private UserRepository userRepository;

	@Test
	public void findByName_success() {
		// given
		User user = new User();
		user.setName("Firstname Lastname");
		user.setUsername("firstname@lastname");
		user.setPassword("password123");
		user.setStatus(UserStatus.OFFLINE);
		user.setToken("1");
		user.setCreatedAt(Instant.now());

		entityManager.persist(user);
		entityManager.flush();

		// when
		User found = userRepository.findByName(user.getName());

		// then
		assertNotNull(found.getId());
		assertEquals(found.getName(), user.getName());
		assertEquals(found.getUsername(), user.getUsername());
		assertEquals(found.getToken(), user.getToken());
		assertEquals(found.getStatus(), user.getStatus());
	}

	@Test
	public void findByToken_success() {
		User user = new User();
		user.setName("token user");
		user.setUsername("token-username");
		user.setPassword("password123");
		user.setStatus(UserStatus.ONLINE);
		user.setToken("token-123");
		user.setCreatedAt(Instant.now());

		entityManager.persist(user);
		entityManager.flush();

		User found = userRepository.findByToken("token-123");

		assertNotNull(found.getId());
		assertEquals("token-username", found.getUsername());
		assertEquals("token-123", found.getToken());
	}
}
