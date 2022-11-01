import javax.net.ssl.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class socketHandler implements Runnable {
    protected final Socket socket;
    long lastActivityTime = System.currentTimeMillis();

    public socketHandler(Socket socket) {
        this.socket = socket;
    }

    ;

    public void run() {
        try {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            int _char;
            while ((_char = input.read()) != -1) {
                lastActivityTime = System.currentTimeMillis();
                output.write("\r\nSent: ".getBytes());
                output.write(_char);
                output.flush();
            }
            System.out.println("Client has disconnected. " + socket.getPort());
            socket.close();
        } catch (IOException e) {

        }
    }
}


public class Server {
    static int port = 9999;

    static class SavingTrustManager implements X509TrustManager {

        private final X509TrustManager tm;
        private X509Certificate[] chain;

        SavingTrustManager(X509TrustManager tm) {
            this.tm = tm;
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new UnsupportedOperationException();
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            this.chain = chain;
            tm.checkServerTrusted(chain, authType);
        }
    }

    public static void main(String[] args) {
        String ksName = "zamecnik.p12";
        char ksPass[] = "testpass".toCharArray();
        char ctPass[] = "testpass".toCharArray();

        try {
            KeyStore ks = KeyStore.getInstance("pkcs12");
            ks.load(new FileInputStream(ksName), ksPass);
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, ctPass);
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), null, null);
            SSLServerSocketFactory ssf = sc.getServerSocketFactory();
            SSLServerSocket sslServerSocket
                    = (SSLServerSocket) ssf.createServerSocket(8888);
            printServerSocketInfo(sslServerSocket);
            SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
            printSocketInfo(sslSocket);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    sslSocket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    sslSocket.getInputStream()));
            String message = "Welcome to SSL Reverse Echo Server." +
                    " Please type in some words.";
            writer.write(message, 0, message.length());
            writer.newLine();
            writer.flush();

            watchDog watchDog = new watchDog();
            watchDog.start();
            ExecutorService exec = Executors.newFixedThreadPool(10);

            while ((message = reader.readLine()) != null) {
                socketHandler handler = new socketHandler(sslSocket);
                watchDog.add(handler);
                exec.execute(handler);

                writer.write(message);
                writer.newLine();
                writer.flush();


//                socketHandler handler = new socketHandler(sslSocket);
//                watchDog.add(handler);
//                exec.execute(handler);

                System.out.println("New client: " + sslSocket.getInetAddress() + ":" + sslSocket.getPort());
            }

        } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException |
                 KeyManagementException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }

    }
    private static void printSocketInfo(SSLSocket s) {
        System.out.println("Socket class: " + s.getClass());
        System.out.println("   Remote address = "
                + s.getInetAddress().toString());
        System.out.println("   Remote port = " + s.getPort());
        System.out.println("   Local socket address = "
                + s.getLocalSocketAddress().toString());
        System.out.println("   Local address = "
                + s.getLocalAddress().toString());
        System.out.println("   Local port = " + s.getLocalPort());
        System.out.println("   Need client authentication = "
                + s.getNeedClientAuth());
        SSLSession ss = s.getSession();
        System.out.println("   Cipher suite = " + ss.getCipherSuite());
        System.out.println("   Protocol = " + ss.getProtocol());
    }

    private static void printServerSocketInfo(SSLServerSocket s) {
        System.out.println("Server socket class: " + s.getClass());
        System.out.println("   Socket address = "
                + s.getInetAddress().toString());
        System.out.println("   Socket port = "
                + s.getLocalPort());
        System.out.println("   Need client authentication = "
                + s.getNeedClientAuth());
        System.out.println("   Want client authentication = "
                + s.getWantClientAuth());
        System.out.println("   Use client mode = "
                + s.getUseClientMode());
    }
}

class watchDog extends Thread {
    LinkedList<socketHandler> list = new LinkedList<>();

    synchronized void add(socketHandler handler) {
        list.add(handler);
    }

    synchronized void remove(socketHandler handler) {
        list.remove(handler);
    }

    synchronized void checkHandlerActivity() {
        try {
            long time = System.currentTimeMillis();
            for (socketHandler handler : list) {
                if (time - handler.lastActivityTime > 20000) {
                    System.out.println("Killed: " + handler.socket.getPort());
                    handler.socket.close();
                    remove(handler);
                }
            }

        } catch (Exception ignored) {

        }
    }

    public void run() {
        while (true) {
            try {
                checkHandlerActivity();
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
    }

}
