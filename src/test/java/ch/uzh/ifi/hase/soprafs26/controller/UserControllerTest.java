package ch.uzh.ifi.hase.soprafs26.controller;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.BDDMockito.given;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserLoginDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserLogoutDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs26.service.UserService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * UserControllerTest
 * This is a WebMvcTest which allows to test the UserController i.e. GET/POST
 * request without actually sending them over the network.
 * This tests if the UserController works.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private UserRepository userRepository;

	private static final String TEST_TOKEN = "test-token";
	private User authenticatedUser;

	@BeforeEach
	void setUp() {
		authenticatedUser = new User();
		authenticatedUser.setId(99L);
		authenticatedUser.setUsername("authUser");
		authenticatedUser.setToken(TEST_TOKEN);
		authenticatedUser.setStatus(UserStatus.ONLINE);
		given(userRepository.findByToken(TEST_TOKEN)).willReturn(authenticatedUser);
	}

	@Test
	void givenUsers_whenGetUsers_thenReturnJsonArray() throws Exception {
		// given
		User user = new User();
		user.setName("Firstname Lastname");
		user.setUsername("firstname@lastname");
		user.setStatus(UserStatus.OFFLINE);

		List<User> allUsers = Collections.singletonList(user);

		// this mocks the UserService -> we define above what the userService should
		// return when getUsers() is called
		given(userService.getUsers()).willReturn(allUsers);

		// when
		MockHttpServletRequestBuilder getRequest = get("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", TEST_TOKEN);

		// then
		mockMvc.perform(getRequest).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name", is(user.getName())))
				.andExpect(jsonPath("$[0].username", is(user.getUsername())))
				.andExpect(jsonPath("$[0].status", is(user.getStatus().toString())));
	}

	@Test
	void createUser_validInput_userCreated() throws Exception {
		// given
		User user = new User();
		user.setId(1L);
		user.setName("Test User");
		user.setUsername("testUsername");
		user.setPassword("password123");
		user.setToken("1");
		user.setStatus(UserStatus.ONLINE);

		UserPostDTO userPostDTO = new UserPostDTO();
		userPostDTO.setName("Test User");
		userPostDTO.setUsername("testUsername");
		userPostDTO.setPassword("password123");

		given(userService.createUser(Mockito.any())).willReturn(user);

		// when/then -> do the request + validate the result
		MockHttpServletRequestBuilder postRequest = post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", TEST_TOKEN)
				.content(asJsonString(userPostDTO));

		// then
		mockMvc.perform(postRequest)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", is(user.getId().intValue())))
				.andExpect(jsonPath("$.name", is(user.getName())))
				.andExpect(jsonPath("$.username", is(user.getUsername())))
				.andExpect(jsonPath("$.status", is(user.getStatus().toString())));
	}

	@Test
	void registerUser_validInput_userCreated() throws Exception {
		User user = new User();
		user.setId(2L);
		user.setName("Register User");
		user.setUsername("registerUsername");
		user.setPassword("password123");
		user.setToken("token-register");
		user.setStatus(UserStatus.ONLINE);

		UserPostDTO userPostDTO = new UserPostDTO();
		userPostDTO.setUsername("registerUsername");
		userPostDTO.setPassword("password123");

		given(userService.registerUser(Mockito.any())).willReturn(user);

		MockHttpServletRequestBuilder postRequest = post("/users/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(userPostDTO));

		mockMvc.perform(postRequest)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", is(user.getId().intValue())))
				.andExpect(jsonPath("$.username", is(user.getUsername())))
				.andExpect(jsonPath("$.token", is(user.getToken())))
				.andExpect(jsonPath("$.status", is(user.getStatus().toString())));
	}

	@Test
	void loginUser_validInput_success() throws Exception {
		User user = new User();
		user.setId(3L);
		user.setName("Login User");
		user.setUsername("loginUsername");
		user.setPassword("password123");
		user.setToken("token-login");
		user.setStatus(UserStatus.ONLINE);

		UserLoginDTO userLoginDTO = new UserLoginDTO();
		userLoginDTO.setUsername("loginUsername");
		userLoginDTO.setPassword("password123");

		given(userService.loginUser(Mockito.any(), Mockito.any())).willReturn(user);

		MockHttpServletRequestBuilder postRequest = post("/users/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(userLoginDTO));

		mockMvc.perform(postRequest)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(user.getId().intValue())))
				.andExpect(jsonPath("$.username", is(user.getUsername())))
				.andExpect(jsonPath("$.token", is(user.getToken())))
				.andExpect(jsonPath("$.status", is(user.getStatus().toString())));
	}

	@Test
	void logoutUser_validInput_success() throws Exception {
		UserLogoutDTO userLogoutDTO = new UserLogoutDTO();
		userLogoutDTO.setToken("token-logout");

		MockHttpServletRequestBuilder postRequest = post("/users/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", TEST_TOKEN)
				.content(asJsonString(userLogoutDTO));

		mockMvc.perform(postRequest).andExpect(status().isNoContent());
	}

		@Test
	void registerUser_duplicateUsername_returnsBadRequest() throws Exception {
		UserPostDTO userPostDTO = new UserPostDTO();
		userPostDTO.setUsername("existingUser");
		userPostDTO.setPassword("password123");

		given(userService.registerUser(Mockito.any()))
				.willThrow(new ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"The username is already taken. Therefore, the user could not be created!"));

		MockHttpServletRequestBuilder postRequest = post("/users/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(userPostDTO));

		mockMvc.perform(postRequest)
				.andExpect(status().isBadRequest());
	}

	@Test
	void registerUser_emptyUsername_returnsBadRequest() throws Exception {
		UserPostDTO userPostDTO = new UserPostDTO();
		userPostDTO.setUsername("   ");
		userPostDTO.setPassword("password123");

		given(userService.registerUser(Mockito.any()))
				.willThrow(new ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"Username must not be empty."));

		MockHttpServletRequestBuilder postRequest = post("/users/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(userPostDTO));

		mockMvc.perform(postRequest)
				.andExpect(status().isBadRequest());
	}

	@Test
	void loginUser_invalidCredentials_returnsUnauthorized() throws Exception {
		UserLoginDTO userLoginDTO = new UserLoginDTO();
		userLoginDTO.setUsername("loginUsername");
		userLoginDTO.setPassword("wrongPassword");

		given(userService.loginUser(Mockito.any(), Mockito.any()))
				.willThrow(new ResponseStatusException(
						HttpStatus.UNAUTHORIZED,
						"Invalid username or password."));

		MockHttpServletRequestBuilder postRequest = post("/users/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(userLoginDTO));

		mockMvc.perform(postRequest)
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutUser_invalidToken_returnsUnauthorized() throws Exception {
		UserLogoutDTO userLogoutDTO = new UserLogoutDTO();
		userLogoutDTO.setToken("invalid-token");

		Mockito.doThrow(new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"Invalid token."))
				.when(userService).logoutUser("invalid-token");

		MockHttpServletRequestBuilder postRequest = post("/users/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", TEST_TOKEN)
				.content(asJsonString(userLogoutDTO));

		mockMvc.perform(postRequest)
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutUser_emptyToken_returnsBadRequest() throws Exception {
		UserLogoutDTO userLogoutDTO = new UserLogoutDTO();
		userLogoutDTO.setToken("   ");

		Mockito.doThrow(new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Token must not be empty."))
				.when(userService).logoutUser("   ");

		MockHttpServletRequestBuilder postRequest = post("/users/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", TEST_TOKEN)
				.content(asJsonString(userLogoutDTO));

		mockMvc.perform(postRequest)
				.andExpect(status().isBadRequest());
	}

	/**
	 * Helper Method to convert userPostDTO into a JSON string such that the input
	 * can be processed
	 * Input will look like this: {"name": "Test User", "username": "testUsername"}
	 * 
	 * @param object
	 * @return string
	 */
	private String asJsonString(final Object object) {
		try {
			return new ObjectMapper().writeValueAsString(object);
		} catch (JacksonException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					String.format("The request body could not be created.%s", e.toString()));
		}
	}
}