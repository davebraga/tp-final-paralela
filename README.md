# **Sistema Distribuído de Monitoramento e Rastreamento de Gado**

## **Visão Geral**

Este projeto demonstra um sistema distribuído para monitoramento e rastreamento de gado, simulando a coleta de dados de brincos eletrônicos e o processamento desses dados em uma arquitetura de múltiplos nós. O objetivo é explorar conceitos de computação distribuída, como comunicação entre processos, paralelismo, gerenciamento de filas e simulação de tolerância a falhas.  
O sistema é composto por quatro módulos principais, cada um representando uma camada na cadeia de processamento de dados:

1. **Brinco Simulado:** Simula o dispositivo de campo acoplado ao animal.  
2. **Gateway:** Coleta dados dos brincos próximos e os encaminha.  
3. **Nó de Borda:** Realiza pré-processamento e detecção inicial de anomalias.  
4. **Nó Central:** Armazena os dados, executa análises complexas e dispara alertas finais.

Para simular um ambiente de rede mais realista e permitir a execução dos nós em máquinas independentes, o projeto utiliza ngrok para expor as portas dos nós que atuam como servidores.

## **Arquitetura do Sistema**

A arquitetura do sistema segue um fluxo hierárquico de dados:  
Brinco Simulado (N)  
     ↓ (UDP)  
Gateway (M)  ───► Nó de Borda (O)  
                      ↓ (TCP)  
                     Nó Central (P)

* N, M, O, P representam diferentes portas e túneis ngrok (ou localhost para testes locais).  
* Cada seta indica a direção do fluxo de dados e o protocolo de comunicação.

## **Funcionalidades dos Componentes**

### **1\. Brinco Simulado (brinco-simulado/BrincoSimulado.java)**

* **Função:** Simula um brinco eletrônico acoplado a um boi. Gera dados aleatórios de localização (latitude, longitude), temperatura e atividade.  
* **Comunicação:** Envia dados via UDP para um Gateway.  
* **Recurso de Teste:** Inclui uma probabilidade (10%) de gerar coordenadas intencionalmente fora dos limites da fazenda para testar a funcionalidade de alerta do Nó Central.

### **2\. Gateway (gateway/Gateway.java)**

* **Função:** Atua como um ponto de coleta de dados de múltiplos brincos em uma área específica. Enfileira os dados recebidos.  
* **Comunicação:**  
  * Recebe dados dos Brincos Simulados via **UDP** (porta local 12347).  
  * Envia dados para o Nó de Borda via **TCP** (porta remota configurável).  
* **Tolerância a Falhas:** Possui lógica básica de retentativa para reconexão ao Nó de Borda em caso de falha de comunicação.

### **3\. Nó de Borda (no-borda/NoBorda.java)**

* **Função:** Recebe dados dos Gateways, realiza um pré-processamento leve (como detecção de anomalias simples) e encaminha para o Nó Central. Um exemplo de anomalia é a detecção de temperatura acima de 39.5°C (febre).  
* **Comunicação:**  
  * Recebe dados dos Gateways via **TCP** (porta local 12346).  
  * Envia dados para o Nó Central via **TCP** (porta remota configurável).  
* **Paralelismo:** Utiliza um ExecutorService para lidar com múltiplas conexões de Gateway simultaneamente.  
* **Tolerância a Falhas:** Possui lógica básica de retentativa para reconexão ao Nó Central.

### **4\. Nó Central (no-central/NoCentral.java)**

* **Função:** O ponto final de agregação e análise de dados. "Armazena" os dados recebidos (em memória para esta simulação) e executa análises de alto nível.  
* **Comunicação:** Recebe dados dos Nós de Borda via **TCP** (porta local 12345).  
* **Análises:**  
  * **Alerta de Febre:** Confirma alertas de febre originados no Nó de Borda.  
  * **Alerta de Limite da Fazenda:** Verifica se a localização do boi (com base em coordenadas pré-definidas da fazenda e um raio de desvio) está fora dos limites e dispara um alerta.  
* **Paralelismo:** Utiliza um ExecutorService para lidar com múltiplas conexões de Nó de Borda.

## **Requisitos**

* **Java Development Kit (JDK):** Versão 17 ou superior (configurável no pom.xml).  
* **Apache Maven:** Ferramenta de gerenciamento de projeto e build.  
* **ngrok:** Ferramenta para criar túneis seguros do seu localhost para a internet pública. Baixe em [ngrok.com](https://ngrok.com/).  
* **Acesso à Internet:** Necessário para o ngrok e para baixar as dependências do Maven.

## **Como Executar o Projeto**

Cada módulo (Central, Borda, Gateway, Brinco) deve ser tratado como um projeto Maven separado.

### **1\. Estrutura do Projeto**

Os diretórios estão organizados da seguinte forma:

src/main/java  
├── Central/  
│   ├── pom.xml  
│   └── src/main/java/Central.java  
├── Borda/  
│   ├── pom.xml  
│   └── src/main/java/Borda.java  
├── Gateway/  
│   ├── pom.xml  
│   └── src/main/java/Gateway.java  
└── Brinco/  
    ├── pom.xml  
    └── src/main/java/Brinco.java

### **2\. Configuração do pom.xml**

Certifique-se de que cada diretório de módulo (Central, Borda, Gateway, Brinco) contenha seu respectivo arquivo pom.xml conforme fornecido nas instruções anteriores.  
**Importante:** Verifique e ajuste a versão do maven.compiler.source e maven.compiler.target no pom.xml para corresponder à versão do seu JDK instalado (ex: 17).

### **3\. Compilar Cada Módulo**

Para cada módulo, navegue até o seu diretório raiz (ex: seu\_projeto\_distribuido/no-central/) no terminal e execute o comando Maven para compilar e empacotar:  

>mvn clean package

Este comando criará um arquivo JAR executável (\*-jar-with-dependencies.jar) na pasta target/ de cada módulo.

### **4\. Ordem de Execução (com ngrok)**

Você precisará de vários terminais abertos para este processo.

#### **a. Iniciar Nó Central**

1. **Execute o Nó Central:**  
   >cd seu\_projeto\_distribuido/Central/  

   >java -jar target/Central-1.0-SNAPSHOT-jar-with-dependencies.jar

   O Nó Central começará a ouvir na porta 12345\.  
2. Exponha o Nó Central com ngrok:  
   Em um novo terminal, execute:  

   >ngrok tcp 12345

   Anote o endereço Forwarding que o ngrok exibir 

   Ex:
   >tcp://0.tcp.ngrok.io:XXXXX). 
   
   Você usará 0.tcp.ngrok.io como \<IP\_NOCENTRAL\> e XXXXX como \<PORTA\_NOCENTRAL\>.

#### **b. Iniciar Nó de Borda**

1. Execute o Nó de Borda:  
   Em um novo terminal, navegue até a pasta Borda/ e execute, usando o endereço ngrok do Nó Central:  

   >cd seu\_projeto\_distribuido/Borda/ 

   >java -jar target/Borda-1.0-SNAPSHOT-jar-with-dependencies.jar \<IP\_NOCENTRAL\> \<PORTA\_NOCENTRAL\>

   Exemplo: 
   
   >java -jar target/Borda-1.0-SNAPSHOT-jar-with-dependencies.jar 0.tcp.ngrok.io 12345 
   
   (substitua pela sua porta ngrok).

   O Nó de Borda começará a ouvir localmente na porta 12346 e se conectará ao Nó Central via ngrok.  

2. Exponha o Nó de Borda com ngrok:  
   Em um novo terminal, execute: 

   >ngrok tcp 12346

   Anote o novo endereço Forwarding 

   Ex: 
   >tcp://0.tcp.ngrok.io:YYYYY). 
   Você usará 0.tcp.ngrok.io como \<IP\_NOBORDA\> e YYYYY como \<PORTA\_NOBORDA\>.

#### **c. Iniciar Gateway**

1. Execute o Gateway:  
   Em um novo terminal, navegue até a pasta Gateway/ e execute, usando o endereço ngrok do Nó de Borda:  
   cd seu\_projeto\_distribuido/Gateway/  
   java -jar target/Gateway-1.0-SNAPSHOT-jar-with-dependencies.jar \<IP\_NOBORDA\> \<PORTA\_NOBORDA\>

   Exemplo: 
   >java -jar target/Gateway-1.0-SNAPSHOT-jar-with-dependencies.jar 0.tcp.ngrok.io 23456 
   (substitua pela sua porta ngrok).

   O Gateway começará a ouvir localmente na porta 12347 (UDP) e se conectará ao Nó de Borda via ngrok.  
2. Exponha o Gateway com ngrok:  
   Em um novo terminal, execute:

   >ngrok tcp 12347

   Anote o novo endereço Forwarding (ex: tcp://0.tcp.ngrok.io:ZZZZZ). Você usará 0.tcp.ngrok.io como \<IP\_GATEWAY\> e ZZZZZ como \<PORTA\_GATEWAY\>.

#### **d. Iniciar Brinco Simulado(s)**

1. Execute o Brinco Simulado:  
   Em um novo terminal, navegue até a pasta brinco-simulado/ e execute, usando um ID para o brinco e o endereço ngrok do Gateway:  

   >cd seu\_projeto\_distribuido/brinco-simulado/ 
   java -jar target/brinco-simulado-10-SNAPSHOT-jar-with-dependencies.jar \<ID\_BRINCO\> \<IP\_GATEWAY\> \<PORTA\_GATEWAY\>

   Exemplo: 
   >java \-jar target/brinco-simulado-1.0-SNAPSHOT-jar-with-dependencies.jar VACA001 0.tcp.ngrok.io 34567 

   (substitua pela sua porta ngrok).
     
   Você pode abrir múltiplos terminais para simular vários brincos (com IDs diferentes, ex: VACA002, BOI001).

Observe os logs em todos os terminais para ver o fluxo de dados e os alertas sendo gerados.

## **Considerações sobre o Projeto**

* **Simulação vs. Realidade:** Este projeto utiliza sockets diretos e filas em memória para simulação. Em um sistema de produção, seriam utilizados protocolos mais robustos (gRPC, HTTP/2), message brokers (Kafka, RabbitMQ) e bancos de dados distribuídos (Cassandra, MongoDB, etc.) para garantir escalabilidade, durabilidade e consistência.  
* **Tolerância a Falhas:** A lógica de retentativas básica é implementada para simular a resiliência a falhas temporárias de rede ou indisponibilidade de nós.  
* **Escalabilidade:** A arquitetura modular permite a adição de mais Gateways e Nós de Borda para escalar a capacidade de ingestão e pré-processamento de dados. Mais Nós Centrais poderiam ser adicionados com um sistema de banco de dados distribuído real.  
* **Geolocalização:** A detecção de limite de fazenda é uma simplificação (cálculo retangular). Em um sistema real, seriam usados cálculos de distância geodésica mais precisos para coordenadas de latitude/longitude.

## **Melhorias Futuras**

* **Uso de Message Broker:** Integrar Apache Kafka ou RabbitMQ para comunicação assíncrona entre módulos, melhorando a durabilidade das mensagens e a resiliência a falhas.  
* **Banco de Dados Distribuído:** Substituir o armazenamento em memória do Nó Central por um banco de dados NoSQL distribuído (ex: Apache Cassandra para dados de séries temporais) para persistência e escalabilidade.  
* **Interface de Usuário:** Desenvolver uma interface web para visualizar a localização dos bois em um mapa, dados de temperatura e alertas em tempo real.  
* **Algoritmos de ML:** Implementar algoritmos de Machine Learning mais avançados no Nó Central para prever doenças, analisar padrões de comportamento ou otimizar o manejo do rebanho.  
* **Containerização:** Empacotar cada nó em um container Docker para facilitar o deployment em ambientes distribuídos (Kubernetes, Docker Swarm).  
* **Mecanismos de Consistência:** Explorar diferentes modelos de consistência em caso de replicação de dados entre nós.  
* **Monitoramento:** Adicionar ferramentas de monitoramento e logging distribuído (ex: ELK Stack, Prometheus/Grafana) para observar o desempenho e a saúde do sistema.

## **Desenvolvedores**
- Camila Lopes ([@camilamlopes](https://github.com/camilamlopes/))
- Davi Braga ([@davebraga](https://github.com/davebraga/))
