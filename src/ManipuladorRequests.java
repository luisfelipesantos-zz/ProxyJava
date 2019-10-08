import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Date;

import javax.imageio.ImageIO;

public class ManipuladorRequests implements Runnable {

	
	Socket clientSocket;
        
	Date data;
        
	FileWriter fw;
        
	String requestString;


	/**
	 * Lê os dados e manda para a proxy
	 */
	BufferedReader proxyClienteBr;

	/**
	 * Envia os dados da proxy para o cliente
	 */
	BufferedWriter proxyClienteBw;
	

	/**
	 * Transmite os dados do cliente para o servidor usando https
	 */
	private Thread httpsClientServer;

	private String ipLocal;
	private String ipServer;	

	public ManipuladorRequests(Socket clientSocket){
		this.clientSocket = clientSocket;
		String str = String.valueOf(clientSocket.getLocalAddress().getHostName());
			

		try{
			ipLocal = String.valueOf(InetAddress.getLocalHost().getHostAddress());
			proxyClienteBr = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			proxyClienteBw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}


	
	/**
	 * Esse método trata a urlString e direciona ela para o método correspondente de acordo com o tipo de requisição 
	 */
	@Override
	public void run() {

		try{
			requestString = proxyClienteBr.readLine();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Erro ao ler requisição do cliente");
			return;
		}

		String request = requestString.substring(0,requestString.indexOf(' '));

		String urlString = requestString.substring(requestString.indexOf(' ')+1);

		urlString = urlString.substring(0, urlString.indexOf(' '));

		if(!urlString.substring(0,4).equals("http")){
			String temp = "http://";
			urlString = temp + urlString;
		}	
		

		if(request.equals("CONNECT")){
			System.out.println("HTTPS Request for : " + urlString + "\n");
			handleHTTPSRequest(urlString);
		} 

		else{
				System.out.println("HTTP GET for : " + urlString + "\n");
				sendArchiveToClient(urlString);
			
		}
	} 


	private void sendArchiveToClient(String urlString){

		try{
			
			int fileExtensionIndex = urlString.lastIndexOf(".");
			String fileExtension;

			fileExtension = urlString.substring(fileExtensionIndex, urlString.length());

			String fileName = urlString.substring(0,fileExtensionIndex);

			fileName = fileName.substring(fileName.indexOf('.')+1);

			fileName = fileName.replace("/", "__");

			fileName = fileName.replace('.','_');
			
			if(fileExtension.contains("/")){
				fileExtension = fileExtension.replace("/", "__");
				fileExtension = fileExtension.replace('.','_');
				fileExtension += ".html";
			}
		
			fileName = fileName + fileExtension;



			File fileToCache = null;



			if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
					fileExtension.contains(".jpeg") || fileExtension.contains(".gif")){
				
				URL remoteURL = new URL(urlString);
				BufferedImage image = ImageIO.read(remoteURL);

				if(image != null) {
					ImageIO.write(image, fileExtension.substring(1), fileToCache);

					String line = "HTTP/1.0 200 OK\n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyClienteBw.write(line);
					proxyClienteBw.flush();

					ImageIO.write(image, fileExtension.substring(1), clientSocket.getOutputStream());

				} else {
					System.out.println("Sending 404 to client as image wasn't received from server"
							+ fileName);
					String erroNotFound = "HTTP/1.0 404 NOT FOUND\n" +
							"Proxy-agent: ProxyServer/1.0\n" +
							"\r\n";
					proxyClienteBw.write(erroNotFound);
					proxyClienteBw.flush();
					return;
				}
			} 

			else {
								
				URL remoteURL = new URL(urlString);

				//Criando conexão com o servidor remoto
				HttpURLConnection proxyToServerCon = (HttpURLConnection)remoteURL.openConnection();
				proxyToServerCon.setRequestProperty("Content-Type", 
						"application/x-www-form-urlencoded");
				proxyToServerCon.setRequestProperty("Content-Language", "en-US");  
				proxyToServerCon.setUseCaches(false);
				proxyToServerCon.setDoOutput(true);
			
				BufferedReader proxyServerBr = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));
				
				String line = "HTTP/1.0 200 OK\n" +
						"Proxy-agent: ProxyServer/1.0\n" +
						"\r\n";
				proxyClienteBw.write(line);
				
				while((line = proxyServerBr.readLine()) != null){
					proxyClienteBw.write(line);
				}
				
				proxyClienteBw.flush();

				if(proxyServerBr != null){
					proxyServerBr.close();
				}
			}

			if(proxyClienteBw != null){
				proxyClienteBw.close();
			}
		} 

		catch (Exception e){
			//e.printStackTrace();
		}
	}

	
	/**
	 * Manipula requisições entre o servidor remoto e o cliente
	 */
	private void handleHTTPSRequest(String urlString){
		String url = urlString.substring(7);
		String pieces[] = url.split(":");
		url = pieces[0];
		int port  = Integer.valueOf(pieces[1]);
		


		try{
			// Get actual IP associated with this URL through DNS
			InetAddress address = InetAddress.getByName(url);

			data = new Date();
                        
			File logs = new File("logs.txt");
			if(!logs.exists()){
				System.out.println("logs.txt not found - creating a new file");
				logs.createNewFile();
			} else {
				fw = new FileWriter(logs, true);
				fw.write(data+":\r\n"+InetAddress.getLocalHost().getHostAddress()+": "+requestString + "\r");
				fw.close();
			}

			
			// Tratando o ip do servidor
			Socket proxyToServerSocket = new Socket(address, port);
			proxyToServerSocket.setSoTimeout(5000);
			String serverAddress = String.valueOf(proxyToServerSocket.getRemoteSocketAddress());
			String urlServer = serverAddress.substring(0, serverAddress.indexOf('/'));
			System.out.println("Endereço do servidor: " + serverAddress);
			String ipServer = serverAddress.substring(serverAddress.indexOf('/') + 1, serverAddress.indexOf(':'));
			

			// Verificando se o ip de destino do request está bloqueado
			if(Proxy.isBlocked(ipServer)){
				System.out.println("Blocked site requested : " + urlServer);
				blockedSiteRequested();
			} else {
				String line = "HTTP/1.0 200 Connection established\r\n" +
					"Proxy-Agent: ProxyServer/1.0\r\n" +
					"\r\n";
				proxyClienteBw.write(line);
				proxyClienteBw.flush();

				fw = new FileWriter("logs.txt", true);
				fw.write(data+":\r\n"+ ipServer + ": "+ line + "\r");
				fw.close();
			}


			BufferedWriter proxyServerBw = new BufferedWriter(new OutputStreamWriter(proxyToServerSocket.getOutputStream()));

			BufferedReader proxyServerBr = new BufferedReader(new InputStreamReader(proxyToServerSocket.getInputStream()));



			
			TransmissionClientServerHttps clientToServerHttps = 
					new TransmissionClientServerHttps(clientSocket.getInputStream(), proxyToServerSocket.getOutputStream());
			
			httpsClientServer = new Thread(clientToServerHttps);
			httpsClientServer.start();
			
			
			try {
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = proxyToServerSocket.getInputStream().read(buffer);
					if (read > 0) {
						clientSocket.getOutputStream().write(buffer, 0, read);
						if (proxyToServerSocket.getInputStream().available() < 1) {
							clientSocket.getOutputStream().flush();
						}
					}
				} while (read >= 0);
			}
			catch (SocketTimeoutException e) {
				e.printStackTrace();
			}
			catch (IOException e) {
				e.printStackTrace();
			}


			if(proxyToServerSocket != null){
				proxyToServerSocket.close();
			}

			if(proxyServerBr != null){
				proxyServerBr.close();
			}

			if(proxyServerBw != null){
				proxyServerBw.close();
			}

			if(proxyClienteBw != null){
				proxyClienteBw.close();
			}
			
			
		} catch (SocketTimeoutException e) {
			String line = "HTTP/1.0 504 Timeout Occured after 10s\n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";
			try{
				proxyClienteBw.write(line);
				proxyClienteBw.flush();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		} 
		catch (Exception e){
			System.out.println("Error on HTTPS : " + urlString );
			e.printStackTrace();
		}
	}

	


	class TransmissionClientServerHttps implements Runnable{
		
		InputStream proxyClientIS;
		OutputStream proxyServerOS;
		
		public TransmissionClientServerHttps(InputStream proxyClientIS, OutputStream proxyServerOS) {
			this.proxyClientIS = proxyClientIS;
			this.proxyServerOS = proxyServerOS;
		}

		@Override
		public void run(){
			try {
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = proxyClientIS.read(buffer);
					if (read > 0) {
						proxyServerOS.write(buffer, 0, read);
						if (proxyClientIS.available() < 1) {
							proxyServerOS.flush();
						}
					}
				} while (read >= 0);
			}
			catch (SocketTimeoutException ste) {
				ste.printStackTrace();
			}
			catch (IOException e) {
				System.out.println("Leitura https do proxy para o cliente excedida");
				e.printStackTrace();
			}
		}
	}

	
	private void blockedSiteRequested(){
		try {
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
			String line = "HTTP/1.1 403 Forbidden \n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";
			bufferedWriter.write(line);
			bufferedWriter.flush();

			fw = new FileWriter("logs.txt", true);
			fw.write(data + ": \n" + ipServer + ": " + line + "\r");
			fw.write("Acesso proibido");
			fw.close();

		} catch (IOException e) {
			System.out.println("Error writing to client when requested a blocked site");
			e.printStackTrace();
		}
	}

}




