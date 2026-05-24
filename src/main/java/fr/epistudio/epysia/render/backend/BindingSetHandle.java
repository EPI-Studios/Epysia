package fr.epistudio.epysia.render.backend;

public record BindingSetHandle(long id) {

    public static final BindingSetHandle EMPTY = new BindingSetHandle(0L);
}
