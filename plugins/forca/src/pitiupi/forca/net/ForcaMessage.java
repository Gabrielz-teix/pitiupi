package pitiupi.forca.net;

import pitiupi.models.Messages.Message;

/**
 * Mensagem única do jogo da forca. O campo "tipo" diz o que ela é.
 *
 * CONVITE   - quem escolheu a palavra -> quem vai adivinhar (manda só os tracinhos e a dica)
 * OCUPADO   - resposta quando quem recebeu o convite já está jogando
 * LETRA     - quem adivinha -> quem escolheu
 * RESULTADO - quem escolheu -> quem adivinha (como a palavra ficou depois da letra)
 * SAIR      - qualquer um saiu da partida
 *
 * A palavra só vai na mensagem quando o jogo acaba.
 */
public class ForcaMessage extends Message {

    private static final long serialVersionUID = 1L;

    public static final String CONVITE = "CONVITE";
    public static final String OCUPADO = "OCUPADO";
    public static final String LETRA = "LETRA";
    public static final String RESULTADO = "RESULTADO";
    public static final String SAIR = "SAIR";

    private String tipo;
    private String deId;
    private String deNome;
    private String paraId;

    private String dica;
    private String mascara;
    private char letra;
    private int erros;
    private String letrasErradas;
    private String fim;      // "", "GANHOU" ou "PERDEU" (do ponto de vista de quem adivinha)
    private String palavra;  // só preenchida no fim

    public ForcaMessage() {
    }

    public ForcaMessage(String tipo, String deId, String deNome, String paraId) {
        this.tipo = tipo;
        this.deId = deId;
        this.deNome = deNome;
        this.paraId = paraId;
    }

    public String getTipo() { return tipo; }
    public String getDeId() { return deId; }
    public String getDeNome() { return deNome; }
    public String getParaId() { return paraId; }

    public String getDica() { return dica; }
    public void setDica(String dica) { this.dica = dica; }

    public String getMascara() { return mascara; }
    public void setMascara(String mascara) { this.mascara = mascara; }

    public char getLetra() { return letra; }
    public void setLetra(char letra) { this.letra = letra; }

    public int getErros() { return erros; }
    public void setErros(int erros) { this.erros = erros; }

    public String getLetrasErradas() { return letrasErradas; }
    public void setLetrasErradas(String letrasErradas) { this.letrasErradas = letrasErradas; }

    public String getFim() { return fim; }
    public void setFim(String fim) { this.fim = fim; }

    public String getPalavra() { return palavra; }
    public void setPalavra(String palavra) { this.palavra = palavra; }
}
