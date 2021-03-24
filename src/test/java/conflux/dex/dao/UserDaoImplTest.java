package conflux.dex.dao;

import conflux.dex.model.User;
import org.junit.Test;

public class UserDaoImplTest {

    @Test(expected = NullPointerException.class)
    public void testAddUser() {
        UserDaoImpl userDao = new UserDaoImpl();
        userDao.addUser(new User());
    }

    @Test(expected = NullPointerException.class)
    public void testGetUser() {
        UserDaoImpl userDao = new UserDaoImpl();
        userDao.getUser(1L);
    }

    @Test(expected = NullPointerException.class)
    public void testGetUserByName() {
        UserDaoImpl userDao = new UserDaoImpl();
        userDao.getUserByName("alice");
    }
}
