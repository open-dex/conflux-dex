package conflux.dex.common.channel;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import conflux.dex.common.BusinessException;

public interface Channel<T> extends Sender<T>, Receiver<T> {
	
	static <T> Channel<T> create() {
		return new DefaultChannel<T>();
	}

}

class DefaultChannel<T> implements Channel<T> {
	
	private BlockingDeque<T> queue = new LinkedBlockingDeque<T>();

	@Override
	public T receive() throws InterruptedException {
		return this.queue.takeFirst();
	}

	@Override
	public void send(T data) throws BusinessException {
		try {
			this.queue.putLast(data);
		} catch (InterruptedException e) {
			throw BusinessException.internalError("failed to send data in channel", e);
		}
	}
	
}