package vyuka;

import java.net.*;
import java.io.*;

public class EmailSender {
    Socket s;
    InputStream input;
    OutputStream output;
    int characterCount;
    byte[] buffer = new byte[1000];
    String endMessage = "\r\n";
    String endConnection = "\r\n.\r\n";

    /*
     * Constructor opens Socket to host/port. If the Socket throws an exception
     * during opening,
     * the exception is not handled in the constructor.
     */
    protected EmailSender(String host, int port) {
        try {
            s = new Socket(host, port);
            input = s.getInputStream();
            output = s.getOutputStream();
            characterCount = input.read(buffer);

            output.write(("HELO pc1-01-101" + endMessage).getBytes());
            output.flush();
            characterCount = input.read(buffer);
            System.out.write(buffer, 0, characterCount); // print server response
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    /*
     * sends email from an email address to an email address with some subject and
     * text.
     * If the Socket throws an exception during sending, the exception is not
     * handled by this method.
     */
    protected void send(String from, String to, String subject, String text) {
        try {
            output.write(("MAIL FROM:" + from + endMessage).getBytes());
            output.flush();
            characterCount = input.read(buffer);
            System.out.write(buffer, 0, characterCount);

            output.write(("RCPT TO:" + to + endMessage).getBytes());
            output.flush();
            characterCount = input.read(buffer);
            System.out.write(buffer, 0, characterCount);

            output.write(("DATA" + endMessage).getBytes());
            output.flush();
            characterCount = input.read(buffer);

            output.write(("SUBJECT:" + subject + endMessage).getBytes());
            output.write((text + endConnection).getBytes());
            output.flush();

        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    /*
     * sends QUIT and closes the socket
     *
     * \r\n.\r\n <-- quit sequence
     */
    protected void close() {
        try {
            output.write(("QUIT" + endMessage).getBytes());
            output.flush();
            Thread.sleep(1000);
            characterCount = input.read(buffer);
            System.out.write(buffer, 0, characterCount);
            s.close();
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
}