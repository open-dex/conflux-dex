package conflux.dex.common;

public interface Handler<T> {
	void handle(T data);
}
