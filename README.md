# Cluster didatico com Java Sockets

O coordenador tem uma interface visual com duas etapas: primeiro soma os inteiros de `1` ate `N` no proprio servidor e mostra o tempo; depois divide a mesma soma em duas faixas. Dois clientes independentes calculam um subtotal cada e devolvem os resultados pela rede. O coordenador combina os subtotais, confere os resultados e mostra o tempo distribuido, a diferenca percentual e o fator de aceleracao ou lentidao.

Ao receber os resultados dos dois clientes, o painel abre uma janela com os dois métodos, as faixas atribuídas a cada nó, os subtotais, os tempos e a comparação entre as execuções.

O exemplo usa TCP com Java Sockets e um protocolo de texto simples, uma mensagem por linha:

- Cliente para servidor: `HELLO;nome-do-no`
- Servidor para cliente: `TASK;numero-da-tarefa;inicio;fim`
- Cliente para servidor: `RESULT;numero-da-tarefa;subtotal;tempo-do-calculo-em-nanos`

## Requisitos

- Java JDK 17 ou superior instalado em cada computador (o comando `java -version` deve funcionar).
- Os tres computadores conectados a uma rede que permita conexoes TCP entre eles.
- O computador do coordenador deve aceitar conexoes de entrada na porta TCP `5000`.

## Compilar

Abra o PowerShell nesta pasta e execute:

```powershell
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out src\cluster\Coordenador.java src\cluster\Cliente.java
```

Copie a pasta `out` atualizada para os dois computadores clientes. Nao e necessario instalar bibliotecas externas.

## Executar em tres computadores

1. No computador que sera o servidor, descubra o IPv4 da rede local com `ipconfig`.
2. No servidor, execute:

   ```powershell
   java -cp out cluster.Coordenador
   ```

   A janela do painel abre e inicia o servidor TCP na porta `5000`. O valor inicial de `N` pode ser alterado no campo da primeira etapa.
3. Em cada um dos outros dois computadores, execute, substituindo o IP pelo IPv4 do servidor:

   ```powershell
   java -cp out cluster.Cliente 192.168.1.10 no-1
   ```

   No terceiro computador, use `no-2`. O IP de exemplo deve ser substituido pelo endereco real.
4. No painel, clique em **Executar no servidor**. Quando os dois nós aparecerem como conectados, clique em **Dividir entre os 2 clientes**. Observe os intervalos diferentes nos clientes e os subtotais reunidos no servidor.

Os clientes devem conseguir alcancar o servidor pela rede. Se o Firewall do Windows bloquear a conexao, permita o Java ou uma regra de entrada TCP para a porta `5000` na rede local usada para a aula.

## Demonstracao em um computador

Para apresentar o fluxo sem tres maquinas, abra tres terminais na pasta do projeto. Inicie o coordenador no primeiro e, nos outros dois, execute:

```powershell
java -cp out cluster.Cliente 127.0.0.1 no-1
java -cp out cluster.Cliente 127.0.0.1 no-2
```

Isso demonstra o protocolo cliente-servidor. O painel precisa concluir a execução local antes de habilitar a etapa distribuída; quando os dois clientes estiverem conectados, clique no botão de distribuição. Para demonstrar um cluster com computadores independentes, execute os clientes em máquinas diferentes.

## O que explicar durante a aula

- **Nos:** cada computador cliente funciona como um no com processador e memoria proprios.
- **Decomposicao:** o intervalo e dividido em duas tarefas independentes.
- **Comunicacao:** o servidor envia os limites de cada tarefa; cada cliente devolve um subtotal via TCP.
- **Coordenacao e reducao:** o servidor espera os dois resultados, soma os subtotais e confere o total.
- **Custo e limite:** o tempo de rede, a conexao, a espera pelo no mais lento e o trabalho feito pelo servidor tambem contam. Com uma tarefa pequena, distribuir pode ser mais lento. O servidor central e um ponto unico de falha.

Este prototipo e voltado ao ensino; nao implementa tolerancia a falhas, seguranca de rede nem balanceamento dinamico. Tambem nao e uma implementacao de MPI: sockets mostram os fundamentos de troca de mensagens, enquanto MPI e uma interface padronizada para comunicacao paralela distribuida.

