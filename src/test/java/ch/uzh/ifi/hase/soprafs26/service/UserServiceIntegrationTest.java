package ch.uzh.ifi.hase.soprafs26.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the UserResource REST resource.
 *
 * @see UserService
 */
@WebAppConfiguration
@SpringBootTest
public class UserServiceIntegrationTest {

	@Qualifier("userRepository")
	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserService userService;

	@BeforeEach
	public void setup() {
		userRepository.deleteAll();
	}

	@Test
	public void createUser_validInputs_success() {
		// given
		assertNull(userRepository.findByUsername("testUsername"));

		User testUser = new User();
		testUser.setName("testName");
		testUser.setUsername("testUsername");
		testUser.setPassword("password123");

		// when
		User createdUser = userService.createUser(testUser);

		// then
		assertEquals(testUser.getId(), createdUser.getId());
		assertEquals(testUser.getName(), createdUser.getName());
		assertEquals(testUser.getUsername(), createdUser.getUsername());
		assertNotNull(createdUser.getToken());
		assertEquals(UserStatus.ONLINE, createdUser.getStatus());
		assertNotEquals("password123", createdUser.getPassword());
		assertTrue(new BCryptPasswordEncoder().matches("password123", createdUser.getPassword()));
	}

	@Test
	public void createUser_duplicateUsername_throwsException() {
		assertNull(userRepository.findByUsername("testUsername"));

		User testUser = new User();
		testUser.setName("testName");
		testUser.setUsername("testUsername");
		testUser.setPassword("password123");
		userService.createUser(testUser);

		// attempt to create second user with same username
		User testUser2 = new User();

		// change the name but forget about the username
		testUser2.setName("testName2");
		testUser2.setUsername("testUsername");
		testUser2.setPassword("password123");

		// check that an error is thrown
		assertThrows(ResponseStatusException.class, () -> userService.createUser(testUser2));
	}

	@Test
	public void loginAndLogout_validFlow_success() {
		User registeredUser = new User();
		registeredUser.setName("testName");
		registeredUser.setUsername("testUsername");
		registeredUser.setPassword("password123");
		userService.registerUser(registeredUser);

		User loggedInUser = userService.loginUser("testUsername", "password123");
		assertNotNull(loggedInUser.getToken());
		assertEquals(UserStatus.ONLINE, loggedInUser.getStatus());

		String token = loggedInUser.getToken();
		userService.logoutUser(token);
		User persistedUser = userRepository.findByUsername("testUsername");

		assertEquals(UserStatus.OFFLINE, persistedUser.getStatus());
		assertNull(persistedUser.getToken());
	}

	@Test
	public void login_invalidPassword_throwsUnauthorized() {
		User registeredUser = new User();
		registeredUser.setName("testName");
		registeredUser.setUsername("testUsername");
		registeredUser.setPassword("password123");
		userService.registerUser(registeredUser);

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> userService.loginUser("testUsername", "wrong"));
		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
	}
}
