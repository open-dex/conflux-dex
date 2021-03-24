package conflux.dex.common;

import java.util.LinkedList;
import java.util.List;

public class Event<T> {
	
	private List<Handler<T>> handlers = new LinkedList<Handler<T>>();
	
	public void addHandler(Handler<T> handler) {
		if (handler != null) {
			this.handlers.add(handler);
		}
	}
	
	public void fire(T data) {
		for (Handler<T> handler : this.handlers) {
			handler.handle(data);
		}
	}

}
