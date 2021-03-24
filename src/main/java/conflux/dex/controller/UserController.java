package conflux.dex.controller;

import conflux.dex.model.PagingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import conflux.dex.common.Validators;
import conflux.dex.dao.UserDao;
import conflux.dex.model.User;
import conflux.web3j.types.AddressType;

import java.util.List;

/**
 * User management
 */
@RestController
@RequestMapping("/users")
public class UserController {
	
	private UserDao dao;
	
	@Autowired
	public UserController(UserDao dao) {
		this.dao = dao;
	}
	
	/**
	 * Get user
	 * @param address user address
	 */
	@GetMapping("/{address}")
	public User get(@PathVariable String address) {
		address = AddressTool.toHex(address);
		Validators.validateAddress(address, AddressType.User, "address");
		return this.dao.getUserByName(address).mustGet();
	}

}

class UserPagingResult {
	/**
	 * Total number of users.
	 */
	public int total;
	/**
	 * Fetched users.
	 */
	public List<User> items;

	public UserPagingResult(PagingResult<User> result) {
		this.total = result.getTotal();
		this.items = result.getItems();
	}
}