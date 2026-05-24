package fr.epistudio.epysia.render.backend;

import java.util.List;

public record BindingSetLayout(List<BindingSlot> slots) {

    public static final BindingSetLayout EMPTY = new BindingSetLayout(List.of());

    public BindingSetLayout {
        slots = List.copyOf(slots);
    }
}
