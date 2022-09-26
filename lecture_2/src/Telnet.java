import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Telnet {
    public static void main(String[] args) {
        Socket s;
        InputStream input;
        OutputStream output;

        try {
            s = new Socket("smtp.utb.cz", 25);
            input = s.getInputStream();
            output = s.getOutputStream();
            int char_;

            while (true) {
                if (input.available() > 0) {
                    char_ = input.read();
                    System.out.println((char) char_);

                }
                if (System.in.available() > 0) {
                    char_ = System.in.read();
                    output.write(char_);
                    output.flush();
                }

                Thread.sleep(10);
            }
        } catch (Exception e) {
        }
    }
}
