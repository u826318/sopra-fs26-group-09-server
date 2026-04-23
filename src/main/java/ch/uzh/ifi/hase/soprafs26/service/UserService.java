package ch.uzh.ifi.hase.soprafs26.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * User Service
 * This class is the "worker" and responsible for all functionality related to
 * the user
 * (e.g., it creates, modifies, deletes, finds). The result will be passed back
 * to the caller.
 */
@Service
@Transactional
public class UserService {

	private final Logger log = LoggerFactory.getLogger(UserService.class);
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private final UserRepository userRepository;

	public UserService(@Qualifier("userRepository") UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public List<User> getUsers() {
		return this.userRepository.findAll();
	}

	public User getUserByToken(String token) {
		User user = userRepository.findByToken(token);
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token.");
		}
		return user;
	}

	public User createUser(User newUser) {
		return registerUser(newUser);
	}

	public User registerUser(User newUser) {
		validateCredentials(newUser.getUsername(), newUser.getPassword());
		checkIfUsernameExists(newUser.getUsername());

		if (newUser.getName() == null || newUser.getName().trim().isEmpty()) {
			newUser.setName(newUser.getUsername());
		}

		newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
		newUser.setToken(UUID.randomUUID().toString());
		newUser.setStatus(UserStatus.ONLINE);
		newUser.setCreatedAt(Instant.now());
		newUser = userRepository.save(newUser);
		userRepository.flush();

		log.debug("Registered user: {}", newUser.getUsername());
		return newUser;
	}

	public User loginUser(String username, String password) {
		validateCredentials(username, password);

		User user = userRepository.findByUsername(username);
		if (user == null || user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
		}

		user.setToken(UUID.randomUUID().toString());
		user.setStatus(UserStatus.ONLINE);
		userRepository.flush();

		log.debug("Logged in user: {}", user.getUsername());
		return user;
	}

	public void logoutUser(String token) {
		if (token == null || token.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token must not be empty.");
		}

		User user = userRepository.findByToken(token);
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token.");
		}

		user.setStatus(UserStatus.OFFLINE);
		user.setToken(null);
		userRepository.flush();
		log.debug("Logged out user: {}", user.getUsername());
	}

	/**
	 * This is a helper method that will check the uniqueness criteria of the
	 * username and the name
	 * defined in the User entity. The method will do nothing if the input is unique
	 * and throw an error otherwise.
	 *
	 * @param userToBeCreated
	 * @throws org.springframework.web.server.ResponseStatusException
	 * @see User
	 */
	private void checkIfUsernameExists(String username) {
		User userByUsername = userRepository.findByUsername(username);
		if (userByUsername != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"The username is already taken. Therefore, the user could not be created!");
		}
	}

	private void validateCredentials(String username, String password) {
		if (username == null || username.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must not be empty.");
		}
		if (password == null || password.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not be empty.");
		}
	}
}
