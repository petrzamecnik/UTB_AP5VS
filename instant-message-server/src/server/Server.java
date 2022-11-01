package server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.*;


class ActiveHandlers {
    //    private static final long serialVersionUID = 1L;
    private final HashSet<SocketHandler> activeHandlersSet = new HashSet<SocketHandler>();

    /**
     * sendMessageToAll - Pošle zprávu všem aktivním klientům kromě sebe sama
     *
     * @param sender  - reference odesílatele
     * @param message - řetězec se zprávou
     */
    synchronized void sendMessageToAll(SocketHandler sender, String message) {
        for (SocketHandler handler : activeHandlersSet) {
            if (handler != sender) {
                if (!handler.messages.offer(message))   // zkus přidat zprávu do fronty jeho zpráv
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
            }
        }
    }

    synchronized void sendPrivateMessage(SocketHandler sender, String privateUserID, String message) {
        boolean userExists = false;
        String userNotFoundMessage = "User \"" + privateUserID + "\" does not exist!";
        for (SocketHandler handler : activeHandlersSet) {
            if (handler.clientID.equals(privateUserID)) {
                userExists = true;
                if (!handler.messages.offer(message))
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
            }
        }

        if (!userExists) {
            for (SocketHandler handler : activeHandlersSet) {
                if (handler == sender) {
                    if (!handler.messages.offer(userNotFoundMessage))
                        System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);

                }
            }
        }
    }

    synchronized void sendGroupMessage(SocketHandler sender, String privateUserID, String groupName, String message) {
        for (SocketHandler handler : activeHandlersSet) {
            if (handler.userGroups.contains(groupName)) {
                if (!handler.messages.offer(message))
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
            }
        }
    }

    synchronized void sendInfoMessage(SocketHandler sender, String message) {
        for (SocketHandler handler : activeHandlersSet) {
            if (handler == sender) {
                if (!handler.messages.offer(message))
                    System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientID);
            }
        }
    }

    /**
     * add přidá do množiny aktivních handlerů nový handler.
     * Metoda je sychronizovaná, protože HashSet neumí multithreading.
     *
     * @param handler - reference na handler, který se má přidat.
     */
    synchronized void add(SocketHandler handler) {
        activeHandlersSet.add(handler);
    }

    /**
     * remove odebere z množiny aktivních handlerů nový handler.
     * Metoda je sychronizovaná, protože HashSet neumí multithreading.
     *
     * @param handler - reference na handler, který se má odstranit
     */
    synchronized void remove(SocketHandler handler) {
        activeHandlersSet.remove(handler);
    }
}


class SocketHandler {
    /**
     * mySocket je socket, o který se bude tento SocketHandler starat
     */
    Socket mySocket;

    /**
     * List of groups which have user joined
     */
    List<String> userGroups = new ArrayList<>();

    /**
     * client ID je řetězec ve formátu <IP_adresa>:<port>
     */
    String clientID;

    /**
     * activeHandlers je reference na množinu všech právě běžících SocketHandlerů.
     * Potřebujeme si ji udržovat, abychom mohli zprávu od tohoto klienta
     * poslat všem ostatním!
     */
    final ActiveHandlers activeHandlers;

    /**
     * messages je fronta příchozích zpráv, kterou musí mít kažý klient svoji
     * vlastní  - pokud bude je přetížená nebo nefunkční klientova síť,
     * čekají zprávy na doručení právě ve frontě messages
     */
    ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20);

    /**
     * startSignal je synchronizační závora, která zařizuje, aby oba tasky
     * OutputHandler.run() a InputHandler.run() začaly ve stejný okamžik.
     */
    CountDownLatch startSignal = new CountDownLatch(2);

    /**
     * outputHandler.run() se bude starat o OutputStream mého socketu
     */
    OutputHandler outputHandler = new OutputHandler();
    /**
     * inputHandler.run()  se bude starat o InputStream mého socketu
     */
    InputHandler inputHandler = new InputHandler();
    /**
     * protože v outputHandleru nedovedu detekovat uzavření socketu, pomůže mi inputFinished
     */
    volatile boolean inputFinished = false;

    public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
        this.mySocket = mySocket;
        clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
        this.activeHandlers = activeHandlers;
    }

    class OutputHandler implements Runnable {
        public void run() {
            OutputStreamWriter writer;
            try {
                System.err.println("DBG>Output handler starting for " + clientID);
                startSignal.countDown();
                startSignal.await();
                System.err.println("DBG>Output handler running for " + clientID);
                writer = new OutputStreamWriter(mySocket.getOutputStream(), StandardCharsets.UTF_8);
                writer.write("\nYou are connected from " + clientID + "\n");
                writer.flush();
                while (!inputFinished) {
                    // blokující čtení - pokud není ve frontě zpráv nic, uspi se!
                    // pokud nějaké zprávy od ostatních máme,
                    // pošleme je našemu klientovi
                    String m = messages.take();
                    writer.write(m + "\r\n");
                    writer.flush();
                    System.err.println("DBG>Message sent to " + clientID + ":" + m + "\n");
                }
            } catch (IOException | InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            System.err.println("DBG>Output handler for " + clientID + " has finished.");
        }
    }

    class InputHandler implements Runnable {
        public void run() {
            try {
                System.err.println("DBG>Input handler starting for " + clientID);
                startSignal.countDown();
                startSignal.await();
                System.err.println("DBG>Input handler running for " + clientID);

                // init variables
                String request;
                String command;
                String commandValue;
                String privateMessage;
                String privateClientID;
                String group;
                String groupMessage;

                boolean isPrivate;
                boolean isInfoMessage;
                boolean isGroupMessage;

                /* v okamžiku, kdy nás Thread pool spustí, přidáme se do množiny
                   všech aktivních handlerů, aby chodily zprávy od ostatních i nám
                 */
                activeHandlers.add(SocketHandler.this);
                BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), StandardCharsets.UTF_8));
                while ((request = reader.readLine()) != null) {

                    // reset variables
                    command = "";
                    commandValue = "";
                    privateClientID = "";
                    privateMessage = "";
                    group = "";
                    groupMessage = "";
                    isPrivate = false;
                    isInfoMessage = false;
                    isGroupMessage = false;

                    if (request.length() > 0 && request.charAt(0) == '#') {
                        List<Integer> allSpaceIndexes = new ArrayList<>();
                        for (int i = 0; i < request.length(); i++) {
                            char c = request.charAt(i);
                            if (c == ' ') {
                                allSpaceIndexes.add(i);
                            }
                        }

                        if (allSpaceIndexes.size() == 0) {
                            allSpaceIndexes.add(request.length());
                        }

                        try {
                            command = request.substring(1, allSpaceIndexes.get(0)).trim();
                            commandValue = request.substring(allSpaceIndexes.get(0)).trim();
                        } catch (Exception ignored) {
                        }

                        switch (command) {
                            case "setname" -> {
                                clientID = commandValue;
                                request = "You have changed your name to: " + clientID;
                                isInfoMessage = true;
                            }
                            case "pm" -> {
                                privateClientID = request.substring(allSpaceIndexes.get(0), allSpaceIndexes.get(1)).trim();
                                privateMessage = request.substring(allSpaceIndexes.get(1)).trim();
                                isPrivate = true;
                            }
                            case "ng" -> {
                                Globals.allGroups.add(commandValue);
                                request = "You have created a new group: " + commandValue;
                                isInfoMessage = true;
                            }
                            case "lsg" -> {
                                request = "Groups: " + Globals.allGroups;
                                isInfoMessage = true;
                            }
                            case "lsmg" -> {
                                request = "You are in these groups: " + userGroups;
                                isInfoMessage = true;
                            }
                            case "jg" -> {
                                if (Globals.allGroups.contains(commandValue)) {
                                    userGroups.add(commandValue);
                                    request = "You have joined group: " + commandValue;
                                } else {
                                    request = "This group does not exist!";
                                }
                                isInfoMessage = true;
                            }
                            case "lg" -> {
                                if (Globals.allGroups.contains(commandValue)) {
                                    userGroups.remove(commandValue);
                                    request = "You have left group: " + commandValue;
                                } else {
                                    request = "This group does not exist!";
                                }
                                isInfoMessage = true;
                            }
                            case "gm" -> {
                                group = request.substring(allSpaceIndexes.get(0), allSpaceIndexes.get(1)).trim();
                                groupMessage = request.substring(allSpaceIndexes.get(1)).trim();
                                isGroupMessage = true;
                            }
                            case "help" -> {
                                request = """
                                        \n
                                        #setname name           -> change name
                                        #pm user message        -> send private message
                                        #ng groupName           -> create new group
                                        #lsg                    -> list all groups
                                        #lsmg                   -> list all groups you are member of
                                        #jg name                -> join group
                                        #lg name                -> leave group
                                        #gm groupName message   -> send group message
                                        """;
                                isInfoMessage = true;
                            }
                            default -> {
                                System.out.println("command: " + command + " | " + "commandValue: " + commandValue);
                                request = "Invalid command!";
                                isInfoMessage = true;
                            }
                        }
                    }

                    if (isPrivate) {
                        request = "Private message from " + clientID + " -> " + privateMessage;
                        System.out.println(request);
                        activeHandlers.sendPrivateMessage(SocketHandler.this, privateClientID, request);

                    } else if (isInfoMessage) {
                        request = "Info -> " + request;
                        activeHandlers.sendInfoMessage(SocketHandler.this, request);

                    } else if (isGroupMessage) {
                        request = "From " + clientID + " in: " + group + " -> " + groupMessage;
                        activeHandlers.sendGroupMessage(SocketHandler.this, clientID, group, request);

                    } else {
                        request = "From " + clientID + " -> " + request;
                        System.out.println(request);
                        activeHandlers.sendMessageToAll(SocketHandler.this, request);
                    }
                }
                inputFinished = true;
                messages.offer("OutputHandler, wakeup and die!");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                // remove yourself from the set of activeHandlers
                synchronized (activeHandlers) {
                    activeHandlers.remove(SocketHandler.this);
                }
            }
            System.err.println("DBG>Input handler for " + clientID + " has finished.");
        }
    }
}

public class Server {

    public static void main(String[] args) {
        int port = 33000, max_conn = 20;

        if (args.length > 0) {
            if (args[0].startsWith("--help")) {
                System.out.printf("""
                        Usage: Server [PORT] [MAX_CONNECTIONS]
                        If PORT is not specified, default port %d is used
                        If MAX_CONNECTIONS is not specified, default number=%d is used""", port, max_conn);
                return;
            }
            try {
                port = Integer.decode(args[0]);
            } catch (NumberFormatException e) {
                System.err.printf("Argument %s is not integer, using default value", args[0], port);
            }
            if (args.length > 1) try {
                max_conn = Integer.decode(args[1]);
            } catch (NumberFormatException e) {
                System.err.printf("Argument %s is not integer, using default value", args[1], max_conn);
            }

        }
        // TODO Auto-generated method stub
        System.out.printf("IM server listening on port %d, maximum nr. of connections=%d...\n", port, max_conn);
        ExecutorService pool = Executors.newFixedThreadPool(2 * max_conn);
        ActiveHandlers activeHandlers = new ActiveHandlers();

        try {
            ServerSocket sSocket = new ServerSocket(port);
            do {
                Socket clientSocket = sSocket.accept();
                clientSocket.setKeepAlive(true);
                SocketHandler handler = new SocketHandler(clientSocket, activeHandlers);
                pool.execute(handler.inputHandler);
                pool.execute(handler.outputHandler);
            } while (!pool.isTerminated());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            pool.shutdown();
            try {
                // Wait a while for existing tasks to terminate
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    pool.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!pool.awaitTermination(60, TimeUnit.SECONDS)) System.err.println("Pool did not terminate");
                }
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                pool.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
        }
    }
}


