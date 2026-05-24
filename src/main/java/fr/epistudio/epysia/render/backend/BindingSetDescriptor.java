package fr.epistudio.epysia.render.backend;

import java.util.List;

public record BindingSetDescriptor(BindingSetLayout layout, List<Binding> bindings) {

    public BindingSetDescriptor {
        bindings = List.copyOf(bindings);
    }
}
