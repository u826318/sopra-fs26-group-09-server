package ch.uzh.ifi.hase.soprafs26.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;

public class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserService userService;

	private User testUser;

	@BeforeEach
	public void setup() {
		MockitoAnnotations.openMocks(this);

		// given
		testUser = new User();
		testUser.setId(1L);
		testUser.setName("testName");
		testUser.setUsername("testUsername");
		testUser.setPassword("password123");

		// when -> any object is being save in the userRepository -> return the dummy
		// user
		Mockito.when(userRepository.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	public void createUser_validInputs_success() {
		// when -> any object is being save in the userRepository -> return the dummy
		// testUser
		User createdUser = userService.createUser(testUser);

		// then
		Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any());

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
		// given -> a first user has already been created
		Mockito.when(userRepository.findByUsername(Mockito.any())).thenReturn(testUser);

		// then -> attempt to create second user with same user -> check that an error
		// is thrown
		assertThrows(ResponseStatusException.class, () -> userService.createUser(testUser));
	}

	@Test
	public void loginUser_validCredentials_success() {
		User persistedUser = new User();
		persistedUser.setId(1L);
		persistedUser.setName("testName");
		persistedUser.setUsername("testUsername");
		persistedUser.setPassword(new BCryptPasswordEncoder().encode("password123"));
		Mockito.when(userRepository.findByUsername("testUsername")).thenReturn(persistedUser);

		User loggedInUser = userService.loginUser("testUsername", "password123");

		assertEquals(UserStatus.ONLINE, loggedInUser.getStatus());
		assertNotNull(loggedInUser.getToken());
		Mockito.verify(userRepository, Mockito.times(1)).flush();
	}

	@Test
	public void loginUser_invalidCredentials_throwsUnauthorized() {
		User persistedUser = new User();
		persistedUser.setUsername("testUsername");
		persistedUser.setPassword(new BCryptPasswordEncoder().encode("password123"));
		Mockito.when(userRepository.findByUsername("testUsername")).thenReturn(persistedUser);

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> userService.loginUser("testUsername", "wrongPassword"));
		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
	}

	@Test
	public void logoutUser_validToken_success() {
		testUser.setToken("token-123");
		Mockito.when(userRepository.findByToken("token-123")).thenReturn(testUser);

		userService.logoutUser("token-123");

		assertEquals(UserStatus.OFFLINE, testUser.getStatus());
		assertNull(testUser.getToken());
		Mockito.verify(userRepository, Mockito.times(1)).flush();
	}

}
