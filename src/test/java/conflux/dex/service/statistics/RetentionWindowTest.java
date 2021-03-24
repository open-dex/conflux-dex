package conflux.dex.service.statistics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.Assert;
import org.junit.Test;

public class RetentionWindowTest {
	
	private void addSlot(RetentionWindow window, long... users) {
		ConcurrentMap<Long, Boolean> slot = new ConcurrentHashMap<Long, Boolean>();
		
		for (long id : users) {
			slot.putIfAbsent(id, true);
		}
		
		window.move(slot);
	}
	
	@Test
	public void testRemoveUser() {
		RetentionWindow window = new RetentionWindow(3);
		
		this.addSlot(window, 1, 2);
		this.addSlot(window, 3, 4);
		
		// not exists
		Assert.assertFalse(window.removeUser(5));
		
		// exists
		Assert.assertTrue(window.removeUser(3));
		
		// already removed
		Assert.assertFalse(window.removeUser(3));
	}
	
	@Test
	public void testMoveNotFull() {
		RetentionWindow window = new RetentionWindow(3);
		
		this.addSlot(window, 1, 2);
		this.addSlot(window, 3, 4);
		
		Assert.assertTrue(window.removeUser(1));
	}
	
	@Test
	public void testMoveFull() {
		RetentionWindow window = new RetentionWindow(3);
		
		this.addSlot(window, 1, 2);
		this.addSlot(window, 3, 4);
		this.addSlot(window, 5, 6);
		
		// slot (1, 2) will be removed
		this.addSlot(window, 7, 8);
		
		Assert.assertFalse(window.removeUser(1));
	}
	
	@Test
	public void testMoveUpdate() {
		RetentionWindow window = new RetentionWindow(3);
		
		this.addSlot(window, 1, 2);
		// 1 will be updated to the new slot, and previous slot changed to (2)
		this.addSlot(window, 1);
		this.addSlot(window, 3);
		// oldest slot (2) will be removed
		this.addSlot(window, 4);
		
		Assert.assertTrue(window.removeUser(1));
		Assert.assertFalse(window.removeUser(2));
	}

}
