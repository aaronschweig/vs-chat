package vs.chat.packets;

import java.util.Set;
import java.util.UUID;

import vs.chat.entities.Message;

public class GetMessagesResponsePacket implements Packet{

	public UUID chatId;
	public Set<Message> messages;
}
