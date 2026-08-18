package fr.epistudio.epysia.scripting.foreign;

import java.util.List;

public interface ForeignComponentType {

    String name();

    String category();

    String description();

    List<ForeignPropertyDefinition> properties();

    ForeignInstance instantiate();
}
