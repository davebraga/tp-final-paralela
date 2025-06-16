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
    private static final int GATEWAY_UDP_PORT = 12347;
    private static final String BORDA_NODE_IP = "127.0.0.1"; // IP do Nó de Borda
    private static final int BORDA_NODE_TCP_PORT = 12346;

    // Fila bloqueante para armazenar dados recebidos antes de enviar para o nó de borda
    private static ArrayBlockingQueue<String> dataQueue = new ArrayBlockingQueue<>(100);

    /**
     * Inicia o ouvinte UDP para receber dados dos brincos.
     */
    private static void startUdpReceiver() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(GATEWAY_UDP_PORT)) {
                System.out.println("Gateway ouvindo dados dos brincos em UDP Porta " + GATEWAY_UDP_PORT);
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String receivedData = new String(packet.getData(), 0, packet.getLength());
                    try {
                        dataQueue.put(receivedData); // Adiciona à fila
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

    /**
     * Inicia o despachante TCP que pega dados da fila e os envia para o Nó de Borda.
     */
    private static void startTcpDispatcher() {
        new Thread(() -> {
            while (true) {
                try {
                    String dataToSend = dataQueue.take(); // Bloqueia até haver dados na fila
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

    /**
     * Envia dados para o Nó de Borda via TCP. Inclui lógica de retentativa básica.
     * @param data Dados JSON em formato String.
     */
    private static void sendDataToBordaNode(String data) {
        int maxRetries = 5;
        int currentRetry = 0;
        boolean sent = false;

        while (!sent && currentRetry < maxRetries) {
            try (Socket bordaSocket = new Socket(BORDA_NODE_IP, BORDA_NODE_TCP_PORT)) {
                bordaSocket.setSoTimeout(5000); // Timeout de 5 segundos para conexão/escrita
                OutputStream os = bordaSocket.getOutputStream();
                os.write((data + "\n").getBytes()); // Adiciona '\n' como delimitador de mensagem
                os.flush();
                System.out.println("Gateway enviou dados para o Nó de Borda: " + data);
                sent = true;
            } catch (IOException e) {
                currentRetry++;
                System.err.println("Erro ao conectar ou enviar para o Nó de Borda. Retentativa " + currentRetry + "/" + maxRetries + ": " + e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(2 * currentRetry); // Aumenta o tempo de espera
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Retentativa interrompida.");
                    break;
                }
            }
        }
        if (!sent) {
            System.err.println("Falha ao enviar dados para o Nó de Borda após " + maxRetries + " retentativas: " + data);
            // Lógica para lidar com falha persistente: logar, notificar, descartar, etc.
        }
    }

    public static void main(String[] args) {
        startUdpReceiver();
        startTcpDispatcher();
        // O main thread pode esperar indefinidamente ou ser usado para outras tarefas de gerenciamento.
        try {
            Thread.currentThread().join(); // Mantém o main thread vivo
        } catch (InterruptedException e) {
            System.err.println("Gateway main thread interrupted.");
            Thread.currentThread().interrupt();
        }
    }
}
