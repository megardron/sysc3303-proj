package assign1;

import java.io.*;
import java.net.*;

public class Stoppable extends Thread {
	protected boolean shutdown;
	protected boolean timeout;
	DatagramPacket sendPacket,receivePacket;
	int port;
	String filename;
	boolean verbose = true;

	public void setShutdown() {
		shutdown = true;
	} 

	/* TODO
	 * 	-print ack0
	 * 	-check port and host on packet
	 * 		-error if unexpected
	 * 		-except intermediate complicates things there?
	 * 	-timeout/retransmit
	 * 	-timing diagram
	 * 	-update class diagram
	 * 	-fix ucm
	 * 	-cleanup intermediate
	 * 	-remove extra print statements
	 * 	-package name
	 * 	-fix the client menu?
	 * 	-indicate when verbosity changes
	 * 	-class descriptions in readme
	 * 	-locations of files (specify vs default) etc et c e t   c 
	 * 		-i mean it kind of works, but.....
	 * 	-ALSO, i forget
	 * 	-set name for printing in constructors
	 */

	//Working:
	//	-Lose WRQ
	//	-Lose RRQ
	//	-Duplicate D/A
	//	-Delay D on R
	//	-Normal Read
	//	-Normal Write (maybe duplicate ack?)
	
	//Not working:
	//	-Lose D1 acts like duplicate RRQ/WRQ
	//	-Losing last A
	//	-Losing A on RRQ may work?
	//	-On write request, losing data
	//	-
	//WRQ



	/*
	 * write takes a file outputstream and a communication socket as arguments
	 * it waits for data on the socket and writes it to the file
	 */
	public void write(BufferedOutputStream out, DatagramSocket sendReceiveSocket) throws IOException {
		byte[] resp = new byte[4];
		resp[0] = 0;
		resp[1] = 4;
		int timeoutCounter = 0;
		port = 0;
		int expected = 1;
		int actual;
		byte[] data = new byte[516];
		try {
			do {
				timeout = true;
				receivePacket = new DatagramPacket(data,516);
				//validate and save after we get it
				while (timeout) {
					try {
						System.out.println("so fast tho");
						sendReceiveSocket.setSoTimeout(6000); //this timeout kinda breaks things
						//should probably extract the initial ack
						sendReceiveSocket.receive(receivePacket);
						System.out.println("we have the goods");
						timeout = false;
						timeoutCounter = 0;
						if (!Message.validate(receivePacket)) {
							System.out.print("Invalid packet.");
							Message.printIncoming(receivePacket, "ERROR", true);
							System.exit(0);
						}
					} catch (SocketTimeoutException e) {
						timeout = true;
						timeoutCounter++;
						System.out.println("Timed out.");
						sendReceiveSocket.send(sendPacket);
						if (shutdown) { //interrupt?
							return;
						}
					}
				}				
				System.out.println(timeoutCounter);

				if ((port!=0)&&(receivePacket.getPort()!=port)) {
					System.out.println("ERROR, WRONG PORT");
					System.exit(2);
				}
				port = receivePacket.getPort();
				Message.printIncoming(receivePacket, "Write",verbose);
				actual = Message.parseBlock(data);
				if (expected==actual) {
					System.out.println("Writing to file.");
					out.write(data,4,receivePacket.getLength()-4);
					expected++;
				}
				System.arraycopy(receivePacket.getData(), 2, resp, 2, 2);
				sendPacket = new DatagramPacket(resp, resp.length,
						receivePacket.getAddress(), receivePacket.getPort());
				sendReceiveSocket.send(sendPacket);
				System.out.println("write:  " + (expected-1) +"      " + actual);
				Message.printOutgoing(sendPacket, this.toString(),verbose);
			} while (receivePacket.getLength()==516);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/*
	 * read takes an input stream, a socket and a port as arguments
	 * reads data from the file in 512 byte chunks and sends them over the socket to the port
	 * on localhost
	 */
	public void read(BufferedInputStream in, DatagramSocket sendReceiveSocket, int port) throws IOException {
		int n;
		int timeoutCounter = 0;
		byte block1 = 0;
		byte block2 = 0;
		int newPort = port;
		byte[] data = new byte[512];
		byte[] resp = new byte[4];
		try {
			boolean empty = true;
			sendPacket = new DatagramPacket(resp,4);
			while (((n = in.read(data)) != -1)) {
				timeout = true;
				System.out.println(timeoutCounter);
				if ((int) block2 ==-1)
					block1++;
				block2++;
				empty = false;
				byte[] message = new byte[n+4];
				message[0] = 0;
				message[1] = 3;
				message[2] = block1;
				message[3] = block2;
				for (int i = 0;i<n;i++) {
					message[i+4] = data[i];
				}
				sendPacket = new DatagramPacket(message,n+4,InetAddress.getLocalHost(),port);
				Message.printOutgoing(sendPacket, "Read", verbose);
				while (timeout) {
					sendReceiveSocket.send(sendPacket);
					receivePacket = new DatagramPacket(resp,4);
					//retransmit here???
					timeout = false;

					
					try {
						sendReceiveSocket.setSoTimeout(1500);
						sendReceiveSocket.receive(receivePacket);
						Message.printIncoming(receivePacket,"Read",verbose);
						timeoutCounter = 0;
						if (receivePacket.getPort()!=port) {
							System.out.println("ERROR, WRONG PORT");
							System.exit(2);
						}
						if (!Message.validate(receivePacket)) {
							System.out.print("Invalid packet.");
							Message.printIncoming(receivePacket, "ERROR", true);
							System.exit(0);
						}
					} catch (SocketTimeoutException e) {
						timeout = true;
						timeoutCounter++;
						if (shutdown) {
							System.exit(0);
						}
						else {
							System.out.println("timeout: " + Message.parseBlock(sendPacket.getData()));
							if (receivePacket.getLength()>4) {
								Message.printIncoming(receivePacket, "But why", true);
							}
							sendReceiveSocket.send(sendPacket);
						} //issue with not ending?
					}
				}
				System.out.println("read:  sent:" + Message.parseBlock(sendPacket.getData()) +"      received:" + Message.parseBlock(receivePacket.getData()));
				Message.printIncoming(receivePacket, "Read", verbose);
				System.out.println(Message.parseBlock(sendPacket.getData())!=Message.parseBlock(receivePacket.getData()));
				while ((Message.parseBlock(sendPacket.getData())!=Message.parseBlock(receivePacket.getData()))||timeout) {
					try {
						sendReceiveSocket.receive(receivePacket);
						Message.printIncoming(receivePacket,"Final packet",verbose);
						timeout = false;
					}
					catch (SocketTimeoutException e) {
						timeout = true;
					}
					System.out.println("had not got the right ack yet: " +  Message.parseBlock(receivePacket.getData()));
				}

			}
			if ((n==-1&&sendPacket.getLength()==516)||empty) {
				if ((int) block2 ==-1)
					block1++;
				block2++;
				resp[0] = 0;
				resp[1] = 3;
				resp[2] = block1;
				resp[3] = block2;
				sendPacket = new DatagramPacket(resp,4,InetAddress.getLocalHost(),port);
				sendReceiveSocket.send(sendPacket);
				Message.printOutgoing(sendPacket, "Read", verbose);
				try {
					sendReceiveSocket.receive(sendPacket);
				} catch (SocketTimeoutException e) {
					System.out.println(":/");
					System.exit(2);
				}
				Message.printIncoming(sendPacket, "Read123", verbose);
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
