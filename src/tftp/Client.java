package tftp;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client extends Stoppable{

	DatagramPacket sendPacket, receivePacket;
	DatagramSocket sendReceiveSocket;

	int serverPort = 69;

	public static final int READ = 1; 
	public static final int WRITE = 2;

	// validation client side
	public Client()
	{
		try {
			sendReceiveSocket = new DatagramSocket();
		} 
		catch (SocketException se) {   // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
		}
	}

	/*
	 * sendAndReceive takes an opcode as an argument and sends a request of that type
	 * the filename is
	 * Once it has sent the request it waits for the server to respond.
	 */
	public void sendAndReceive(int opcode) {
		timeout = true;
		String format = "ocTet";
		byte msg[] = Message.formatRequest(filename, format, opcode);
		try {
			super.sendPacket = new DatagramPacket(msg, msg.length,
					InetAddress.getLocalHost(), serverPort); //SERVERPORT TO SUBMIT  CHANGED
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}
		Message.printOutgoing(super.sendPacket, "Client",verbose);

		// Send the datagram packet to the server via the send/receive socket. 
		
		int timeoutCounter = 0;
		byte data[] = new byte[516];
		super.receivePacket = new DatagramPacket(data, data.length);
		if (!shutdown) {
			// Process the received datagram.
			if (opcode==WRITE) {
				try {
					byte[] resp = new byte[500];
					super.receivePacket = new DatagramPacket(resp,500);
					while (timeout) {
						try {
							sendReceiveSocket.send(super.sendPacket);
						} catch (IOException e) {
							e.printStackTrace();
							System.exit(1);
						}
						timeout = false;
						try {
							sendReceiveSocket.setSoTimeout(1500);
							sendReceiveSocket.receive(super.receivePacket);
							if (!Message.validate(super.receivePacket, false)) {
								return;
							}
							Message.printIncoming(super.receivePacket, "Client", verbose);
						} catch (SocketTimeoutException e) {
							
							timeoutCounter++;
							timeout = true;
							if (shutdown||timeoutCounter==5) {
								System.exit(0);
							}
							System.out.println("Timed out, retransmitting.  ");
							Message.printOutgoing(super.sendPacket,"Retransmit:",verbose);
						} catch (MalformedPacketException e) {
							Message.printIncoming(super.receivePacket, "ERROR", verbose);
							super.sendPacket = createErrorPacket(e.getMessage(),4,super.receivePacket.getPort());
							sendReceiveSocket.send(super.sendPacket);
							Message.printOutgoing(super.sendPacket, "Error", verbose);
							return;
						}
					}
					port = super.receivePacket.getPort();
					BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename));
					if (super.receivePacket.getData()[1]==4&&Message.parseBlock(super.receivePacket.getData())==0) {
						read(in,sendReceiveSocket,port);
					}
					else if (super.receivePacket.getData()[1]==4){
						super.sendPacket = createErrorPacket("Invalid block number.",4,super.receivePacket.getPort());
						sendReceiveSocket.send(super.sendPacket);
						Message.printOutgoing(super.sendPacket, "Error", verbose);
					}
					else if (super.receivePacket.getData()[1]==5) {
						
					}
					else{
						super.sendPacket = createErrorPacket("Invalid opcode.",4,super.receivePacket.getPort());
						sendReceiveSocket.send(super.sendPacket);
						Message.printOutgoing(super.sendPacket, "Error", verbose);
					}
					in.close();

				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			else if (opcode==READ) {
				try {
					sendReceiveSocket.send(super.sendPacket);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				try {
					System.out.println("Creating file output.");
					BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(filename));
					write(out,sendReceiveSocket);
					System.out.println("how");
					out.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public static void main(String args[])
	{
		
		Client c = new Client();
		String x;
		Scanner sc = new Scanner(System.in);
		System.out.println("(R)ead, (w)rite, toggle (v)erbose, toggle (t)est, or (q)uit?");
		System.out.println("Default options are verbose mode on, test mode off.");
		while(sc.hasNext()) { //TODO loop for invalid file (both r/w?) slash just loop in general
			x = sc.next();
			if (x.contains("R")||x.contains("r")) {
				System.out.println("Please enter a filename.");
				c.filename = sc.next();
				sc.reset();
				new Message(c).start();
				c.sendAndReceive(READ);
				System.exit(0);
			}
			else if (x.contains("w")||x.contains("W")) {
				System.out.println("Please enter a filename.");
				c.filename = sc.next();
				sc.reset();
				new Message(c).start();
				c.sendAndReceive(WRITE);
				System.exit(0);
			}
			else if (x.contains("v")||x.contains("V")) {
				c.verbose = !c.verbose;
				System.out.println("Verbose = " + c.verbose);
			}
			else if (x.contains("t")||x.contains("T")) {
				if (c.serverPort==23) {
					c.serverPort = 69;
					System.out.println("Test mode off.");
				}
				else {
					c.serverPort = 23;
					System.out.println("Test mode on.");
				}
			}
			else if (x.contains("q")||x.contains("Q")) {
				c.sendReceiveSocket.close();
				System.exit(0);
			}
			else {
				sc.reset(); //clear scanner
			}
		}
		sc.close();
		
	}
}
