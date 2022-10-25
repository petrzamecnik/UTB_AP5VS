import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                if (time - handler.lastActivityTime > 10000) {
                    System.out.println("Killed: " + handler.socket.getPort());
                    handler.socket.close();
                    remove(handler);
                }
            }

        } catch (Exception e) {

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
        System.out.println("Hello world!");

        try {
            String keyFile = "zamecnik.p12";
            char passPhrase[] = "testpass".toCharArray();
            System.out.println("Loading key: " + keyFile);
            InputStream inf = new FileInputStream(keyFile);

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(inf, passPhrase);
            inf.close();

            SSLContext context = SSLContext.getInstance("TLS");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
            SavingTrustManager tm = new SavingTrustManager(defaultTrustManager);
            context.init(null, new TrustManager[] { tm }, null);

            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("server running");

            SSLSocketFactory factory = context.getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket("localhost", port);



            watchDog watchDog = new watchDog();
            watchDog.start();
            ExecutorService exec = Executors.newFixedThreadPool(2);

            while (true) {
                Socket s = serverSocket.accept();
                socketHandler handler = new socketHandler(s);
                watchDog.add(handler);
                exec.execute(handler);

                System.out.println("New client: " + s.getInetAddress() + ":" + s.getPort());
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }
}