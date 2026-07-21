package fr.epistudio.epysia.editor.assets;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AssetQuery {

    public enum SortField {
        NAME("Name"), TYPE("Type"), SIZE("Size"), MODIFIED("Modified");

        private final String label;

        SortField(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private String searchText = "";
    private SortField sortField = SortField.NAME;
    private boolean ascending = true;
    private AssetType typeFilter;

    public String searchText() {
        return searchText;
    }

    public void setSearchText(String value) {
        this.searchText = value.replace("\0", "").strip();
    }

    public boolean isSearching() {
        return !searchText.isEmpty();
    }

    public SortField sortField() {
        return sortField;
    }

    public void setSortField(SortField field) {
        this.sortField = field;
    }

    public boolean ascending() {
        return ascending;
    }

    public void toggleDirection() {
        ascending = !ascending;
    }

    public Optional<AssetType> typeFilter() {
        return Optional.ofNullable(typeFilter);
    }

    public void setTypeFilter(AssetType type) {
        this.typeFilter = type;
    }

    public List<AssetEntry> apply(List<AssetEntry> entries) {
        List<AssetEntry> result = entries.stream()
                .filter(this::matchesSearch)
                .filter(this::matchesType)
                .sorted(comparator())
                .toList();
        return result;
    }

    private boolean matchesSearch(AssetEntry entry) {
        return !isSearching()
                || entry.displayName().toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT));
    }

    private boolean matchesType(AssetEntry entry) {
        return typeFilter == null || entry.type() == typeFilter;
    }

    private Comparator<AssetEntry> comparator() {
        Comparator<AssetEntry> byField = switch (sortField) {
            case NAME -> Comparator.comparing(entry -> entry.displayName().toLowerCase(Locale.ROOT));
            case TYPE -> Comparator.comparing(AssetEntry::type).thenComparing(AssetEntry::displayName);
            case SIZE -> Comparator.comparingLong(AssetEntry::byteSize);
            case MODIFIED -> Comparator.comparingLong(AssetEntry::modifiedMillis);
        };
        return ascending ? byField : byField.reversed();
    }
}
