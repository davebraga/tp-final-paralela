package puc.paralela;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Borda {
        private static final int BORDA_NODE_TCP_PORT = 12346;
    private static final String CENTRAL_NODE_IP = "127.0.0.1"; // IP do Nó Central
    private static final int CENTRAL_NODE_TCP_PORT = 12345;

    private static ExecutorService clientHandlerPool = Executors.newFixedThreadPool(5); // Pool para lidar com conexões de gateway
    private static ArrayBlockingQueue<String> dataQueueToCentral = new ArrayBlockingQueue<>(100); // Fila para o nó central

    public static void main(String[] args) {
        startBordaServer();
        startCentralNodeDispatcher();
        // O main thread pode esperar indefinidamente
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("Nó de Borda main thread interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Inicia o servidor TCP para receber dados dos Gateways.
     */
    private static void startBordaServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(BORDA_NODE_TCP_PORT)) {
                System.out.println("Nó de Borda ouvindo em TCP Porta " + BORDA_NODE_TCP_PORT + " para Gateways.");
                while (true) {
                    Socket clientSocket = serverSocket.accept(); // Aceita uma nova conexão
                    clientHandlerPool.submit(() -> handleGatewayConnection(clientSocket));
                }
            } catch (IOException e) {
                System.err.println("Erro no servidor do Nó de Borda: " + e.getMessage());
                e.printStackTrace();
            } finally {
                clientHandlerPool.shutdown();
            }
        }).start();
    }

    /**
     * Lida com a conexão de um Gateway, recebendo e processando os dados.
     * @param clientSocket Socket do Gateway conectado.
     */
    private static void handleGatewayConnection(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        System.out.println("Nó de Borda: Conexão recebida do Gateway " + clientAddress);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) { // Lê linhas (mensagens delimitadas por '\n')
                processAndForwardData(line);
            }
        } catch (IOException e) {
            System.err.println("Erro ao lidar com a conexão do Gateway " + clientAddress + ": " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar o socket do Gateway: " + e.getMessage());
            }
            System.out.println("Nó de Borda: Conexão com o Gateway " + clientAddress + " fechada.");
        }
    }

    /**
     * Processa os dados recebidos (detecção de anomalias) e os enfileira para o Nó Central.
     * @param dataString Dados JSON em formato String.
     */
    private static void processAndForwardData(String dataString) {
        try {
            JSONObject data = new JSONObject(dataString);
            String brincoId = data.optString("brinco_id", "N/A");
            double temperatura = Double.parseDouble(data.optString("temperatura", "0.0"));

            boolean alertaFebre = false;
            if (temperatura > 39.5) { // Exemplo de anomalia: febre (temperatura > 39.5°C)
                System.out.println("ALERTA DO NÓ DE BORDA! Brinco " + brincoId + ": Temperatura alta (" + temperatura + "°C).");
                alertaFebre = true;
            }
            data.put("alerta_febre", alertaFebre);
            data.put("processed_at_borda_ms", System.currentTimeMillis()); // Adiciona timestamp de processamento

            dataQueueToCentral.put(data.toString()); // Adiciona à fila para envio ao central
            System.out.println("Nó de Borda processou e enfileirou dados do brinco " + brincoId + " para o Nó Central.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Processamento e encaminhamento interrompidos.");
        } catch (Exception e) {
            System.err.println("Erro ao processar dados no Nó de Borda: " + e.getMessage() + ". Dados: " + dataString);
        }
    }

    /**
     * Inicia o despachante TCP que pega dados da fila e os envia para o Nó Central.
     */
    private static void startCentralNodeDispatcher() {
        new Thread(() -> {
            while (true) {
                try {
                    String dataToSend = dataQueueToCentral.take(); // Bloqueia até haver dados na fila
                    sendDataToCentralNode(dataToSend);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Despachante para Nó Central interrompido.");
                    break;
                } catch (Exception e) {
                    System.err.println("Erro no despachante para Nó Central: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Envia dados para o Nó Central via TCP. Inclui lógica de retentativa básica.
     * @param data Dados JSON em formato String.
     */
    private static void sendDataToCentralNode(String data) {
        int maxRetries = 5;
        int currentRetry = 0;
        boolean sent = false;

        while (!sent && currentRetry < maxRetries) {
            try (Socket centralSocket = new Socket(CENTRAL_NODE_IP, CENTRAL_NODE_TCP_PORT)) {
                centralSocket.setSoTimeout(5000); // Timeout de 5 segundos
                OutputStream os = centralSocket.getOutputStream();
                os.write((data + "\n").getBytes());
                os.flush();
                System.out.println("Nó de Borda enviou dados para o Nó Central: " + data);
                sent = true;
            } catch (IOException e) {
                currentRetry++;
                System.err.println("Erro ao conectar ou enviar para o Nó Central. Retentativa " + currentRetry + "/" + maxRetries + ": " + e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(2 * currentRetry);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Retentativa para Nó Central interrompida.");
                    break;
                }
            }
        }
        if (!sent) {
            System.err.println("Falha ao enviar dados para o Nó Central após " + maxRetries + " retentativas: " + data);
        }
    }
}
