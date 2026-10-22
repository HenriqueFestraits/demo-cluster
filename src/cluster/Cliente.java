package cluster;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** No de processamento: recebe uma faixa, calcula o subtotal e devolve o resultado. */
public class Cliente {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            System.out.println("Uso: java -cp out cluster.Cliente <IP-servidor> [nome-do-no] [porta]");
            return;
        }
        String host = args[0];
        String nome = args.length >= 2 ? args[1] : "cliente-" + System.getenv("COMPUTERNAME");
        int porta = args.length >= 3 ? Integer.parseInt(args[2]) : 5000;

        try (Socket socket = new Socket(host, porta);
             BufferedReader entrada = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter saida = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            saida.write("HELLO;" + nome);
            saida.newLine();
            saida.flush();
            System.out.printf("%s conectado ao coordenador %s:%d; aguardando tarefa...%n",
                    nome, host, porta);

            String tarefa;
            while ((tarefa = entrada.readLine()) != null) {
                if (tarefa.equals("BUSY")) {
                    System.out.println("O servidor ja esta com dois clientes conectados.");
                    return;
                }
                String[] partes = tarefa.split(";", -1);
                if (partes.length != 4 || !partes[0].equals("TASK")) {
                    throw new IllegalStateException("Tarefa invalida recebida: " + tarefa);
                }

                int numeroTarefa = Integer.parseInt(partes[1]);
                long inicio = Long.parseLong(partes[2]);
                long fim = Long.parseLong(partes[3]);
                long comeco = System.nanoTime();
                long subtotal = somar(inicio, fim);
                long tempoNanos = System.nanoTime() - comeco;
                double tempoMs = tempoNanos / 1_000_000.0;

                System.out.printf("Tarefa %d: somando de %,d ate %,d%n", numeroTarefa, inicio, fim);
                System.out.printf("Subtotal calculado: %,d (%.3f ms)%n", subtotal, tempoMs);
                saida.write("RESULT;" + numeroTarefa + ";" + subtotal + ";" + tempoNanos);
                saida.newLine();
                saida.flush();
                System.out.println("Resultado enviado ao coordenador; aguardando outra tarefa.");
            }
            System.out.println("Conexao encerrada pelo servidor.");
        }
    }

    private static long somar(long inicio, long fim) {
        long total = 0;
        for (long numero = inicio; numero <= fim; numero++) {
            total += numero;
        }
        return total;
    }
}

