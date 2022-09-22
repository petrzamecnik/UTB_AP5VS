import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

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
    public static void main(String[] args) {
        Socket s;
        InputStream input;
        OutputStream output;
        String host;
        int port;

        if (args.length > 1) {
            host = args[0];
            port = Integer.parseInt(args[1]);

            try {
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
            }
        }
    }
}
