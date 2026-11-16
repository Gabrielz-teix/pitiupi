package pitiupi.forca.control;

import java.text.Normalizer;

/**
 * Regras da forca. Só existe no PC de quem escolheu a palavra.
 */
public class ForcaJogo {

    public static final int MAX_ERROS = 6;

    private final String palavra;
    private String acertadas = "";
    private String erradas = "";

    public ForcaJogo(String palavra) {
        this.palavra = limpar(palavra);
    }

    /** Deixa tudo maiúsculo e tira acentos (MAÇÃ vira MACA). */
    public static String limpar(String texto) {
        String semAcento = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toUpperCase().replaceAll("[^A-Z ]", "").replaceAll(" +", " ");
    }

    /** Uma palavra serve se tiver pelo menos 3 letras. */
    public static boolean palavraValida(String texto) {
        return limpar(texto).replace(" ", "").length() >= 3;
    }

    /** Registra a letra. Devolve false se ela já tinha sido tentada. */
    public boolean tentar(char letra) {
        letra = Character.toUpperCase(letra);
        if (acertadas.indexOf(letra) >= 0 || erradas.indexOf(letra) >= 0) {
            return false;
        }
        if (palavra.indexOf(letra) >= 0) {
            acertadas += letra;
        } else {
            erradas += letra;
        }
        return true;
    }

    /** A palavra com tracinho nas letras que ainda não foram descobertas. Ex.: "_ A _ A _ A". */
    public String mascara() {
        StringBuilder sb = new StringBuilder();
        for (char c : palavra.toCharArray()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (c == ' ') {
                sb.append(' ');
            } else if (acertadas.indexOf(c) >= 0) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    public boolean ganhou() {
        return !mascara().contains("_");
    }

    public boolean perdeu() {
        return erradas.length() >= MAX_ERROS;
    }

    public int erros() {
        return erradas.length();
    }

    public String erradas() {
        return erradas;
    }

    public String palavra() {
        return palavra;
    }
}
