import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import javax.imageio.ImageIO;

public class RequestHandler implements Runnable {

	private SocketChannel socketChannel;

	public RequestHandler(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
		// this.serverSocketChannel = socketChannel;
		System.out.println("RequestHandler initialized");
	}

	public static int getLastPush(String dir) {
		// Sistema schifoso, da cambiare
		return new File("./" + dir).listFiles().length + 1;
	}

	public void run() {

		LoadConfig config = null;
		try {
			config = new LoadConfig();
		} catch (IOException e) {
			e.printStackTrace();
		}

		String type = null;

		try {

			socketChannel.socket().setSoTimeout(10000);
			MainServer.log("Client connected from: " + socketChannel);

			// Prendere immagine
			DataInputStream dis = new DataInputStream(socketChannel.socket().getInputStream());

			// Leggo string
			BufferedReader stringIn = new BufferedReader(new InputStreamReader(socketChannel.socket().getInputStream()));

			// Invio al client
			DataOutputStream dos = new DataOutputStream(socketChannel.socket().getOutputStream());

			// leggo in ricezione
			MainServer.log("Attendo auth");
			String auth = stringIn.readLine();

			// check auth
			MainServer.log("Auth ricevuto: " + auth);

			String pass = config.getPass();
			if (pass.equals(auth)) {
				dos.writeBytes("OK\n");
				System.out.println("Client Authenticated");

				// Aspetto e leggo il type
				type = stringIn.readLine();
				System.out.println("fileType: " + type);

				// Informo il client della ricezione e così parte l'upload
				dos.writeBytes(type + "\n");

				Integer i = getLastPush(config.getFolder());

				String fileName = i.toString();
				System.out.println("fileName: " + fileName);

				switch (type) {

				case "img":

					// transfer image
					int len = dis.readInt();
					System.out.println("Transfer started.");
					byte[] data = new byte[len];
					dis.readFully(data);
					System.out.println("Transfer ended.");

					File toWrite = new File(config.getFolder() + "/" + fileName + ".png");

					ImageIO.write(ImageIO.read(new ByteArrayInputStream(data)), "png", toWrite);

					dos.writeBytes("http://" + config.getDomain() + "/" + toWrite.getName());

					break;
				case "file":

					// transfer file
					System.out.println("Transfer started.");
					readFileFromSocket(config.getFolder() + "/" + fileName + ".zip");
					System.out.println("Transfer ended.");

					System.out.println("Sending link...");
					dos.writeBytes("http://" + config.getDomain() + "/" + fileName + ".zip");

					break;
				default:

				}

				i++;

				System.out.println("Chiudo");
				dos.close();
				dis.close();
				stringIn.close();
			} else {
				dos.writeBytes("Invalid Id or Password");
				System.out.println("Invalid Id or Password");
				dos.close();
				dis.close();
				stringIn.close();
			}

			socketChannel.close();

		} catch (Exception exc) {
			exc.printStackTrace();
		}
		System.out.println("----------");
	}

	public SocketChannel createServerSocketChannel(int filePort) {

		ServerSocketChannel serverSocketChannel = null;
		SocketChannel socketChannel = null;
		try {

			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.socket().bind(new InetSocketAddress(filePort));
			socketChannel = serverSocketChannel.accept();
			System.out.println("SocketChannel connection established with: " + socketChannel.getRemoteAddress());

		} catch (IOException e) {
			e.printStackTrace();
		}

		return socketChannel;
	}

	public void readFileFromSocket(String fileName) {
		RandomAccessFile aFile = null;
		try {
			aFile = new RandomAccessFile(fileName, "rw");
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			FileChannel fileChannel = aFile.getChannel();
			while (socketChannel.read(buffer) > 0) {
				buffer.flip();
				fileChannel.write(buffer);
				buffer.clear();
			}
			Thread.sleep(1000);
			fileChannel.close();
			System.out.println("End of file reached, closing channel");
			// socketChannel.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

}
