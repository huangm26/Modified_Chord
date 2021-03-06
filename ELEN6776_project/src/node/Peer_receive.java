package node;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import message.FindMessage;
import message.InsertMessage;
import message.Message;
import message.NotifyNewPredecessor;
import message.NotifyNewSuccessor;
import message.PeerPredecessorReply;
import message.PeerPredecessorRequest;
import message.PeerStartAck;
import message.PeerStartRequest;
import message.SendFingerTable;
import utility.Configuration;


/*
 * This thread is used to receive all the messages from server and other peers. When different types of messages arrives, it will handle them differently.
 */
public class Peer_receive implements Runnable{

	/*
	 * MY ID, MY IP ADDRESS and MY PORT NUMBER
	 */
	public int ID;
	public String IPAddr;
	public int portNum;
	
	public static ServerSocketChannel receiveSocket;
	
	public Peer_receive(int ID, String IP)
	{
		this.ID = ID;
		this.IPAddr = IP;
		this.portNum = 6000 + ID;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			listen();
		} catch (IOException e) {
		// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private void listen() throws IOException
	{

		try {
			receiveSocket = ServerSocketChannel.open();
			receiveSocket.socket().bind(new InetSocketAddress(InetAddress.getByName(IPAddr), portNum));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		Message message = null;
		ByteBuffer buffer = ByteBuffer.allocate(4000);
		while (true)
		{
			buffer.clear();
			SocketChannel newChannel = receiveSocket.accept();
			newChannel.read(buffer);
			buffer.flip();
			ByteArrayInputStream in = new ByteArrayInputStream(buffer.array());
			ObjectInputStream is = new ObjectInputStream(in);
			try {
				message = (Message) is.readObject();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(message.isPeerStartAck())
			{
				onReceiveStartAck((PeerStartAck)message);
			} 
			else if(message.isNofityNewSuccessor())
			{
				updateSuccessor((NotifyNewSuccessor) message);
			}
			else if(message.isNotifyNewPredecessor())
			{
				updatePredecessor((NotifyNewPredecessor) message);
			}
			else if(message.isSendFingerTabel())
			{
				updateFingerTable((SendFingerTable) message);
			}
			else if(message.isInsertMessage())
			{
				onReceiveInsertMessage((InsertMessage) message);
			}
			else if(message.isFindMessage())
			{
				onReceiveFindMessage((FindMessage) message);
			}
		}		
	}
	
	//On receiving the Start request ACK from server. The peer will be notified about its successor's ID
	private void onReceiveStartAck(PeerStartAck ack)
	{
		if(ack.successful)
		{
			System.out.println("Successful launching peer with ID " + ack.to);
//			System.out.println("The predecessor is " + ack.prev_id);
//			System.out.println("The successor is " + ack.next_id);
			updateNeighbors(ack);
		}
		else
		{
			System.out.println("The ID you entered already exists, please enter another one!");
		}
		
	}
	
	
	//Update the predecessor and successor of the current node, and notify the neighbors if have one
	private void updateNeighbors(PeerStartAck ack)
	{
		//This peer is the first node up in the system.
		if(ack.next_id == -1)
		{
			Peer.predecessor = -1;
			Peer.successor = -1;
		} else
		{
			Peer.predecessor = ack.prev_id;
			Peer.successor = ack.next_id;
			System.out.println("My initial predecessor " + Peer.predecessor);
			System.out.println("My initial successor "+ Peer.successor);
			notifyNeighbors();
		}
		
	}
	
	private void notifyNeighbors()
	{
		//Notify the successor that I am the new predecessor
		NotifyNewPredecessor notifyPredecessor = new NotifyNewPredecessor(ID, Peer.successor, ID);
		sendMessage(notifyPredecessor);
		
		
		//Notify the predecessor that I am the new successor
		NotifyNewSuccessor notifySuccessor = new NotifyNewSuccessor(ID, Peer.predecessor, ID);
		sendMessage(notifySuccessor);
	}
	
	private void updateSuccessor(NotifyNewSuccessor newSuccessor)
	{
		if(newSuccessor.successor != Peer.ID)
			Peer.successor = newSuccessor.successor;
		else
			Peer.successor = -1;
		System.out.println("New successor with ID " + Peer.successor);
	}
	
	private void updatePredecessor(NotifyNewPredecessor newPredecessor)
	{
		if(newPredecessor.predecessor != Peer.ID)
		{
			Peer.predecessor = newPredecessor.predecessor;
			transferMessage(newPredecessor);
		}
		else
		{
			Peer.predecessor = -1;
		}
		System.out.println("New Predecessor with ID " + Peer.predecessor);
	}
	
	private void transferMessage(NotifyNewPredecessor newPredecessor)
	{
		int new_predecessor = newPredecessor.predecessor;
		Iterator<Entry<String, String>> it = Peer.storage.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry pair = (Map.Entry)it.next();
//	        System.out.println(pair.getKey() + " = " + pair.getValue());
	        int key_val = Integer.parseInt((String)pair.getKey()) % 32;
	        if(key_val <= new_predecessor)
	        {
	        	InsertMessage new_message = new InsertMessage(Peer.ID, new_predecessor, (String)pair.getKey(), (String)pair.getValue());
				sendMessage(new_message);
				it.remove(); // avoids a ConcurrentModificationException
	        }
	    }
	}
	
	private void updateFingerTable(SendFingerTable message)
	{
		Peer.fingerTable = message.finger_table;
		System.out.println("Updating finger table ");
		System.out.println(Arrays.toString(Peer.fingerTable));
	}
	
	private void onReceiveInsertMessage(InsertMessage message)
	{
		int key_val = Integer.parseInt(message.key) % 32;
		//The key belongs to me and I am the first peer in the system
		if((Peer.ID < Peer.predecessor) && (key_val <= Peer.ID) && (key_val  >= 0))
		{
			Peer.storage.put(message.key, message.value);
			System.out.println(Peer.storage);
		}
		else if((Peer.ID < Peer.predecessor) && (key_val  > Peer.predecessor))
		{
			Peer.storage.put(message.key, message.value);
			System.out.println(Peer.storage);
		}
		//The key belongs to me and I am not the first peer in the system
		else if((key_val <= Peer.ID) && (key_val  > Peer.predecessor))
		{	
			Peer.storage.put(message.key, message.value);
			System.out.println(Peer.storage);
		}
		//The key belongs to the successor
		else if((key_val > Peer.ID) && (key_val <= Peer.successor))
		{
			InsertMessage new_message = new InsertMessage(Peer.ID, Peer.successor, message.key, message.value);
			sendMessage(new_message);
		}
		//Route based on finger table
		else
		{
			for(int i = 0; i < 5; i++)
			{
				if(Peer.fingerTable[i+1] >= key_val)
				{
					InsertMessage new_message = new InsertMessage(Peer.ID, Peer.fingerTable[i], message.key, message.value);
					sendMessage(new_message);
					break;
				}
				else if((Peer.fingerTable[i] > Peer.fingerTable[i+1]) && (key_val < 32))
				{
					InsertMessage new_message = new InsertMessage(Peer.ID, Peer.fingerTable[i], message.key, message.value);
					sendMessage(new_message);
					break;
				}
				else if(i == 3)
				//Last entry in the finger table is still less than the search key, route to the last entry peer
				{
					InsertMessage new_message = new InsertMessage(Peer.ID, Peer.fingerTable[4], message.key, message.value);
					sendMessage(new_message);
					break;
				}
			}
		}
	}
	
	private void onReceiveFindMessage(FindMessage message)
	{
		String key = message.key;
		int key_val = Integer.parseInt(key) % 32;
		//The key belongs to me and I am the first peer in the system
		if((Peer.ID < Peer.predecessor) && (key_val <= Peer.ID) && (key_val  >= 0))
		{
			if(Peer.storage.containsKey(key))
			{
				String value = Peer.storage.get(key);
				System.out.println("Found the Message with key " + key);
				System.out.println("The value is " + value);
			}
			else 
			{
				System.out.println("There is no message with key " + key);
			}
		}
		else if((Peer.ID < Peer.predecessor)  && (key_val  > Peer.predecessor))
		{
			if(Peer.storage.containsKey(key))
			{
				String value = Peer.storage.get(key);
				System.out.println("Found the Message with key " + key);
				System.out.println("The value is " + value);
			}
			else 
			{
				System.out.println("There is no message with key " + key);
			}
		}
		//The key belongs to me and I am not the first peer in the system
		else if((key_val <= Peer.ID) && (key_val  > Peer.predecessor))
		{
			
			if(Peer.storage.containsKey(key))
			{
				String value = Peer.storage.get(key);
				System.out.println("Found the Message with key " + key);
				System.out.println("The value is " + value);
			}
			else 
			{
				System.out.println("There is no message with key " + key);
			}
		}
		//The key belongs to the successor
		else if((key_val > Peer.ID) && (key_val <= Peer.successor))
		{
			FindMessage new_message = new FindMessage(Peer.ID, Peer.successor, key);
			sendMessage(new_message);
		}
		//Route based on finger table
		else
		{
			for(int i = 0; i < 5; i++)
			{
				if(Peer.fingerTable[i+1] >= key_val)
				{
					FindMessage new_message = new FindMessage(Peer.ID, Peer.fingerTable[i], key);
					sendMessage(new_message);
					break;
				} 
				else if((Peer.fingerTable[i] > Peer.fingerTable[i+1]) && (key_val < 32))
				{
					FindMessage new_message = new FindMessage(Peer.ID, Peer.fingerTable[i], key);
					sendMessage(new_message);
					break;
				}
				else if(i == 3)
				//Last entry in the finger table is still less than the search key, route to the last entry peer
				{
					FindMessage new_message = new FindMessage(Peer.ID, Peer.fingerTable[4], key);
					sendMessage(new_message);
					break;
				}
			}
		}
	}
	
	private void sendMessage(Message message)
	{
		Peer_send new_message_thread = new Peer_send(message, Configuration.peerIP, 6000 + message.to);
		new Thread(new_message_thread).start();
	}
}
