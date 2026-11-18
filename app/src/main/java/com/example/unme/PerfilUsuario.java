package com.example.unme;

import java.util.Objects;

public final class PerfilUsuario {

    private final String nome;
    private final String foto;

    public PerfilUsuario(String nome, String foto) {
        this.nome = nome != null ? nome : "";
        this.foto = foto != null ? foto : "";
    }

    public static PerfilUsuario vazio() {
        return new PerfilUsuario("", "");
    }

    public String obterNome() {
        return nome;
    }

    public String obterFoto() {
        return foto;
    }

    public boolean possuiNome() {
        return !nome.isEmpty();
    }

    public boolean possuiFoto() {
        return !foto.isEmpty();
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) return true;
        if (!(outro instanceof PerfilUsuario)) return false;
        PerfilUsuario perfil = (PerfilUsuario) outro;
        return Objects.equals(nome, perfil.nome) && Objects.equals(foto, perfil.foto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nome, foto);
    }
}
