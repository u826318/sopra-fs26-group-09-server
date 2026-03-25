package ch.uzh.ifi.hase.soprafs26.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserAuthDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserLoginDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserLogoutDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.UserService;

import java.util.ArrayList;
import java.util.List;

/**
 * User Controller
 * This class is responsible for handling all REST request that are related to
 * the user.
 * The controller will receive the request and delegate the execution to the
 * UserService and finally return the result.
 */
@RestController
public class UserController {

	private final UserService userService;

	UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/users")
	@ResponseStatus(HttpStatus.OK)
	public List<UserGetDTO> getAllUsers() {
		// fetch all users in the internal representation
		List<User> users = userService.getUsers();
		List<UserGetDTO> userGetDTOs = new ArrayList<>();

		// convert each user to the API representation
		for (User user : users) {
			userGetDTOs.add(DTOMapper.INSTANCE.convertEntityToUserGetDTO(user));
		}
		return userGetDTOs;
	}

	@PostMapping("/users")
	@ResponseStatus(HttpStatus.CREATED)
	public UserGetDTO createUser(@RequestBody UserPostDTO userPostDTO) {
		// convert API user to internal representation
		User userInput = toUserEntity(userPostDTO);

		// create user
		User createdUser = userService.createUser(userInput);
		// convert internal representation of user back to API
		return DTOMapper.INSTANCE.convertEntityToUserGetDTO(createdUser);
	}

	@PostMapping("/users/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserAuthDTO register(@RequestBody UserPostDTO userPostDTO) {
		User userInput = toUserEntity(userPostDTO);
		User createdUser = userService.registerUser(userInput);
		return toAuthDTO(createdUser);
	}

	@PostMapping("/users/login")
	@ResponseStatus(HttpStatus.OK)
	public UserAuthDTO login(@RequestBody UserLoginDTO userLoginDTO) {
		User loggedInUser = userService.loginUser(userLoginDTO.getUsername(), userLoginDTO.getPassword());
		return toAuthDTO(loggedInUser);
	}

	@PostMapping("/users/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@RequestBody UserLogoutDTO userLogoutDTO) {
		userService.logoutUser(userLogoutDTO.getToken());
	}

	private User toUserEntity(UserPostDTO userPostDTO) {
		return DTOMapper.INSTANCE.convertUserPostDTOtoEntity(userPostDTO);
	}

	private UserAuthDTO toAuthDTO(User user) {
		return DTOMapper.INSTANCE.convertEntityToUserAuthDTO(user);
	}
}
