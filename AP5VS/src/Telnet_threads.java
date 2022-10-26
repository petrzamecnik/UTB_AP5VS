import javax.net.ssl.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

class myThread extends Thread {
    InputStream input;
    Robot robot = new Robot();


    public myThread(InputStream input) throws AWTException {
        this.input = input;
    }

    public void run() {
        try {
            int char_;
            while ((char_ = input.read()) != -1) {
                System.out.print((char) char_);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Thread end");
        robot.keyPress(KeyEvent.VK_ENTER);
        System.out.print("\r\n");
    }
}

public class Telnet_threads {

    private static class SavingTrustManager implements X509TrustManager {

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
        Socket s;
        InputStream input;
        OutputStream output;
//        String host;
//        int port;

        String host = "localhost";
        int port = 9999;

        try {
            String keyFile = "public.p12";
            char passPhrase[] = "testpass".toCharArray();
            System.out.println("Loading key: " + keyFile);

            KeyStore ks = KeyStore.getInstance("pkcs12");
            ks.load(new FileInputStream(keyFile), passPhrase);
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passPhrase);
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), null, null);

            SSLServerSocketFactory ssf = sc.getServerSocketFactory();
            SSLServerSocket socket
                    = (SSLServerSocket) ssf.createServerSocket(8888);
            SSLSocket c = (SSLSocket) socket.accept();

            s = new Socket(host, port);
            input = s.getInputStream();
            output = s.getOutputStream();

            Thread thread_1 = new myThread(input);
            thread_1.start();

            while (true) {
                if (!thread_1.isAlive() || s.isClosed()) return;
                int char_ = System.in.read();
                output.write(char_);
                output.flush();
            }

        } catch (IOException | AWTException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        } catch (UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }


//        if (args.length > 1) {
//            host = args[0];
//            port = Integer.parseInt(args[1]);
//
//            try {
//                s = new Socket(host, port);
//                input = s.getInputStream();
//                output = s.getOutputStream();
//
//                Thread thread_1 = new myThread(input);
//                thread_1.start();
//
//                while (true) {
//                    if (!thread_1.isAlive() || s.isClosed()) return;
//                    int char_ = System.in.read();
//                    output.write(char_);
//                    output.flush();
//                }
//
//            } catch (IOException | AWTException e) {
//                throw new RuntimeException(e);
//            }
//        }
    }
}
