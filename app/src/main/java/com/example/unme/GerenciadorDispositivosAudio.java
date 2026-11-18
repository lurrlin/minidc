package com.example.unme;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class GerenciadorDispositivosAudio {

    public static final class OpcaoDispositivo {
        public final String id;
        public final String nome;
        public final int tipoAndroid;
        public final AudioDeviceInfo infoNativa;

        public OpcaoDispositivo(String id, String nome, int tipoAndroid, AudioDeviceInfo infoNativa) {
            this.id = id;
            this.nome = nome;
            this.tipoAndroid = tipoAndroid;
            this.infoNativa = infoNativa;
        }

        @Override
        public String toString() {
            return nome;
        }
    }

    public static final String SAIDA_ALTO_FALANTE = "saida_alto_falante";
    public static final String SAIDA_AURICULAR = "saida_auricular";
    public static final String SAIDA_FONE_FIO = "saida_fone_fio";
    public static final String SAIDA_BLUETOOTH = "saida_bluetooth";

    private final AudioManager gerenciadorAudio;
    private String idMicrofoneSelecionado = "";
    private String idSaidaSelecionada = SAIDA_ALTO_FALANTE;
    private int modoAudioAnterior = AudioManager.MODE_NORMAL;
    private boolean altoFalanteAnterior = false;
    private boolean sessaoComunicacaoAtiva = false;

    public GerenciadorDispositivosAudio(Context contexto) {
        this.gerenciadorAudio = (AudioManager) contexto.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    public void iniciarModoComunicacao() {
        if (gerenciadorAudio == null || sessaoComunicacaoAtiva) {
            return;
        }
        modoAudioAnterior = gerenciadorAudio.getMode();
        altoFalanteAnterior = gerenciadorAudio.isSpeakerphoneOn();
        gerenciadorAudio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        sessaoComunicacaoAtiva = true;
        aplicarSaidaAudio(idSaidaSelecionada);
    }

    public void encerrarModoComunicacao() {
        if (gerenciadorAudio == null || !sessaoComunicacaoAtiva) {
            return;
        }
        sessaoComunicacaoAtiva = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                gerenciadorAudio.clearCommunicationDevice();
            } else {
                gerenciadorAudio.stopBluetoothSco();
                gerenciadorAudio.setBluetoothScoOn(false);
                gerenciadorAudio.setSpeakerphoneOn(altoFalanteAnterior);
            }
            gerenciadorAudio.setMode(modoAudioAnterior);
        } catch (Exception ignorado) {
        }
    }

    public List<OpcaoDispositivo> listarMicrofones() {
        List<OpcaoDispositivo> resultado = new ArrayList<>();
        if (gerenciadorAudio == null) {
            resultado.add(new OpcaoDispositivo("padrao", "Microfone padrão", -1, null));
            return resultado;
        }

        AudioDeviceInfo[] entradas = gerenciadorAudio.getDevices(AudioManager.GET_DEVICES_INPUTS);
        int indiceGenerico = 1;
        for (AudioDeviceInfo dispositivo : entradas) {
            if (!dispositivo.isSource()) {
                continue;
            }
            int tipo = dispositivo.getType();
            if (!ehTipoMicrofoneSuportado(tipo)) {
                continue;
            }
            String id = String.valueOf(dispositivo.getId());
            String rotulo = descreverMicrofone(dispositivo, indiceGenerico++);
            resultado.add(new OpcaoDispositivo(id, rotulo, tipo, dispositivo));
        }

        if (resultado.isEmpty()) {
            resultado.add(new OpcaoDispositivo("padrao", "Microfone padrão", -1, null));
        }

        boolean encontrado = false;
        for (OpcaoDispositivo opcao : resultado) {
            if (opcao.id.equals(idMicrofoneSelecionado)) {
                encontrado = true;
                break;
            }
        }
        if (!encontrado && !resultado.isEmpty()) {
            idMicrofoneSelecionado = resultado.get(0).id;
        }
        return resultado;
    }

    public OpcaoDispositivo obterMicrofonePorId(String idDispositivo) {
        List<OpcaoDispositivo> microfones = listarMicrofones();
        for (OpcaoDispositivo opcao : microfones) {
            if (opcao.id.equals(idDispositivo)) {
                return opcao;
            }
        }
        return microfones.isEmpty() ? null : microfones.get(0);
    }

    public String obterIdMicrofoneSelecionado() {
        return idMicrofoneSelecionado;
    }

    public void definirIdMicrofoneSelecionado(String id) {
        this.idMicrofoneSelecionado = id != null ? id : "";
    }

    public List<OpcaoDispositivo> listarSaidasAudio() {
        List<OpcaoDispositivo> resultado = new ArrayList<>();
        if (gerenciadorAudio == null) {
            resultado.add(new OpcaoDispositivo(SAIDA_ALTO_FALANTE, "Alto-falante", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null));
            return resultado;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                List<AudioDeviceInfo> comunicacao = gerenciadorAudio.getAvailableCommunicationDevices();
                int indice = 1;
                for (AudioDeviceInfo dispositivo : comunicacao) {
                    String id = "com_" + dispositivo.getId();
                    String nome = descreverSaida(dispositivo, indice++);
                    resultado.add(new OpcaoDispositivo(id, nome, dispositivo.getType(), dispositivo));
                }
            } catch (SecurityException ignorado) {
            }
        }

        if (resultado.isEmpty()) {
            AudioDeviceInfo[] saidas = gerenciadorAudio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            boolean temAuricular = false;
            boolean temFoneFio = false;
            boolean temBluetooth = false;
            for (AudioDeviceInfo saida : saidas) {
                int tipo = saida.getType();
                if (tipo == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                    temAuricular = true;
                } else if (tipo == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || tipo == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || tipo == AudioDeviceInfo.TYPE_USB_HEADSET
                        || tipo == AudioDeviceInfo.TYPE_USB_DEVICE) {
                    temFoneFio = true;
                } else if (tipo == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || tipo == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    temBluetooth = true;
                }
            }
            resultado.add(new OpcaoDispositivo(SAIDA_ALTO_FALANTE, "Alto-falante", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null));
            if (temAuricular) {
                resultado.add(new OpcaoDispositivo(SAIDA_AURICULAR, "Fone auricular", AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, null));
            }
            if (temFoneFio) {
                resultado.add(new OpcaoDispositivo(SAIDA_FONE_FIO, "Fone com fio / USB", AudioDeviceInfo.TYPE_WIRED_HEADSET, null));
            }
            if (temBluetooth) {
                resultado.add(new OpcaoDispositivo(SAIDA_BLUETOOTH, " Áudio Bluetooth", AudioDeviceInfo.TYPE_BLUETOOTH_SCO, null));
            }
        }

        boolean encontrado = false;
        for (OpcaoDispositivo opcao : resultado) {
            if (opcao.id.equals(idSaidaSelecionada)) {
                encontrado = true;
                break;
            }
        }
        if (!encontrado && !resultado.isEmpty()) {
            for (OpcaoDispositivo opcao : resultado) {
                if (opcao.tipoAndroid == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || opcao.tipoAndroid == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || opcao.tipoAndroid == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || opcao.tipoAndroid == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    idSaidaSelecionada = opcao.id;
                    encontrado = true;
                    break;
                }
            }
            if (!encontrado) {
                idSaidaSelecionada = resultado.get(0).id;
            }
        }
        return resultado;
    }

    public String obterIdSaidaSelecionada() {
        return idSaidaSelecionada;
    }

    public boolean aplicarSaidaAudio(String idSaida) {
        if (gerenciadorAudio == null || idSaida == null || idSaida.isEmpty()) {
            return false;
        }
        List<OpcaoDispositivo> saidas = listarSaidasAudio();
        OpcaoDispositivo escolhida = null;
        for (OpcaoDispositivo opcao : saidas) {
            if (opcao.id.equals(idSaida)) {
                escolhida = opcao;
                break;
            }
        }
        if (escolhida == null && !saidas.isEmpty()) {
            escolhida = saidas.get(0);
        }
        if (escolhida == null) {
            return false;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && escolhida.infoNativa != null) {
                boolean sucesso = gerenciadorAudio.setCommunicationDevice(escolhida.infoNativa);
                if (sucesso) {
                    idSaidaSelecionada = escolhida.id;
                }
                return sucesso;
            } else {
                if (SAIDA_BLUETOOTH.equals(escolhida.id)) {
                    gerenciadorAudio.setSpeakerphoneOn(false);
                    gerenciadorAudio.startBluetoothSco();
                    gerenciadorAudio.setBluetoothScoOn(true);
                } else if (SAIDA_ALTO_FALANTE.equals(escolhida.id)) {
                    gerenciadorAudio.stopBluetoothSco();
                    gerenciadorAudio.setBluetoothScoOn(false);
                    gerenciadorAudio.setSpeakerphoneOn(true);
                } else {
                    gerenciadorAudio.stopBluetoothSco();
                    gerenciadorAudio.setBluetoothScoOn(false);
                    gerenciadorAudio.setSpeakerphoneOn(false);
                }
                idSaidaSelecionada = escolhida.id;
                return true;
            }
        } catch (Exception erro) {
            return false;
        }
    }

    private static boolean ehTipoMicrofoneSuportado(int tipo) {
        if (tipo == AudioDeviceInfo.TYPE_BUILTIN_MIC
                || tipo == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || tipo == AudioDeviceInfo.TYPE_USB_DEVICE
                || tipo == AudioDeviceInfo.TYPE_USB_HEADSET
                || tipo == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && tipo == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            return true;
        }
        return false;
    }

    private static String descreverMicrofone(AudioDeviceInfo dispositivo, int indice) {
        CharSequence produto = dispositivo.getProductName();
        String sufixoProduto = (produto != null && produto.length() > 0) ? " (" + produto + ")" : "";
        int tipo = dispositivo.getType();
        if (tipo == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                String endereco = dispositivo.getAddress();
                if ("bottom".equalsIgnoreCase(endereco)) {
                    return "Microfone principal (inferior)";
                } else if ("back".equalsIgnoreCase(endereco)) {
                    return "Microfone traseiro (ambiente)";
                } else if ("top".equalsIgnoreCase(endereco)) {
                    return "Microfone superior";
                }
            }
            return "Microfone interno " + indice;
        }
        if (tipo == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
            return "Microfone do fone com fio";
        }
        if (tipo == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && tipo == AudioDeviceInfo.TYPE_BLE_HEADSET)) {
            return "Microfone Bluetooth" + sufixoProduto;
        }
        if (tipo == AudioDeviceInfo.TYPE_USB_HEADSET || tipo == AudioDeviceInfo.TYPE_USB_DEVICE) {
            return "Microfone USB" + sufixoProduto;
        }
        return "Microfone " + indice;
    }

    private static String descreverSaida(AudioDeviceInfo dispositivo, int indice) {
        CharSequence produto = dispositivo.getProductName();
        String sufixoProduto = (produto != null && produto.length() > 0) ? " (" + produto + ")" : "";
        int tipo = dispositivo.getType();
        if (tipo == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            return "Alto-falante";
        }
        if (tipo == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
            return "Fone auricular";
        }
        if (tipo == AudioDeviceInfo.TYPE_WIRED_HEADSET || tipo == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
            return "Fone com fio";
        }
        if (tipo == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || tipo == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && (tipo == AudioDeviceInfo.TYPE_BLE_HEADSET || tipo == AudioDeviceInfo.TYPE_BLE_SPEAKER))) {
            return "Bluetooth" + sufixoProduto;
        }
        if (tipo == AudioDeviceInfo.TYPE_USB_HEADSET || tipo == AudioDeviceInfo.TYPE_USB_DEVICE) {
            return "Áudio USB" + sufixoProduto;
        }
        return "Saída de áudio " + indice;
    }
}
