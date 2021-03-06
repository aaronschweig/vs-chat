package vs.chat.server.listener;

import java.io.IOException;

import vs.chat.packets.Packet;
import vs.chat.server.ConnectionHandler;
import vs.chat.server.ServerContext;

public interface Listener<T extends Packet, R extends Packet> {

	public R next(final T packet, final ServerContext context, final ConnectionHandler handler) throws IOException;

	public default void close() throws IOException {

	}
}
