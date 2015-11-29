package node;

import java.util.Scanner;

import message.NotifyNewPredecessor;
import message.NotifyNewSuccessor;
import message.PeerStartAck;
import message.PeerStopRequest;
import utility.Configuration;

public class Peer_input implements Runnable{

	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		attachShutDownHook();
		System.out.println("The System has stablized, ready to send files!");
		while(true)
		{
			String content = null;
			Scanner scanner = new Scanner(System.in);
			content = scanner.nextLine();
			if(content.equals("EXIT"))
			{
				System.exit(0);
			}
		}
	}
	
	public void attachShutDownHook()
	{
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				//On shutting down the peer. Add exit code here!!!!!!!!
				System.out.println("Shutting down the peer " + Peer.ID);
				notifyNeighbors();
				notifyServer();
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}
	
	
		
	private void notifyNeighbors()
	{
		//Notify the successor its predecessor should be my current predecessor
		NotifyNewPredecessor notifyPredecessor = new NotifyNewPredecessor(Peer.ID, Peer.successor, Peer.predecessor);
		Peer_send nPredecessor_thread = new Peer_send(notifyPredecessor, Configuration.peerIP, 6000 + notifyPredecessor.to);
		new Thread(nPredecessor_thread).start();
			
			
		//Notify the predecessor is successor should be my current successor
		NotifyNewSuccessor notifySuccessor = new NotifyNewSuccessor(Peer.ID, Peer.predecessor, Peer.successor);
		Peer_send nSuccessor_thread = new Peer_send(notifySuccessor, Configuration.peerIP, 6000 + notifySuccessor.to);
		new Thread(nSuccessor_thread).start();
	}

	
	private void notifyServer()
	{
		PeerStopRequest stop_request = new PeerStopRequest(Peer.ID, 0);
		Peer_send send_stop_request = new Peer_send(stop_request, Peer.serverIP, Peer.serverPort);
		new Thread(send_stop_request).start();
	}
}