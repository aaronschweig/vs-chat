package vs.chat.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.util.Optional;
import java.util.UUID;

import vs.chat.packets.LogoutPacket;
import vs.chat.packets.LogoutSuccessPacket;
import vs.chat.packets.Packet;
import vs.chat.server.warehouse.WarehouseResourceType;

public class ConnectionHandler extends Thread {

	private final Socket client;
	private final ServerContext context;
	private final ObjectOutputStream outputStream;
	private final ObjectInputStream inputStream;

	private Optional<UUID> connectedToUserId = Optional.empty();
	private boolean closeRequested = false;

	public Optional<UUID> getConnectedToUserId() {
		return connectedToUserId;
	}

	public void setConnectedToUserId(UUID connectedToUserId) {
		this.connectedToUserId = Optional.of(connectedToUserId);
	}

	public ConnectionHandler(final Socket client, final ServerContext context, final ObjectOutputStream outputStream,
			final ObjectInputStream inputStream) {
		this.client = client;
		this.context = context;
		this.outputStream = outputStream;
		this.inputStream = inputStream;
	}

	@Override
	public void run() {
		Thread.currentThread().setName("Connection Handler");
		while (!this.context.isCloseRequested().get() && !closeRequested) {
			System.out.println("Handling");
			try {
				var object = inputStream.readObject();
				var packet = (Packet) object;
				if (this.wasPacketIdSeen(packet)) {
					continue;
				}
				this.handlePacket(packet);
				this.storePacketId(packet);
			} catch (IOException | ClassNotFoundException e) {
				break;
			}
		}
		this.close();
	}

	private boolean wasPacketIdSeen(final Packet packet) {
		return this.context.getWarehouse().get(WarehouseResourceType.PACKETS).containsKey(packet.getId());
	}

	private void storePacketId(final Packet packet) {
		this.context.getWarehouse().get(WarehouseResourceType.PACKETS).put(packet.getId(), packet);
	}

	private void handlePacket(final Packet packet) throws IOException {
		if (packet instanceof LogoutPacket) {
			this.pushTo(new LogoutSuccessPacket());
			this.closeRequested = true;
			return;
		}
		for (var listener : this.context.getListeners()) {
			try {
				var methods = listener.getClass().getDeclaredMethods();
				for (var method : methods) {
					if (method.getName().equals("next") && !method.isSynthetic()) {
						var packetType = method.getParameters()[0];
						if (packetType.getType().isAssignableFrom(packet.getClass())) {
							var retu = (Packet) method.invoke(listener, packet, this.context, this);
							this.pushTo(retu);
						}
					}
				}

			} catch (SecurityException | IllegalArgumentException | IllegalAccessException
					| InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		this.context.getWarehouse().print();
	}

	public synchronized void pushTo(final Packet packet) {
		try {
			if (null == packet)
				return;
			System.out.println("pushing" + packet);
			outputStream.writeObject(packet);
			outputStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
			this.close();
		}

	}

	private void close() {
		System.out.println("closing");
		this.context.getConnections().remove(this);
		try {
			this.client.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

}
