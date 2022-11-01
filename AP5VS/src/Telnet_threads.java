import javax.net.ssl.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
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
        try {
            String file = "public.p12";
            char passphrase[] = "testpass".toCharArray();
            System.out.println("Loading KeyStore " + file + "...");
            InputStream inf = new FileInputStream(file);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());//"pkcs12");
            ks.load(inf, passphrase);
            inf.close();

            SSLContext context = SSLContext.getInstance("TLS");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
            SavingTrustManager tm = new SavingTrustManager(defaultTrustManager);
            context.init(null, new TrustManager[] { tm }, null);

            SSLSocketFactory factory = context.getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket("localhost", 8888);

            socket.startHandshake();
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

            out.println("Ahoj, tady je SSL Socket klient\n A to je vse\n.");
            out.println();
            out.flush();

            if (out.checkError())
                System.out.println("SSLSocketClient:  java.io.PrintWriter error");

//            while (true) {
//                if (!thread_1.isAlive() || s.isClosed()) return;
//                int char_ = System.in.read();
//                output.write(char_);
//                output.flush();
//            }

            /* read response */
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null)
                System.out.println(inputLine);

            in.close();
            out.close();
            socket.close();

        } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException |
                 KeyManagementException e) {
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
