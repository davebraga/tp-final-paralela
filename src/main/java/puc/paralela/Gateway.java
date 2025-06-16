package puc.paralela;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/**
 * O Gateway recebe dados de brincos via UDP, os coloca em uma fila
 * e uma thread separada os envia para o Nó de Borda via TCP.
 */
class Gateway {
    private static final int GATEWAY_UDP_PORT = 12345; // Porta local do Gateway para receber dos brincos

    private static String BORDA_NODE_IP;      // Definido via argumento de linha de comando
    private static int BORDA_NODE_TCP_PORT; // Definido via argumento de linha de comando

    private static ArrayBlockingQueue<String> dataQueue = new ArrayBlockingQueue<>(100);

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java -jar gateway-1.0-SNAPSHOT-jar-with-dependencies.jar <IP_NOBORDA> <PORTA_NOBORDA>");
            return;
        }

        BORDA_NODE_IP = args[0];
        BORDA_NODE_TCP_PORT = Integer.parseInt(args[1]);

        startUdpReceiver();
        startTcpDispatcher();
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("Gateway main thread interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    private static void startUdpReceiver() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(GATEWAY_UDP_PORT)) {
                System.out.println("Gateway ouvindo dados dos brincos em UDP Porta " + GATEWAY_UDP_PORT);
                System.out.println("Conectando-se ao Nó de Borda em " + BORDA_NODE_IP + ":" + BORDA_NODE_TCP_PORT);
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String receivedData = new String(packet.getData(), 0, packet.getLength());
                    try {
                        dataQueue.put(receivedData);
                        System.out.println("Gateway recebeu dados do brinco e enfileirou: " + receivedData);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Receptor UDP interrompido.");
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro no receptor UDP do Gateway: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private static void startTcpDispatcher() {
        new Thread(() -> {
            while (true) {
                try {
                    String dataToSend = dataQueue.take();
                    sendDataToBordaNode(dataToSend);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Despachante TCP interrompido.");
                    break;
                } catch (Exception e) {
                    System.err.println("Erro no despachante TCP do Gateway: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void sendDataToBordaNode(String data) {
        int maxRetries = 5;
        int currentRetry = 0;
        boolean sent = false;

        while (!sent && currentRetry < maxRetries) {
            try (Socket bordaSocket = new Socket(BORDA_NODE_IP, BORDA_NODE_TCP_PORT)) {
                bordaSocket.setSoTimeout(5000);
                OutputStream os = bordaSocket.getOutputStream();
                os.write((data + "\n").getBytes());
                os.flush();
                System.out.println("Gateway enviou dados para o Nó de Borda (via " + BORDA_NODE_IP + ":" + BORDA_NODE_TCP_PORT + "): " + data);
                sent = true;
            } catch (IOException e) {
                currentRetry++;
                System.err.println("Erro ao conectar ou enviar para o Nó de Borda (retentativa " + currentRetry + "/" + maxRetries + "): " + e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(2 * currentRetry);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Retentativa interrompida.");
                    break;
                }
            }
        }
        if (!sent) {
            System.err.println("Falha ao enviar dados para o Nó de Borda após " + maxRetries + " retentativas: " + data);
        }
    }
