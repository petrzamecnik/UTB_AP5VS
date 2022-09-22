import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Main
 */

public class Main {

  public static void main(String[] args) {
    String host = "smtp.utb.cz";
    int port = 25;

    try {
      Socket s = new Socket(host, port);
      InputStream input = s.getInputStream();
      OutputStream output = s.getOutputStream();
      byte[] buffer = new byte[1000];
      int characterCount = input.read(buffer);

      System.out.write(buffer, 0, characterCount);
      output.write("HELLO pc1-01-101\r\n".getBytes());
      output.flush();
      characterCount = input.read(buffer);
      System.out.write(buffer, 0, characterCount);

      System.out.write(buffer, 0, characterCount);
      output.write("MAIL FROM: p_zamecnik@utb.cz\r\n".getBytes());
      output.flush();
      Thread.sleep(1000);
      characterCount = input.read(buffer);
      System.out.write(buffer, 0, characterCount);

    } catch (UnknownHostException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }
}