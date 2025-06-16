package puc.paralela;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * O Nó Central recebe dados processados do Nó de Borda e os "armazena".
 * Em um sistema real, aqui haveria a integração com um banco de dados distribuído
 **/

public class Central {
    private static final int CENTRAL_NODE_TCP_PORT = 12347;

    private static final List<JSONObject> DATABASE = Collections.synchronizedList(new LinkedList<>());

    private static ExecutorService clientHandlerPool = Executors.newFixedThreadPool(5); // Pool para lidar com conexões de nó de borda

    private static final double FARM_CENTER_LAT = -19.92;
    private static final double FARM_CENTER_LON = -43.93;

    // Raio de desvio máximo em graus para simular uma área de 2 hectares.
    private static final double MAX_LAT_DEVIATION = 0.0008; // Desvio máximo em latitude (graus)
    private static final double MAX_LON_DEVIATION = 0.0008; // Desvio máximo em longitude (graus)

    public static void main(String[] args) {
        startCentralServer();
        // O main thread pode esperar indefinidamente
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("Central main thread interrompida.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Inicia o servidor TCP para receber dados dos Nós de Borda.
     */
    private static void startCentralServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(CENTRAL_NODE_TCP_PORT)) {
                System.out.println("Nó Central ouvindo em TCP Porta " + CENTRAL_NODE_TCP_PORT + " para Nós de Borda.");
                while (true) {
                    Socket clientSocket = serverSocket.accept(); // Aceita uma nova conexão
                    clientHandlerPool.submit(() -> handleBordaConnection(clientSocket));
                }
            } catch (IOException e) {
                System.err.println("Erro no servidor do Nó Central: " + e.getMessage());
                e.printStackTrace();
            } finally {
                clientHandlerPool.shutdown();
            }
        }).start();
    }

    /**
     * Lida com a conexão de um Nó de Borda, recebendo e armazenando os dados.
     * @param clientSocket Socket do Nó de Borda conectado.
     */
    private static void handleBordaConnection(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        System.out.println("Nó Central: Conexão recebida do Nó de Borda " + clientAddress);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                storeData(line);
            }
        } catch (IOException e) {
            System.err.println("Erro ao lidar com a conexão do Nó de Borda " + clientAddress + ": " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar o socket do Nó de Borda: " + e.getMessage());
            }
            System.out.println("Nó Central: Conexão com o Nó de Borda " + clientAddress + " fechada.");
        }
    }

    /**
     * Armazena os dados recebidos no "banco de dados" em memória.
     * @param dataString Dados JSON em formato String.
     */
    private static void storeData(String dataString) {
        try {
            JSONObject data = new JSONObject(dataString);
            DATABASE.add(data);
            System.out.println("Nó Central: Dados do brinco " + data.optString("brinco_id", "N/A") + " armazenados. Total de registros: " + DATABASE.size());

            JSONObject localizacao = data.optJSONObject("localizacao");
            if (localizacao != null) {
                double currentLat = localizacao.optDouble("lat");
                double currentLon = localizacao.optDouble("lon");

                // Verifica se o boi está fora dos limites definidos
                boolean outsideFarm = false;
                if (Math.abs(currentLat - FARM_CENTER_LAT) > MAX_LAT_DEVIATION ||
                    Math.abs(currentLon - FARM_CENTER_LON) > MAX_LON_DEVIATION) {
                    outsideFarm = true;
                }

                if (outsideFarm) {
                    System.out.println("Nó Central: !!! ALERTA DE LIMITE !!! O brinco " + data.optString("brinco_id") +
                                       " está FORA da fazenda! Localização: (" +
                                       String.format("%.6f", currentLat) + ", " + String.format("%.6f", currentLon) + ")");
                } else {
                    System.out.println("Nó Central: Brinco " + data.optString("brinco_id") + " dentro dos limites da fazenda.");
                }
            } else {
                System.out.println("Nó Central: Dados de localização ausentes ou inválidos para o brinco " + data.optString("brinco_id", "N/A"));
            }


            if (data.optBoolean("alerta_febre", false)) {
                System.out.println("Nó Central: ALERTA DE FEBRE CONFIRMADO para " + data.optString("brinco_id") + " (Temp: " + data.optString("temperatura") + "°C)");
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao armazenar dados no Nó Central: " + e.getMessage() + ". Dados: " + dataString);
        }
    }
}
