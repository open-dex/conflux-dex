package conflux.dex.service.statistics;

import org.junit.Assert;
import org.junit.Test;

public class RetentionUsersTest {
	
	@Test
	public void testUpdateFirstDay() {
		RetentionUsers users = new RetentionUsers(3);
		
		// new active user
		users.update(1);
		Assert.assertEquals(0, users.get());
	}
	
	@Test
	public void testUpdateWithRetention() {
		RetentionUsers users = new RetentionUsers(3);
		
		// new active user 1
		users.update(1);
		users.seal();
		
		// new active user 2
		users.update(2);
		Assert.assertEquals(0, users.get());
		
		// retention user
		users.update(1);
		Assert.assertEquals(1, users.get());
		
		// retention one more time
		users.update(1);
		Assert.assertEquals(1, users.get());
		
		users.seal();
		Assert.assertEquals(0, users.get());
	}

}
