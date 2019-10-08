public class Teste {
     public static void main(String[] args) {
        String serverAddress = "adservice.google.com/172.217.29.130:443";
        System.out.println(serverAddress);
        String urlServer = serverAddress.substring(0, serverAddress.indexOf('/'));
        System.out.println(urlServer);
        String ipServer = serverAddress.substring(serverAddress.indexOf('/') + 1, serverAddress.indexOf(':'));
        System.out.println(ipServer);
    }
}