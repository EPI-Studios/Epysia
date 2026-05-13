package fr.epistudio.epysia.components;

public class CountComponent extends Component {

    private int count;

    @Override
    public void onUpdate(float dt) {
        count+=1;
        System.out.println("Count: " + count);
    }
}
