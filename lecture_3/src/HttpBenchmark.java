import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class Task implements Runnable {
    static String host = "localhost";
    static int port = 8080;
    static AtomicInteger sum = new AtomicInteger();


    public void run() {

        int count;
        byte[] buffer = new byte[100000];

        try {
            for (int i = 0; i < 10000; i++) {
                Socket s = new Socket(host, port);
                InputStream input = s.getInputStream();
                OutputStream output = s.getOutputStream();

                output.write("GET / HTTP/1.0\r\n\r\n".getBytes());
                output.flush();

                while ((count = input.read(buffer)) != -1) {
                    sum.addAndGet(count);
                }

                s.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

public class HttpBenchmark {

    public static void main(String[] args) {

        if (args.length > 1) {
            Task.host = args[0];
            Task.port = Integer.parseInt(args[1]);
        }

        System.out.println("Running!");
        Task task_1 = new Task();
        ExecutorService exec = Executors.newFixedThreadPool(100);

        for (int i = 0; i < 100; i++) {
            exec.execute(task_1);
        }
        int counter = 0;
        int previous = 0;

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            previous = counter;
            counter = Task.sum.get();
            int bandwidth = counter - previous;
            if (bandwidth > (1 << 20)) {
                System.out.println("" + (bandwidth >> 20) + " MB/s");
            } else if (bandwidth > (1 << 10)) {
                System.out.println("" + (bandwidth >> 10) + " KB/s");
            } else {
                System.out.println("" + bandwidth + " B/s");

            }
        }
    }
}

