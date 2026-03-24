package ch.uzh.ifi.hase.soprafs26.controller;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserLoginDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserLogoutDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs26.service.UserService;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserControllerTest
 * This is a WebMvcTest which allows to test the UserController i.e. GET/POST
 * request without actually sending them over the network.
 * This tests if the UserController works.
 */
@WebMvcTest(UserController.class)
public class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	public void givenUsers_whenGetUsers_thenReturnJsonArray() throws Exception {
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
		MockHttpServletRequestBuilder getRequest = get("/users").contentType(MediaType.APPLICATION_JSON);

		// then
		mockMvc.perform(getRequest).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name", is(user.getName())))
				.andExpect(jsonPath("$[0].username", is(user.getUsername())))
				.andExpect(jsonPath("$[0].status", is(user.getStatus().toString())));
	}

	@Test
	public void createUser_validInput_userCreated() throws Exception {
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
	public void registerUser_validInput_userCreated() throws Exception {
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
	public void loginUser_validInput_success() throws Exception {
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
	public void logoutUser_validInput_success() throws Exception {
		UserLogoutDTO userLogoutDTO = new UserLogoutDTO();
		userLogoutDTO.setToken("token-logout");

		MockHttpServletRequestBuilder postRequest = post("/users/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(userLogoutDTO));

		mockMvc.perform(postRequest).andExpect(status().isNoContent());
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