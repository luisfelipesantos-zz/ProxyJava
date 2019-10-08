import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class Proxy implements Runnable{

	

	public static void main(String[] args) {
		System.out.println("Defina a porta na qual a proxy ira redirecionar as requisicoes: ");
		Scanner ler = new Scanner(System.in);
		int porta = ler.nextInt();
		Proxy myProxy = new Proxy(porta);
		myProxy.listen();	
	}


	public ServerSocket serverSocket;

	private volatile boolean itsRunning = true;

	//static HashMap<String, File> cache;

	static HashMap<String, String> blockedSites;

	static ArrayList<Thread> servicingThreads;


	public Proxy(int port) {
		
		// Load in hash map containing previously cached sites and blocked Sites
		//cache = new HashMap<>();
		blockedSites = new HashMap<>();

		// Create array list to hold servicing threads
		servicingThreads = new ArrayList<>();

		// Start dynamic manager on a separate thread.
		new Thread(this).start();	// Starts overriden run() method at bottom

		try{
			File blockedSitesTxtFile = new File("blockedSites.txt");
			if(!blockedSitesTxtFile.exists()){
				System.out.println("Nenhum site bloqueado encontrado - Criando novo arquivo");
				blockedSitesTxtFile.createNewFile();
			} else {
				FileInputStream fileInputStream = new FileInputStream(blockedSitesTxtFile);
				ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
				blockedSites = (HashMap<String, String>)objectInputStream.readObject();
				fileInputStream.close();
				objectInputStream.close();
			}
		} catch (IOException e) {
			System.out.println("Erro ao carregar arquivo de sites bloqueados");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		

		try {
			serverSocket = new ServerSocket(port);
			System.out.println("Esperando por cliente na porta " + serverSocket.getLocalPort() + "...");
			itsRunning = true;
		} 

		catch (SocketException se) {
			se.printStackTrace();
		}
		catch (SocketTimeoutException ste) {
			ste.printStackTrace();
		} 
		catch (IOException io) {
			System.out.println("Exceção E/S ao se conectar com cliente");
		}
	}


	public void listen(){

		while(itsRunning){
			try {
				Socket socket = serverSocket.accept();
				
				Thread thread = new Thread(new ManipuladorRequests(socket));
				
				servicingThreads.add(thread);
				
				thread.start();	
			} catch (SocketException e) {
				System.out.println("Server closed");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void closeServer(){
		System.out.println("\nClosing Server..");
		itsRunning = false;
		try{

			FileOutputStream fileOutputStream2 = new FileOutputStream("blockedSites.txt");
			ObjectOutputStream objectOutputStream2 = new ObjectOutputStream(fileOutputStream2);
			objectOutputStream2.writeObject(blockedSites);
			objectOutputStream2.close();
			fileOutputStream2.close();
			System.out.println("Blocked Site list saved");

			try{
				for(Thread thread : servicingThreads){
					if(thread.isAlive()){
						System.out.print("Esperando em "+  thread.getId()+" para fechar...");
						thread.join();
						System.out.println(" fechado");
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			} catch (IOException e) {
				System.out.println("Erro ao salvar sites bloqueados");
				e.printStackTrace();
			}

			try{
				System.out.println("Terminando conexão");
				serverSocket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}


		public static boolean isBlocked (String ip){
			if(blockedSites.get(ip) != null){
				return true;
			} else {
				return false;
			}
		}

		public void run() {
			Scanner scanner = new Scanner(System.in);

			String command;
			while(itsRunning){
				System.out.println("Digite um IP para bloquear, \"b\" para ver os IP's bloqueados, or \"c\" para fechar o servidor.");
				command = scanner.nextLine();
				if(command.toLowerCase().equals("b")){
					System.out.println("\nSites bloqueados atualmente: ");
					for(String key : blockedSites.keySet()){
						System.out.println(key);
					}
					System.out.println();
				} else if(command.equals("c")){
					itsRunning = false;
					closeServer();
				} else {
					blockedSites.put(command, command);
					System.out.println("\n" + command + " bloqueado com sucesso\n");
				}
			}
			scanner.close();
		} 

	}
