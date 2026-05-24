package fr.epistudio.epysia.components;

public final class CountComponent extends Component {

    private int count;

    public int count() {
        return count;
    }

    public void increment() {
        count++;
    }
}
