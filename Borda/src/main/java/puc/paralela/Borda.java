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
import org.json.JSONObject;

public class Borda {
     private static final int BORDA_NODE_TCP_PORT = 12346; // Porta local do Nó de Borda

    private static String CENTRAL_NODE_IP;   // Definido via argumento de linha de comando
    private static int CENTRAL_NODE_TCP_PORT; // Definido via argumento de linha de comando

    private static ExecutorService clientHandlerPool = Executors.newFixedThreadPool(5);
    private static ArrayBlockingQueue<String> dataQueueToCentral = new ArrayBlockingQueue<>(100);

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java -jar Borda-1.0-SNAPSHOT-jar-with-dependencies.jar <IP_NOCENTRAL> <PORTA_NOCENTRAL>");
            return;
        }

        CENTRAL_NODE_IP = args[0];
        CENTRAL_NODE_TCP_PORT = Integer.parseInt(args[1]);

        startBordaServer();
        startCentralNodeDispatcher();
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("Nó de Borda main thread interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    private static void startBordaServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(BORDA_NODE_TCP_PORT)) {
                System.out.println("Nó de Borda ouvindo em TCP Porta " + BORDA_NODE_TCP_PORT + " para Gateways.");
                System.out.println("Conectando-se ao Nó Central em " + CENTRAL_NODE_IP + ":" + CENTRAL_NODE_TCP_PORT);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
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

    private static void handleGatewayConnection(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        System.out.println("Nó de Borda: Conexão recebida do Gateway " + clientAddress);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
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

    private static void processAndForwardData(String dataString) {
        try {
            JSONObject data = new JSONObject(dataString);
            String brincoId = data.optString("brinco_id", "N/A");
            String temperaturaStr = data.optString("temperatura", "0.0");
            double temperatura = Double.parseDouble(temperaturaStr.replace(',', '.'));

            boolean alertaFebre = false;
            if (temperatura > 39.5) {
                System.out.println("ALERTA DO NÓ DE BORDA! Brinco " + brincoId + ": Temperatura alta (" + temperatura + "°C).");
                alertaFebre = true;
            }
            data.put("alerta_febre", alertaFebre);
            data.put("processed_at_borda_ms", System.currentTimeMillis());

            dataQueueToCentral.put(data.toString());
            System.out.println("Nó de Borda processou e enfileirou dados do brinco " + brincoId + " para o Nó Central.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Processamento e encaminhamento interrompidos.");
        } catch (Exception e) {
            System.err.println("Erro ao processar dados no Nó de Borda: " + e.getMessage() + ". Dados: " + dataString);
        }
    }

    private static void startCentralNodeDispatcher() {
        new Thread(() -> {
            while (true) {
                try {
                    String dataToSend = dataQueueToCentral.take();
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

    private static void sendDataToCentralNode(String data) {
        int maxRetries = 5;
        int currentRetry = 0;
        boolean sent = false;

        while (!sent && currentRetry < maxRetries) {
            try (Socket centralSocket = new Socket(CENTRAL_NODE_IP, CENTRAL_NODE_TCP_PORT)) {
                centralSocket.setSoTimeout(5000);
                OutputStream os = centralSocket.getOutputStream();
                os.write((data + "\n").getBytes());
                os.flush();
                System.out.println("Nó de Borda enviou dados para o Nó Central (via " + CENTRAL_NODE_IP + ":" + CENTRAL_NODE_TCP_PORT + "): " + data);
                sent = true;
            } catch (IOException e) {
                currentRetry++;
                System.err.println("Erro ao conectar ou enviar para o Nó Central (retentativa " + currentRetry + "/" + maxRetries + "): " + e.getMessage());
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
