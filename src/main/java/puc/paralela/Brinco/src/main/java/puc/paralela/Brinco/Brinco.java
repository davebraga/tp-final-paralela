package puc.paralela.Brinco;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

class Brinco{
    private static String GATEWAY_IP;      // Definido via argumento de linha de comando
    private static int GATEWAY_PORT;     // Definido via argumento de linha de comando

    private String brincoId;
    private Random random = new Random();

    // Constantes de limite da fazenda (replicadas do Nó Central para lógica de geração)
    // Usadas para calcular coordenadas que garantam que o brinco esteja fora ou dentro
    private static final double FARM_CENTER_LAT = -19.92;
    private static final double FARM_CENTER_LON = -43.93;
    private static final double MAX_LAT_DEVIATION = 0.0008; // Desvio máximo em latitude (graus)
    private static final double MAX_LON_DEVIATION = 0.0008; // Desvio máximo em longitude (graus)

    public Brinco(String id) {
        this.brincoId = id;
    }

    private JSONObject generateBrincoData() {
        double latitude;
        double longitude;
        
        // 10% de chance de gerar uma localização fora da fazenda para teste
        if (random.nextDouble() < 0.10) { 
            // Gera uma localização claramente fora dos limites
            latitude = FARM_CENTER_LAT + (random.nextBoolean() ? 1 : -1) * (MAX_LAT_DEVIATION + 0.0005 + random.nextDouble() * 0.001);
            longitude = FARM_CENTER_LON + (random.nextBoolean() ? 1 : -1) * (MAX_LON_DEVIATION + 0.0005 + random.nextDouble() * 0.001);
            System.out.println("Brinco " + brincoId + " (SIMULANDO FUGA): Gerando coordenadas FORA da fazenda.");
        } else {
            // Gera uma localização dentro dos limites normais de variação, mas garantindo que seja dentro da fazenda
            latitude = FARM_CENTER_LAT + (random.nextDouble() * (2 * MAX_LAT_DEVIATION) - MAX_LAT_DEVIATION);
            longitude = FARM_CENTER_LON + (random.nextDouble() * (2 * MAX_LON_DEVIATION) - MAX_LON_DEVIATION);
        }

        double temperatura = 38.0 + (random.nextDouble() * 2.0 - 1.0);
        String[] atividades = {"pastando", "descansando", "andando", "correndo"};
        String atividade = atividades[random.nextInt(atividades.length)];

        JSONObject data = new JSONObject();
        data.put("brinco_id", this.brincoId);
        data.put("timestamp", System.currentTimeMillis());
        
        JSONObject localizacao = new JSONObject();
        localizacao.put("lat", latitude);
        localizacao.put("lon", longitude);
        data.put("localizacao", localizacao);
        
        data.put("temperatura", String.format("%.2f", temperatura));
        data.put("atividade", atividade);
        
        return data;
    }

    private void sendDataToGateway(JSONObject data) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(GATEWAY_IP);
            byte[] buffer = data.toString().getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, GATEWAY_PORT);
            socket.send(packet);
            System.out.println("Brinco " + brincoId + " enviou dados para o gateway: " + data.toString());
        } catch (IOException e) {
            System.err.println("Erro ao enviar dados do brinco " + brincoId + " para " + GATEWAY_IP + ":" + GATEWAY_PORT + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java -jar Brinco-1.0-SNAPSHOT-jar-with-dependencies.jar <ID_BRINCO> <IP_GATEWAY> <PORTA_GATEWAY>");
            return;
        }
        String id = args[0];
        GATEWAY_IP = args[1];
        GATEWAY_PORT = Integer.parseInt(args[2]);

        Brinco brinco = new Brinco(id);
        System.out.println("Simulando Brinco " + id + ". Conectando-se ao Gateway em " + GATEWAY_IP + ":" + GATEWAY_PORT + ". Pressione Ctrl+C para parar.");

        while (true) {
            JSONObject data = brinco.generateBrincoData();
            brinco.sendDataToGateway(data);
            try {
                TimeUnit.SECONDS.sleep(brinco.random.nextInt(10) + 5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Simulação do brinco " + id + " interrompida.");
                break;
            }
        }
    }
}
