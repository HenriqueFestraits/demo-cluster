package cluster;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Painel visual e coordenador do cluster didatico. */
public class Coordenador extends JFrame {
    private static final int PORTA_PADRAO = 5000;
    private static final int NUMERO_DE_NOS = 2;
    private static final long LIMITE_PADRAO = 40_000_000L;
    private static final Color FUNDO = new Color(242, 246, 251);
    private static final Color AZUL = new Color(35, 91, 164);
    private static final Color VERDE = new Color(36, 137, 91);
    private static final Color CINZA = new Color(110, 123, 140);
    private static final Color TEXTO = new Color(34, 47, 62);

    private final ExecutorService tarefas = Executors.newCachedThreadPool();
    private final Object travaNos = new Object();
    private final No[] nos = new No[NUMERO_DE_NOS];
    private final JLabel[] nomesNos = new JLabel[NUMERO_DE_NOS];
    private final JLabel[] estadosNos = new JLabel[NUMERO_DE_NOS];
    private final JLabel estadoServidor = new JLabel("Iniciando servidor...");
    private final JLabel resultadoLocal = new JLabel("Ainda não executado");
    private final JLabel tempoLocal = new JLabel("Tempo: --");
    private final JLabel resultadoDistribuido = new JLabel("Aguardando execução local e dois clientes");
    private final JLabel tempoDistribuido = new JLabel("Tempo: --");
    private final JLabel comparacaoTempos = new JLabel("Execute as duas etapas para comparar os tempos.");
    private final JLabel mensagem = new JLabel("Conecte dois computadores clientes à porta 5000.");
    private final JTextField limiteCampo = new JTextField("40.000.000", 12);
    private final JButton executarLocal = new JButton("Executar no servidor");
    private final JButton executarDistribuido = new JButton("Dividir entre os 2 clientes");
    private final JButton botaoServidor = new JButton("Encerrar servidor");
    private final JProgressBar progressoDistribuido = new JProgressBar();

    private volatile ServerSocket servidorSocket;
    private volatile boolean servidorAtivo;
    private boolean execucaoLocalConcluida;
    private boolean executando;
    private long limiteLocalCalculado;
    private long totalLocalCalculado;
    private long tempoLocalNanos;
    private long tempoDistribuidoNanos;

    private static final class No {
        final int posicao;
        final String nome;
        final Socket socket;
        final BufferedReader entrada;
        final BufferedWriter saida;

        No(int posicao, String nome, Socket socket, BufferedReader entrada, BufferedWriter saida) {
            this.posicao = posicao;
            this.nome = nome;
            this.socket = socket;
            this.entrada = entrada;
            this.saida = saida;
        }
    }

    private record Resultado(String nome, long inicio, long fim, long subtotal, long tempoNanos) {}

    public Coordenador() {
        super("Cluster didático | Painel do servidor");
        montarInterface();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                encerrar();
                dispose();
            }
        });
        iniciarServidor(PORTA_PADRAO);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Coordenador().setVisible(true));
    }

    private void montarInterface() {
        setMinimumSize(new Dimension(830, 650));
        setSize(980, 720);
        setLocationRelativeTo(null);
        getContentPane().setBackground(FUNDO);
        setLayout(new BorderLayout(0, 16));

        JPanel topo = new JPanel(new BorderLayout(12, 8));
        topo.setBackground(new Color(25, 42, 67));
        topo.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
        JLabel titulo = new JLabel("Cluster em ação");
        titulo.setForeground(Color.WHITE);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 28));
        JLabel subtitulo = new JLabel("Compare o cálculo local com o trabalho dividido pela rede");
        subtitulo.setForeground(new Color(207, 221, 239));
        subtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JPanel textosTopo = new JPanel(new GridLayout(2, 1, 0, 5));
        textosTopo.setOpaque(false);
        textosTopo.add(titulo);
        textosTopo.add(subtitulo);
        estadoServidor.setForeground(new Color(193, 231, 207));
        estadoServidor.setFont(new Font("Segoe UI", Font.BOLD, 13));
        topo.add(textosTopo, BorderLayout.CENTER);
        topo.add(estadoServidor, BorderLayout.EAST);
        add(topo, BorderLayout.NORTH);

        JPanel conteudo = new JPanel(new BorderLayout(0, 14));
        conteudo.setBackground(FUNDO);
        conteudo.setBorder(BorderFactory.createEmptyBorder(0, 24, 20, 24));
        JPanel principal = new JPanel(new GridLayout(1, 2, 14, 0));
        principal.setOpaque(false);
        principal.add(criarCartaoLocal());
        principal.add(criarCartaoDistribuido());

        JPanel rodape = new JPanel(new BorderLayout(0, 10));
        rodape.setOpaque(false);
        rodape.add(criarCartaoNos(), BorderLayout.CENTER);
        mensagem.setForeground(CINZA);
        mensagem.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        mensagem.setBorder(BorderFactory.createEmptyBorder(2, 3, 0, 3));
        rodape.add(mensagem, BorderLayout.SOUTH);

        conteudo.add(principal, BorderLayout.CENTER);
        conteudo.add(rodape, BorderLayout.SOUTH);
        add(conteudo, BorderLayout.CENTER);

        executarLocal.addActionListener(e -> executarLocalmente());
        executarDistribuido.addActionListener(e -> executarDistribuido());
        botaoServidor.addActionListener(e -> encerrar());
        atualizarBotoes();
    }

    private JPanel criarCartaoLocal() {
        JPanel cartao = cartao();
        cartao.setLayout(new BorderLayout(0, 16));
        cartao.add(cabecalhoCartao("1", "Somente no servidor", "Uma máquina resolve o problema inteiro"),
                BorderLayout.NORTH);

        JPanel corpo = new JPanel();
        corpo.setLayout(new BoxLayout(corpo, BoxLayout.Y_AXIS));
        corpo.setOpaque(false);
        corpo.add(rotulo("Problema: somar os inteiros de 1 até N", Font.PLAIN, 14));
        corpo.add(Box.createVerticalStrut(16));
        JLabel nLabel = rotulo("Valor de N", Font.BOLD, 13);
        corpo.add(nLabel);
        corpo.add(Box.createVerticalStrut(6));
        limiteCampo.setEditable(true);
        limiteCampo.setEnabled(true);
        limiteCampo.setFocusable(true);
        limiteCampo.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        limiteCampo.setMargin(new Insets(7, 9, 7, 9));
        limiteCampo.setForeground(TEXTO);
        limiteCampo.setBackground(Color.WHITE);
        limiteCampo.setCaretColor(AZUL);
        limiteCampo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(178, 192, 209)),
                BorderFactory.createEmptyBorder(3, 4, 3, 4)));
        limiteCampo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        limiteCampo.setToolTipText("Digite um número inteiro entre 2 e 1.000.000.000");
        corpo.add(limiteCampo);
        corpo.add(Box.createVerticalStrut(14));
        JButton botaoLocal = estilizarBotao(executarLocal, AZUL);
        botaoLocal.setAlignmentX(LEFT_ALIGNMENT);
        botaoLocal.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        corpo.add(botaoLocal);
        corpo.add(Box.createVerticalStrut(18));
        corpo.add(rotulo("Resultado local", Font.BOLD, 12));
        corpo.add(Box.createVerticalStrut(3));
        resultadoLocal.setFont(new Font("Segoe UI", Font.BOLD, 22));
        resultadoLocal.setForeground(TEXTO);
        corpo.add(resultadoLocal);
        corpo.add(Box.createVerticalStrut(2));
        tempoLocal.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        tempoLocal.setForeground(CINZA);
        corpo.add(tempoLocal);
        cartao.add(corpo, BorderLayout.CENTER);
        JLabel dica = rotulo("Use esta etapa como referência antes de distribuir o trabalho.", Font.PLAIN, 12);
        cartao.add(dica, BorderLayout.SOUTH);
        return cartao;
    }

    private JPanel criarCartaoDistribuido() {
        JPanel cartao = cartao();
        cartao.setLayout(new BorderLayout(0, 16));
        cartao.add(cabecalhoCartao("2", "Dois nós trabalhando", "O servidor divide e reúne os subtotais"),
                BorderLayout.NORTH);

        JPanel corpo = new JPanel();
        corpo.setLayout(new BoxLayout(corpo, BoxLayout.Y_AXIS));
        corpo.setOpaque(false);
        corpo.add(rotulo("Cada cliente recebe uma faixa diferente de números.", Font.PLAIN, 14));
        corpo.add(Box.createVerticalStrut(20));
        JButton botaoDistribuido = estilizarBotao(executarDistribuido, VERDE);
        botaoDistribuido.setAlignmentX(LEFT_ALIGNMENT);
        botaoDistribuido.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        corpo.add(botaoDistribuido);
        corpo.add(Box.createVerticalStrut(18));
        corpo.add(rotulo("Total reunido pelo servidor", Font.BOLD, 12));
        corpo.add(Box.createVerticalStrut(3));
        resultadoDistribuido.setFont(new Font("Segoe UI", Font.BOLD, 22));
        resultadoDistribuido.setForeground(TEXTO);
        corpo.add(resultadoDistribuido);
        corpo.add(Box.createVerticalStrut(2));
        tempoDistribuido.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        tempoDistribuido.setForeground(CINZA);
        corpo.add(tempoDistribuido);
        corpo.add(Box.createVerticalStrut(12));
        comparacaoTempos.setFont(new Font("Segoe UI", Font.BOLD, 13));
        comparacaoTempos.setForeground(CINZA);
        comparacaoTempos.setAlignmentX(LEFT_ALIGNMENT);
        corpo.add(comparacaoTempos);
        corpo.add(Box.createVerticalStrut(12));
        progressoDistribuido.setIndeterminate(false);
        progressoDistribuido.setStringPainted(true);
        progressoDistribuido.setString("Pronto quando os 2 nós estiverem conectados");
        progressoDistribuido.setAlignmentX(LEFT_ALIGNMENT);
        progressoDistribuido.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        corpo.add(progressoDistribuido);
        cartao.add(corpo, BorderLayout.CENTER);
        JLabel dica = rotulo("O tempo distribuído inclui envio, rede e espera pelos nós.", Font.PLAIN, 12);
        cartao.add(dica, BorderLayout.SOUTH);
        return cartao;
    }

    private JPanel criarCartaoNos() {
        JPanel cartao = cartao();
        cartao.setLayout(new BorderLayout(0, 12));
        cartao.add(rotulo("Nós de processamento", Font.BOLD, 17), BorderLayout.NORTH);
        JPanel lista = new JPanel(new GridLayout(1, NUMERO_DE_NOS, 12, 0));
        lista.setOpaque(false);
        for (int i = 0; i < NUMERO_DE_NOS; i++) {
            JPanel no = new JPanel(new BorderLayout(0, 7));
            no.setBackground(new Color(247, 249, 252));
            no.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(225, 232, 240)),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)));
            nomesNos[i] = rotulo("Cliente " + (i + 1), Font.BOLD, 14);
            estadosNos[i] = rotulo("●  Aguardando conexão", Font.PLAIN, 13);
            estadosNos[i].setForeground(CINZA);
            no.add(nomesNos[i], BorderLayout.NORTH);
            no.add(estadosNos[i], BorderLayout.CENTER);
            lista.add(no);
        }
        cartao.add(lista, BorderLayout.CENTER);
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        JLabel nota = rotulo("Servidor TCP na porta " + PORTA_PADRAO, Font.PLAIN, 12);
        rodape.add(nota, BorderLayout.WEST);
        botaoServidor.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        rodape.add(botaoServidor, BorderLayout.EAST);
        cartao.add(rodape, BorderLayout.SOUTH);
        return cartao;
    }

    private JPanel cabecalhoCartao(String numero, String titulo, String descricao) {
        JPanel painel = new JPanel(new BorderLayout(10, 3));
        painel.setOpaque(false);
        JLabel bolha = new JLabel(numero, SwingConstants.CENTER);
        bolha.setOpaque(true);
        bolha.setBackground(new Color(231, 239, 249));
        bolha.setForeground(AZUL);
        bolha.setFont(new Font("Segoe UI", Font.BOLD, 18));
        bolha.setPreferredSize(new Dimension(38, 38));
        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 3));
        textos.setOpaque(false);
        textos.add(rotulo(titulo, Font.BOLD, 18));
        textos.add(rotulo(descricao, Font.PLAIN, 12));
        painel.add(bolha, BorderLayout.WEST);
        painel.add(textos, BorderLayout.CENTER);
        return painel;
    }

    private JPanel cartao() {
        JPanel painel = new JPanel();
        painel.setBackground(Color.WHITE);
        painel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 230, 239)),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        return painel;
    }

    private JLabel rotulo(String texto, int estilo, int tamanho) {
        JLabel label = new JLabel(texto);
        label.setForeground(TEXTO);
        label.setFont(new Font("Segoe UI", estilo, tamanho));
        return label;
    }

    private JButton estilizarBotao(JButton botao, Color cor) {
        botao.setOpaque(true);
        botao.setBackground(cor);
        botao.setForeground(Color.WHITE);
        botao.setFont(new Font("Segoe UI", Font.BOLD, 14));
        botao.setFocusPainted(false);
        botao.setBorder(BorderFactory.createEmptyBorder(11, 14, 11, 14));
        return botao;
    }

    private void iniciarServidor(int porta) {
        tarefas.submit(() -> {
            try {
                ServerSocket socketServidor = new ServerSocket(porta);
                servidorSocket = socketServidor;
                servidorAtivo = true;
                SwingUtilities.invokeLater(() -> {
                    estadoServidor.setText("●  Servidor ativo · porta " + porta);
                    estadoServidor.setForeground(new Color(193, 231, 207));
                    mensagem.setText("Aguardando os dois programas Cliente.java se conectarem.");
                    atualizarBotoes();
                });
                while (servidorAtivo) {
                    Socket socketCliente = socketServidor.accept();
                    socketCliente.setSoTimeout(120_000);
                    registrarCliente(socketCliente);
                }
            } catch (IOException erro) {
                if (servidorAtivo) {
                    SwingUtilities.invokeLater(() -> {
                        estadoServidor.setText("●  Erro ao iniciar o servidor");
                        estadoServidor.setForeground(new Color(255, 204, 153));
                        mensagem.setText("Não foi possível abrir a porta " + porta + ": " + erro.getMessage());
                    });
                    servidorAtivo = false;
                    SwingUtilities.invokeLater(this::atualizarBotoes);
                }
            }
        });
    }

    private void registrarCliente(Socket socket) {
        tarefas.submit(() -> {
            try {
                BufferedReader leitor = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter escritor = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                String hello = leitor.readLine();
                if (hello == null || !hello.startsWith("HELLO;")) {
                    socket.close();
                    return;
                }
                String nome = hello.substring("HELLO;".length());
                int posicao = -1;
                synchronized (travaNos) {
                    for (int i = 0; i < nos.length; i++) {
                        if (nos[i] == null) {
                            posicao = i;
                            nos[i] = new No(i, nome, socket, leitor, escritor);
                            break;
                        }
                    }
                }
                if (posicao < 0) {
                    escritor.write("BUSY");
                    escritor.newLine();
                    escritor.flush();
                    socket.close();
                    return;
                }
                int slot = posicao;
                SwingUtilities.invokeLater(() -> {
                    nomesNos[slot].setText(nome + " · " + socket.getInetAddress().getHostAddress());
                    estadosNos[slot].setText("●  Conectado · aguardando tarefa");
                    estadosNos[slot].setForeground(VERDE);
                    mensagem.setText("Nó " + (slot + 1) + " conectado. Identificado como " + nome + ".");
                    atualizarBotoes();
                });
            } catch (IOException erro) {
                try { socket.close(); } catch (IOException ignored) { }
            }
        });
    }

    private void executarLocalmente() {
        long limite;
        try {
            limite = Long.parseLong(limiteCampo.getText().replace(".", "").replace(",", "").trim());
            if (limite < 2 || limite > 1_000_000_000L) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException erro) {
            mensagem.setText("Informe N como inteiro entre 2 e 1.000.000.000.");
            limiteCampo.requestFocusInWindow();
            return;
        }

        executando = true;
        atualizarBotoes();
        resultadoLocal.setText("Calculando...");
        tempoLocal.setText("Tempo: medindo");
        tempoLocalNanos = 0;
        tempoDistribuidoNanos = 0;
        resultadoDistribuido.setText("Aguardando nova execução distribuída");
        tempoDistribuido.setText("Tempo: --");
        comparacaoTempos.setText("Execute as duas etapas para comparar os tempos.");
        comparacaoTempos.setForeground(CINZA);
        mensagem.setText("O servidor está somando todos os números de 1 até " + formatar(limite) + ".");
        tarefas.submit(() -> {
            long comeco = System.nanoTime();
            long total = somar(1, limite);
            long duracao = System.nanoTime() - comeco;
            SwingUtilities.invokeLater(() -> {
                resultadoLocal.setText(formatar(total));
                tempoLocalNanos = duracao;
                tempoLocal.setText("Servidor: " + formatarTempo(duracao));
                execucaoLocalConcluida = true;
                limiteLocalCalculado = limite;
                totalLocalCalculado = total;
                executando = false;
                mensagem.setText("Etapa local concluída. Com dois clientes conectados, você pode comparar a versão distribuída.");
                atualizarBotoes();
            });
        });
    }

    private void executarDistribuido() {
        long limite;
        try {
            limite = Long.parseLong(limiteCampo.getText().replace(".", "").replace(",", "").trim());
            if (limite < 2 || limite > 1_000_000_000L) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException erro) {
            mensagem.setText("Informe N como inteiro entre 2 e 1.000.000.000.");
            return;
        }
        if (!execucaoLocalConcluida || limite != limiteLocalCalculado) {
            mensagem.setText("Execute primeiro a etapa local usando o mesmo valor de N.");
            return;
        }

        No[] conectados;
        synchronized (travaNos) {
            conectados = nos.clone();
        }
        if (conectados[0] == null || conectados[1] == null) {
            mensagem.setText("Conecte os dois clientes antes de iniciar a distribuição.");
            atualizarBotoes();
            return;
        }

        executando = true;
        atualizarBotoes();
        resultadoDistribuido.setText("Processando...");
        tempoDistribuido.setText("Tempo distribuído: medindo");
        tempoDistribuidoNanos = 0;
        comparacaoTempos.setText("Comparando após receber os dois subtotais...");
        comparacaoTempos.setForeground(CINZA);
        progressoDistribuido.setIndeterminate(true);
        progressoDistribuido.setString("Trabalho em andamento nos dois nós...");
        mensagem.setText("O servidor envia uma faixa diferente para cada cliente.");

        tarefas.submit(() -> {
            long comeco = System.nanoTime();
            Future<Resultado> primeiro = tarefas.submit(() -> processar(conectados[0], 1,
                    1, limite / 2));
            Future<Resultado> segundo = tarefas.submit(() -> processar(conectados[1], 2,
                    limite / 2 + 1, limite));
            try {
                Resultado r1 = primeiro.get();
                Resultado r2 = segundo.get();
                long total = r1.subtotal() + r2.subtotal();
                long duracao = System.nanoTime() - comeco;
                SwingUtilities.invokeLater(() -> {
                    resultadoDistribuido.setText(formatar(total));
                    tempoDistribuidoNanos = duracao;
                    tempoDistribuido.setText("Distribuído: " + formatarTempo(duracao));
                    progressoDistribuido.setIndeterminate(false);
                    boolean resultadosIguais = total == totalLocalCalculado;
                    progressoDistribuido.setString(resultadosIguais
                            ? "Conferência concluída: os totais são iguais"
                            : "Os totais diferem; confira as faixas e os resultados");
                    mostrarComparacao(resultadosIguais);
                    atualizarEstadoNo(conectados[0].posicao,
                            "●  Subtotal: " + formatar(r1.subtotal()), VERDE);
                    atualizarEstadoNo(conectados[1].posicao,
                            "●  Subtotal: " + formatar(r2.subtotal()), VERDE);
                    executando = false;
                    mensagem.setText("O coordenador reuniu os dois subtotais. Compare os tempos e considere o custo da rede.");
                    atualizarBotoes();
                    mostrarJanelaResultado(limite, totalLocalCalculado, tempoLocalNanos,
                            r1, r2, total, duracao, resultadosIguais);
                });
            } catch (Exception erro) {
                SwingUtilities.invokeLater(() -> {
                    progressoDistribuido.setIndeterminate(false);
                    progressoDistribuido.setString("Falha ao receber uma resposta");
                    resultadoDistribuido.setText("Falha na execução");
                    executando = false;
                    mensagem.setText("Erro na tarefa distribuída: " + causa(erro));
                    atualizarBotoes();
                });
            }
        });

        atualizarEstadoNo(conectados[0].posicao,
                "●  Calculando 1–" + formatar(limite / 2), AZUL);
        atualizarEstadoNo(conectados[1].posicao,
                "●  Calculando " + formatar(limite / 2 + 1) + "–" + formatar(limite), AZUL);
    }

    private Resultado processar(No no, int numero, long inicio, long fim) throws IOException {
        try {
            no.saida.write("TASK;" + numero + ";" + inicio + ";" + fim);
            no.saida.newLine();
            no.saida.flush();
            String linha = no.entrada.readLine();
            if (linha == null) {
                throw new IOException("O cliente " + no.nome + " encerrou a conexão.");
            }
            String[] partes = linha.split(";", -1);
            if (partes.length != 4 || !partes[0].equals("RESULT")
                    || !partes[1].equals(Integer.toString(numero))) {
                throw new IOException("Resposta inválida de " + no.nome + ": " + linha);
            }
            return new Resultado(no.nome, inicio, fim, Long.parseLong(partes[2]), Long.parseLong(partes[3]));
        } catch (IOException erro) {
            desconectar(no, erro.getMessage());
            throw erro;
        }
    }

    private void desconectar(No no, String motivo) {
        synchronized (travaNos) {
            if (nos[no.posicao] == no) {
                nos[no.posicao] = null;
            }
        }
        try { no.socket.close(); } catch (IOException ignored) { }
        SwingUtilities.invokeLater(() -> {
            nomesNos[no.posicao].setText("Cliente " + (no.posicao + 1));
            estadosNos[no.posicao].setText("●  Desconectado");
            estadosNos[no.posicao].setForeground(CINZA);
            mensagem.setText("O nó " + no.nome + " desconectou: " + motivo);
            atualizarBotoes();
        });
    }

    private void atualizarEstadoNo(int slot, String texto, Color cor) {
        SwingUtilities.invokeLater(() -> {
            estadosNos[slot].setText(texto);
            estadosNos[slot].setForeground(cor);
        });
    }

    private void mostrarComparacao(boolean resultadosIguais) {
        if (!resultadosIguais || tempoLocalNanos <= 0 || tempoDistribuidoNanos <= 0) {
            comparacaoTempos.setText("Comparação indisponível: confira os resultados e execute novamente.");
            comparacaoTempos.setForeground(CINZA);
            return;
        }
        double variacao = (tempoLocalNanos - tempoDistribuidoNanos) * 100.0 / tempoLocalNanos;
        if (Math.abs(variacao) < 1.0) {
            comparacaoTempos.setText(String.format(Locale.forLanguageTag("pt-BR"),
                    "Tempos muito próximos (diferença de %.1f%%).", Math.abs(variacao)));
            comparacaoTempos.setForeground(CINZA);
        } else if (variacao > 0) {
            double aceleracao = (double) tempoLocalNanos / tempoDistribuidoNanos;
            comparacaoTempos.setText(String.format(Locale.forLanguageTag("pt-BR"),
                    "Distribuída %.2fx mais rápida · %.1f%% menos tempo", aceleracao, variacao));
            comparacaoTempos.setForeground(VERDE);
        } else {
            double fator = (double) tempoDistribuidoNanos / tempoLocalNanos;
            comparacaoTempos.setText(String.format(Locale.forLanguageTag("pt-BR"),
                    "Servidor %.2fx mais rápido · distribuída levou %.1f%% a mais", fator, -variacao));
            comparacaoTempos.setForeground(new Color(180, 105, 28));
        }
    }

    private void mostrarJanelaResultado(long limite, long totalLocal, long duracaoLocal,
                                         Resultado r1, Resultado r2, long totalDistribuido,
                                         long duracaoDistribuida, boolean resultadosIguais) {
        JDialog janela = new JDialog(this, "Detalhes da execução distribuída", true);
        janela.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        janela.setSize(840, 720);
        janela.setMinimumSize(new Dimension(780, 660));
        janela.setLocationRelativeTo(this);
        janela.getContentPane().setBackground(FUNDO);
        janela.setLayout(new BorderLayout(0, 14));

        JPanel conteudo = new JPanel(new BorderLayout(0, 16));
        conteudo.setBackground(FUNDO);
        conteudo.setBorder(BorderFactory.createEmptyBorder(22, 24, 16, 24));

        JPanel cabecalho = new JPanel(new GridLayout(0, 1, 0, 6));
        cabecalho.setOpaque(false);
        JLabel titulo = rotulo("Resultado da comparação", Font.BOLD, 25);
        JLabel problema = rotulo("Soma dos inteiros de 1 até " + formatar(limite), Font.PLAIN, 15);
        cabecalho.add(titulo);
        cabecalho.add(problema);

        JPanel metodos = new JPanel(new GridLayout(1, 2, 14, 0));
        metodos.setOpaque(false);
        metodos.add(criarCartaoMetodo("1 · Somente no servidor", "Uma máquina processou a faixa completa",
                totalLocal, duracaoLocal, AZUL));
        metodos.add(criarCartaoMetodo("2 · Dois nós", "O servidor enviou tarefas e reuniu os resultados",
                totalDistribuido, duracaoDistribuida, VERDE));

        JPanel separacao = cartao();
        separacao.setLayout(new BorderLayout(0, 12));
        separacao.add(rotulo("Como o trabalho foi dividido", Font.BOLD, 18), BorderLayout.NORTH);
        JPanel linhas = new JPanel(new GridLayout(2, 1, 0, 8));
        linhas.setOpaque(false);
        linhas.add(criarLinhaTarefa(r1));
        linhas.add(criarLinhaTarefa(r2));
        separacao.add(linhas, BorderLayout.CENTER);

        JPanel resumo = cartao();
        resumo.setLayout(new BorderLayout(0, 8));
        JLabel comparacao = rotulo(textoComparacao(resultadosIguais, duracaoLocal, duracaoDistribuida),
                Font.BOLD, 16);
        comparacao.setForeground(corComparacao(resultadosIguais, duracaoLocal, duracaoDistribuida));
        resumo.add(comparacao, BorderLayout.NORTH);
        JLabel explicacao = rotulo("O tempo distribuído total inclui envio, comunicação pela rede e espera pelos dois nós. "
                + "O tempo de cada nó mede somente o cálculo local da sua faixa.", Font.PLAIN, 13);
        resumo.add(explicacao, BorderLayout.CENTER);

        JPanel centro = new JPanel(new BorderLayout(0, 14));
        centro.setOpaque(false);
        centro.add(metodos, BorderLayout.NORTH);
        JPanel inferior = new JPanel(new BorderLayout(0, 14));
        inferior.setOpaque(false);
        inferior.add(separacao, BorderLayout.CENTER);
        inferior.add(resumo, BorderLayout.SOUTH);
        centro.add(inferior, BorderLayout.CENTER);
        conteudo.add(cabecalho, BorderLayout.NORTH);
        conteudo.add(centro, BorderLayout.CENTER);

        JButton fechar = estilizarBotao(new JButton("Fechar"), AZUL);
        fechar.addActionListener(e -> janela.dispose());
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setBackground(FUNDO);
        rodape.setBorder(BorderFactory.createEmptyBorder(0, 24, 18, 24));
        rodape.add(fechar, BorderLayout.EAST);
        JScrollPane rolagem = new JScrollPane(conteudo);
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.getViewport().setBackground(FUNDO);
        janela.add(rolagem, BorderLayout.CENTER);
        janela.add(rodape, BorderLayout.SOUTH);
        janela.setVisible(true);
    }

    private JPanel criarCartaoMetodo(String titulo, String descricao, long resultado, long nanos, Color cor) {
        JPanel painel = cartao();
        painel.setLayout(new BoxLayout(painel, BoxLayout.Y_AXIS));
        JLabel nome = rotulo(titulo, Font.BOLD, 17);
        nome.setForeground(cor);
        painel.add(nome);
        painel.add(Box.createVerticalStrut(7));
        painel.add(rotulo(descricao, Font.PLAIN, 12));
        painel.add(Box.createVerticalStrut(14));
        painel.add(rotulo("Resultado", Font.BOLD, 12));
        painel.add(Box.createVerticalStrut(2));
        painel.add(rotulo(formatar(resultado), Font.BOLD, 24));
        painel.add(Box.createVerticalStrut(7));
        painel.add(rotulo("Tempo total: " + formatarTempo(nanos), Font.BOLD, 14));
        return painel;
    }

    private JPanel criarLinhaTarefa(Resultado resultado) {
        JPanel linha = new JPanel(new BorderLayout(12, 3));
        linha.setBackground(new Color(247, 249, 252));
        linha.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 232, 240)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JPanel identificacao = new JPanel(new GridLayout(2, 1, 0, 4));
        identificacao.setOpaque(false);
        identificacao.add(rotulo(resultado.nome(), Font.BOLD, 14));
        identificacao.add(rotulo("Faixa: " + formatar(resultado.inicio()) + " até "
                + formatar(resultado.fim()), Font.PLAIN, 13));
        JPanel calculo = new JPanel(new GridLayout(2, 1, 0, 4));
        calculo.setOpaque(false);
        calculo.add(rotulo("Subtotal: " + formatar(resultado.subtotal()), Font.BOLD, 14));
        calculo.add(rotulo("Cálculo do nó: " + formatarTempo(resultado.tempoNanos()), Font.PLAIN, 13));
        linha.add(identificacao, BorderLayout.CENTER);
        linha.add(calculo, BorderLayout.EAST);
        return linha;
    }

    private String textoComparacao(boolean resultadosIguais, long local, long distribuido) {
        if (!resultadosIguais || local <= 0 || distribuido <= 0) {
            return "Não foi possível comparar: os resultados precisam ser iguais.";
        }
        double variacao = (local - distribuido) * 100.0 / local;
        if (Math.abs(variacao) < 1.0) {
            return String.format(Locale.forLanguageTag("pt-BR"),
                    "Os tempos ficaram muito próximos (diferença de %.1f%%).", Math.abs(variacao));
        }
        if (variacao > 0) {
            return String.format(Locale.forLanguageTag("pt-BR"),
                    "A execução distribuída foi %.2fx mais rápida (%.1f%% menos tempo).",
                    (double) local / distribuido, variacao);
        }
        return String.format(Locale.forLanguageTag("pt-BR"),
                "A execução no servidor foi %.2fx mais rápida; a distribuída levou %.1f%% a mais.",
                (double) distribuido / local, -variacao);
    }

    private Color corComparacao(boolean resultadosIguais, long local, long distribuido) {
        if (!resultadosIguais || local <= 0 || distribuido <= 0) return CINZA;
        return distribuido < local ? VERDE : new Color(180, 105, 28);
    }

    private void atualizarBotoes() {
        boolean doisNosConectados;
        synchronized (travaNos) {
            doisNosConectados = nos[0] != null && nos[1] != null;
        }
        executarLocal.setEnabled(servidorAtivo && !executando);
        executarDistribuido.setEnabled(servidorAtivo && !executando && execucaoLocalConcluida && doisNosConectados);
        botaoServidor.setEnabled(servidorAtivo);
    }

    private void encerrar() {
        servidorAtivo = false;
        try {
            if (servidorSocket != null) servidorSocket.close();
        } catch (IOException ignored) { }
        synchronized (travaNos) {
            for (int i = 0; i < nos.length; i++) {
                if (nos[i] != null) {
                    try { nos[i].socket.close(); } catch (IOException ignored) { }
                    nos[i] = null;
                }
            }
        }
        tarefas.shutdownNow();
        estadoServidor.setText("●  Servidor encerrado");
        estadoServidor.setForeground(new Color(255, 204, 153));
        mensagem.setText("Servidor encerrado. Feche e abra o programa para iniciar outra sessão.");
        for (int i = 0; i < NUMERO_DE_NOS; i++) {
            nomesNos[i].setText("Cliente " + (i + 1));
            estadosNos[i].setText("●  Desconectado");
            estadosNos[i].setForeground(CINZA);
        }
        atualizarBotoes();
        botaoServidor.setEnabled(false);
    }

    private static String formatar(long valor) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")).format(valor);
    }

    private static String formatarTempo(long nanos) {
        double milissegundos = nanos / 1_000_000.0;
        double segundos = nanos / 1_000_000_000.0;
        return String.format(Locale.forLanguageTag("pt-BR"), "%.2f ms (%.4f s)", milissegundos, segundos);
    }

    private static String causa(Exception erro) {
        Throwable causa = erro.getCause() != null ? erro.getCause() : erro;
        return causa.getMessage() == null ? causa.getClass().getSimpleName() : causa.getMessage();
    }

    private static long somar(long inicio, long fim) {
        long total = 0;
        for (long numero = inicio; numero <= fim; numero++) {
            total += numero;
        }
        return total;
    }
}

