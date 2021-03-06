package tftp;

import java.net.*;
import java.util.Scanner;
import java.util.regex.Pattern;




public class Message extends Thread{
	Scanner sc;
	Stoppable s;
	public static final String[] ops = {"","RRQ","WRQ","DATA","ACK"};

	public Message(Stoppable s) {
		this.s = s;
		sc = new Scanner(System.in);
	}

	public void run() {
		while (true) {
			if (sc.hasNext()) {
				String x = sc.next();
				if (x.contains("q")||x.contains("Q")){
					s.setShutdown();
					return;
				}
				else if (x.contains("v")||x.contains("V")) {
					if (s.verbose) {
						System.out.println("Verbose mode turned off.");
					}
					else {
						System.out.println("Verbose mode turned on.");
					}
					s.verbose = !s.verbose;
				}
				else {
					sc.reset();
				}
			}
		}
	}

	//this probs matches invalid strings but so does the sample...
	public static boolean validate(DatagramPacket receivePacket) {
		String data = new String(receivePacket.getData(),0,receivePacket.getLength());
		return Pattern.matches("^\0(((\001|\002).+\0(([oO][cC][tT][eE][tT])|([nN][eE][tT][aA][sS][cC][iI][iI]))\0)|(\004(.|\012|\015)(.|\012|\015))|(\003(.|\012|\015){2,}))$", data);
	}
	
	public static void main(String[] args) {
		byte[] data = {0,3,127,33};
		
		DatagramPacket d = new DatagramPacket(data, 4);
		System.out.println(validate(d));
	}

	public static int parseBlock(byte[] data) {
		int x = (int) data[2];
		int y = (int) data[3];
		if (x<0) {
			x = 256+x;
		}
		if (y<0) {
			y = 256+y;
		}
		return 256*x+y;
	}

	public static String parseFilename(String data) {
		return data.split("\0")[1].substring(1);
	}

	/*
	 * formatRequest takes a filename and a format and an opcode (which corresponds to read or write)
	 * and formats them into a correctly formatted request
	 */
	public static byte[] formatRequest(String filename, String format, int opcode) {
		int l = filename.length();
		byte[] msg = filename.getBytes();
		byte [] result;
		result = new byte[l+4+format.length()];
		result[0] = 0;
		result[1] = (byte) opcode;
		for (int i = 0;i<l;i++) {
			result[i+2] = msg[i];
		}
		result[l+2] = 0;
		for (int i = 0;i<format.length();i++) {
			result[l+3+i] = format.getBytes()[i];
		}
		result[l+3+format.length()] = 0;
		return result;
	}

	//prints relevent information about an incoming packet
	public static void printIncoming(DatagramPacket p, String name, boolean verbose) {
		if (verbose) {
			int opcode = p.getData()[1];
			System.out.println(name + ": packet received.");
			System.out.println("From host: " + p.getAddress());
			System.out.println("Host port: " + p.getPort());
			int len = p.getLength();
			System.out.println("Length: " + len);
			System.out.println("Packet type: "+ ops[opcode]);
			if (opcode<3) {
				System.out.println("Filename: "+ parseFilename(new String (p.getData(), 0, len)));
			}
			else {
				System.out.println("Block number " + parseBlock(p.getData()));

			}
			if (opcode==3) {
				System.out.println("Number of bytes: "+ (len-4));
			}
			System.out.println();
		}
	}

	//prints information about an outgoing packet
	public static void printOutgoing(DatagramPacket p, String name, boolean verbose) {
		if (verbose) {
			int opcode = p.getData()[1];
			System.out.println(name + ": packet sent.");
			System.out.println("To host: " + p.getAddress());
			System.out.println("Host port: " + p.getPort());
			int len = p.getLength();
			System.out.println("Length: " + len);
			System.out.println("Packet type: "+ ops[opcode]);
			if (opcode<3) {
				System.out.println("Filename: "+ parseFilename(new String (p.getData(), 0, len)));
			}
			else {
				System.out.println("Block number " + parseBlock(p.getData()));

			}
			if (opcode==3) {
				System.out.println("Number of bytes: "+ (len-4));
			}
			System.out.println();
		}
	}
}